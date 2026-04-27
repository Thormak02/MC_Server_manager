[CmdletBinding()]
param(
    [string]$ServiceName = "mc-server-manager",
    [string]$DisplayName = "MC Server Manager",
    [string]$Description = "Minecraft Server Manager (FastAPI/uvicorn)",
    [string]$ListenHost = "0.0.0.0",
    [int]$Port = 8000,
    [switch]$Reinstall
)

$ErrorActionPreference = "Stop"

function Test-IsAdministrator {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-IsAdministrator)) {
    throw "Run this script in an elevated PowerShell session (Administrator)."
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$serviceScript = Join-Path $repoRoot "scripts\windows_service.py"
$dataDir = Join-Path $repoRoot "data"
$runtimeConfigPath = Join-Path $dataDir "service_config.json"
$serviceMetaPath = Join-Path $dataDir "service_meta.json"
$venvRoot = Join-Path $repoRoot ".venv"

if (-not (Test-Path -LiteralPath $venvPython)) {
    throw "Virtualenv python not found: '$venvPython'. Run setup first."
}

if (-not (Test-Path -LiteralPath $serviceScript)) {
    throw "Service script not found: '$serviceScript'."
}

Set-Location -LiteralPath $repoRoot
& $venvPython -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install Python requirements."
}

$pywin32PostInstall = Join-Path $venvRoot "Lib\site-packages\pywin32_postinstall.py"
if (Test-Path -LiteralPath $pywin32PostInstall) {
    & $venvPython $pywin32PostInstall -install
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to run pywin32 post-install."
    }
}

$pywin32System32Dir = Join-Path $venvRoot "Lib\site-packages\pywin32_system32"
$pywinDlls = @("pywintypes*.dll", "pythoncom*.dll")
foreach ($pattern in $pywinDlls) {
    foreach ($source in (Get-ChildItem -LiteralPath $pywin32System32Dir -Filter $pattern -File -ErrorAction SilentlyContinue)) {
        Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $venvRoot $source.Name) -Force
    }
}

# Ensure CPython runtime DLLs are available for LocalSystem service startup.
$pyRuntimeInfoRaw = & $venvPython -c "import json, sys, pathlib; print(json.dumps({'base_prefix': str(pathlib.Path(sys.base_prefix)), 'exe_dir': str(pathlib.Path(sys.executable).resolve().parent), 'major': sys.version_info.major, 'minor': sys.version_info.minor}))"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($pyRuntimeInfoRaw)) {
    throw "Failed to read Python runtime info."
}
$pyRuntimeInfo = $pyRuntimeInfoRaw | ConvertFrom-Json
$pythonDllName = "python$($pyRuntimeInfo.major)$($pyRuntimeInfo.minor).dll"
$runtimeCandidates = @(
    (Join-Path $pyRuntimeInfo.base_prefix $pythonDllName),
    (Join-Path $pyRuntimeInfo.base_prefix "python3.dll"),
    (Join-Path $pyRuntimeInfo.base_prefix "vcruntime140.dll"),
    (Join-Path $pyRuntimeInfo.base_prefix "vcruntime140_1.dll"),
    (Join-Path $pyRuntimeInfo.exe_dir $pythonDllName),
    (Join-Path $pyRuntimeInfo.exe_dir "python3.dll"),
    (Join-Path $pyRuntimeInfo.exe_dir "vcruntime140.dll"),
    (Join-Path $pyRuntimeInfo.exe_dir "vcruntime140_1.dll")
) | Select-Object -Unique
foreach ($candidate in $runtimeCandidates) {
    if (Test-Path -LiteralPath $candidate) {
        $destination = Join-Path $venvRoot ([System.IO.Path]::GetFileName($candidate))
        Copy-Item -LiteralPath $candidate -Destination $destination -Force
    }
}

if (-not (Test-Path -LiteralPath $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir | Out-Null
}

$runtimeConfig = [ordered]@{
    listen_host = $ListenHost
    port = $Port
}
$runtimeConfig | ConvertTo-Json | Set-Content -LiteralPath $runtimeConfigPath -Encoding UTF8

$serviceMeta = [ordered]@{
    service_name = $ServiceName
    display_name = $DisplayName
    description = $Description
}
$serviceMeta | ConvertTo-Json | Set-Content -LiteralPath $serviceMetaPath -Encoding UTF8

$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    if (-not $Reinstall) {
        throw "Service '$ServiceName' already exists. Run again with -Reinstall to replace it."
    }

    if ($existing.Status -ne "Stopped") {
        Stop-Service -Name $ServiceName -Force
        (Get-Service -Name $ServiceName).WaitForStatus("Stopped", [TimeSpan]::FromSeconds(90))
    }

    & sc.exe delete $ServiceName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to delete existing service '$ServiceName'."
    }

    $deleted = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        $stillExisting = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
        if ($null -eq $stillExisting) {
            $deleted = $true
            break
        }
    }
    if (-not $deleted) {
        throw "Service '$ServiceName' could not be fully removed. Close Services.msc/Event Viewer handles and try again."
    }
}

& $venvPython $serviceScript --startup auto install
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install Windows service via pywin32."
}

$serviceConfig = & sc.exe qc $ServiceName
if ($LASTEXITCODE -ne 0) {
    throw "Failed to query installed service '$ServiceName'."
}

$expectedBinaryPath = ('"' + (Join-Path $venvRoot "pythonservice.exe") + '"')
if (($serviceConfig -join "`n") -notmatch [Regex]::Escape($expectedBinaryPath)) {
    throw "Installed service binary path is not pywin32 pythonservice.exe. Another wrapper still owns '$ServiceName'."
}

$serviceRegRoot = "HKLM:\SYSTEM\CurrentControlSet\Services\$ServiceName"
if (-not (Test-Path -LiteralPath $serviceRegRoot)) {
    throw "Service registry root not found: '$serviceRegRoot'."
}
$serviceParamsRegPath = Join-Path $serviceRegRoot "Parameters"
New-Item -Path $serviceParamsRegPath -Force | Out-Null

$venvSysPathRaw = & $venvPython -c "import json, sys; print(json.dumps([p for p in sys.path if p]))"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($venvSysPathRaw)) {
    throw "Failed to read virtualenv sys.path."
}

$venvSysPath = $venvSysPathRaw | ConvertFrom-Json
$pythonPathParts = @($repoRoot, (Join-Path $repoRoot "scripts"))
foreach ($entry in $venvSysPath) {
    if (-not [string]::IsNullOrWhiteSpace($entry)) {
        $pythonPathParts += [string]$entry
    }
}
$pythonPathValue = ($pythonPathParts | Select-Object -Unique) -join ";"
$pythonClassValue = "scripts.windows_service.McServerManagerService"

New-ItemProperty -Path $serviceParamsRegPath -Name "PythonPath" -Value $pythonPathValue -PropertyType String -Force | Out-Null
New-ItemProperty -Path $serviceParamsRegPath -Name "PythonClass" -Value $pythonClassValue -PropertyType String -Force | Out-Null

# Fallback for hosts that expect these values directly on the service root.
New-ItemProperty -Path $serviceRegRoot -Name "PythonPath" -Value $pythonPathValue -PropertyType String -Force | Out-Null
New-ItemProperty -Path $serviceRegRoot -Name "PythonClass" -Value $pythonClassValue -PropertyType String -Force | Out-Null

Start-Service -Name $ServiceName
Write-Host "Service '$ServiceName' installed and started."
