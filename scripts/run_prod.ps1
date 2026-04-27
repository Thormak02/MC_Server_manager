[CmdletBinding()]
param(
    [string]$Host = "0.0.0.0",
    [int]$Port = 8000
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"

if (-not (Test-Path -LiteralPath $venvPython)) {
    throw "Python virtual environment not found at '$venvPython'. Run setup first."
}

Set-Location -LiteralPath $repoRoot
$env:PYTHONUNBUFFERED = "1"

& $venvPython -m uvicorn app.main:app --host $Host --port $Port
if ($LASTEXITCODE -ne 0) {
    throw "uvicorn exited with code $LASTEXITCODE."
}
