# MariaDB fuer Freimarken einrichten.
#
# Gedacht fuer den Rechner, auf dem der Spielserver laeuft. Entwicklungsrechner im selben
# Netz greifen darauf zu - deshalb legt das Skript den Datenbankbenutzer nicht nur fuer
# localhost an, sondern auch fuer das lokale Netz, und oeffnet den Port in der Firewall.
#
# Was es tut:
#   1. MariaDB ueber winget als Windows-Dienst installieren (Port 3306, Autostart)
#   2. Schema "freimarken" anlegen
#   3. Benutzer "freimarken" anlegen - NICHT root, nur Rechte auf dieses eine Schema
#   4. Firewall fuer Port 3306 oeffnen, begrenzt auf das lokale Netz
#   5. Beide Passwoerter zufaellig erzeugen und ablegen
#
# Warum die Passwoerter nicht hier stehen: Dieses Skript liegt im oeffentlichen
# Repository. Die Zugangsdaten landen unter data/, und data/ ist in .gitignore.
#
# Ausfuehren: mariadb_einrichten.bat doppelklicken (hebt sich selbst auf Adminrechte).

param(
    # Aus welchem Netz der Datenbankbenutzer kommen darf, z.B. "192.168.178.%".
    # Leer = wird aus der eigenen Adresse abgeleitet.
    [string]$LanMuster = "",
    # Zusaetzlicher Ablageort fuer die Zugangsdaten, damit ein zweiter Rechner sie lesen
    # kann, ohne dass jemand ein Passwort durch die Gegend schickt.
    [string]$ZweitAblage = "\\FriedrichNAS\FriedrichNAS\mc_server_manager\mc_server_manager\data\freimarken"
)

$ErrorActionPreference = "Stop"

function Schritt($text)  { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Gut($text)      { Write-Host "    $text" -ForegroundColor Green }
function Hinweis($text)  { Write-Host "    $text" -ForegroundColor Yellow }
function Schlecht($text) { Write-Host "    $text" -ForegroundColor Red }

$skriptOrdner  = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoWurzel    = Resolve-Path (Join-Path $skriptOrdner "..\..")
$zugangsOrdner = Join-Path $repoWurzel "data\freimarken"
$zugangsDatei  = Join-Path $zugangsOrdner "datenbank.properties"

Write-Host "Freimarken - MariaDB einrichten" -ForegroundColor White
Write-Host "Repository: $repoWurzel"

$istAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Schlecht "Dieses Skript braucht Adminrechte. Bitte mariadb_einrichten.bat benutzen."
    exit 1
}

# --- Netzmuster bestimmen -------------------------------------------------
if ([string]::IsNullOrWhiteSpace($LanMuster)) {
    $eigene = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" } |
        Sort-Object -Property InterfaceMetric |
        Select-Object -First 1).IPAddress
    if ($eigene) {
        $teile = $eigene.Split(".")
        $LanMuster = "$($teile[0]).$($teile[1]).$($teile[2]).%"
    } else {
        $LanMuster = "localhost"
    }
}
Gut "Zugriff erlaubt aus: $LanMuster"

# --- Passwoerter ----------------------------------------------------------
function NeuesPasswort([int]$laenge = 28) {
    # Ohne Zeichen, die in Konsole, YAML oder JDBC-URL Aerger machen.
    $zeichen = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789-_".ToCharArray()
    $rohdaten = New-Object byte[] $laenge
    # Create().GetBytes statt ::Fill - letzteres gibt es erst ab .NET 6, und Windows
    # PowerShell 5.1 laeuft auf dem .NET Framework.
    $zufall = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $zufall.GetBytes($rohdaten) } finally { $zufall.Dispose() }
    -join ($rohdaten | ForEach-Object { $zeichen[$_ % $zeichen.Length] })
}

$rootPasswort = $null
$appPasswort  = $null
if (Test-Path $zugangsDatei) {
    Schritt "Vorhandene Zugangsdaten werden weiterbenutzt"
    $vorhanden = @{}
    Get-Content $zugangsDatei | Where-Object { $_ -match "^\s*[^#].*=" } | ForEach-Object {
        $teile = $_.Split("=", 2); $vorhanden[$teile[0].Trim()] = $teile[1].Trim()
    }
    $rootPasswort = $vorhanden["root.passwort"]
    $appPasswort  = $vorhanden["passwort"]
}
if (-not $rootPasswort) { $rootPasswort = NeuesPasswort }
if (-not $appPasswort)  { $appPasswort  = NeuesPasswort }

# --- 1. Installieren ------------------------------------------------------
Schritt "MariaDB installieren"
if (Get-Service -Name "MariaDB" -ErrorAction SilentlyContinue) {
    Gut "Dienst 'MariaDB' ist bereits vorhanden - Installation wird uebersprungen."
} else {
    $argumente = "/quiet SERVICENAME=MariaDB PORT=3306 UTF8=1 ALLOWREMOTEROOTACCESS=0 PASSWORD=$rootPasswort"
    Write-Host "    winget install MariaDB.Server ..."
    winget install --id MariaDB.Server --exact --silent `
        --accept-package-agreements --accept-source-agreements `
        --override $argumente
    if ($LASTEXITCODE -ne 0) {
        Schlecht "winget meldet Fehlercode $LASTEXITCODE."
        Schlecht "Falls die unbeaufsichtigte Installation nicht greift: MariaDB von"
        Schlecht "mariadb.org/download holen und den Assistenten benutzen - Dienstname"
        Schlecht "MariaDB, Port 3306. Danach dieses Skript erneut starten; es legt dann"
        Schlecht "nur noch Schema, Benutzer und Firewallregel an."
        exit 1
    }
    Gut "installiert"
}

# --- 2. Auf den Dienst warten --------------------------------------------
Schritt "Auf den Dienst warten"
$mysql = $null
foreach ($muster in @("C:\Program Files\MariaDB*\bin\mariadb.exe",
                      "C:\Program Files\MariaDB*\bin\mysql.exe")) {
    $treffer = Get-ChildItem $muster -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($treffer) { $mysql = $treffer.FullName; break }
}
if (-not $mysql) { Schlecht "mariadb.exe nicht gefunden."; exit 1 }
Gut "Werkzeug: $mysql"

Start-Service -Name "MariaDB" -ErrorAction SilentlyContinue
$bereit = $false
foreach ($versuch in 1..30) {
    if ((Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 `
            -WarningAction SilentlyContinue).TcpTestSucceeded) { $bereit = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $bereit) { Schlecht "Port 3306 antwortet nicht."; exit 1 }
Gut "Port 3306 antwortet"

# --- 3. Schema und Benutzer ----------------------------------------------
Schritt "Schema und Benutzer anlegen"
$sql = @"
CREATE DATABASE IF NOT EXISTS freimarken
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'freimarken'@'localhost' IDENTIFIED BY '$appPasswort';
ALTER USER 'freimarken'@'localhost' IDENTIFIED BY '$appPasswort';
GRANT ALL PRIVILEGES ON freimarken.* TO 'freimarken'@'localhost';

CREATE USER IF NOT EXISTS 'freimarken'@'$LanMuster' IDENTIFIED BY '$appPasswort';
ALTER USER 'freimarken'@'$LanMuster' IDENTIFIED BY '$appPasswort';
GRANT ALL PRIVILEGES ON freimarken.* TO 'freimarken'@'$LanMuster';

FLUSH PRIVILEGES;
SELECT CONCAT('Schema bereit: ', SCHEMA_NAME) AS stand
  FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'freimarken';
SELECT CONCAT('Benutzer: ', user, '@', host) AS konten
  FROM mysql.user WHERE user = 'freimarken';
"@
$sql | & $mysql --user=root "--password=$rootPasswort" --host=127.0.0.1 --port=3306
if ($LASTEXITCODE -ne 0) {
    Schlecht "Das Anlegen hat nicht geklappt (Code $LASTEXITCODE)."
    Schlecht "Hat root ein anderes Passwort, die Zeile 'root.passwort' in"
    Schlecht "$zugangsDatei anpassen und erneut starten."
    exit 1
}
Gut "Schema und Benutzer bereit"

# --- 4. Firewall und Erreichbarkeit --------------------------------------
Schritt "Firewall und Erreichbarkeit"
$regel = "Freimarken MariaDB 3306"
if (Get-NetFirewallRule -DisplayName $regel -ErrorAction SilentlyContinue) {
    Gut "Firewallregel besteht bereits"
} elseif ($LanMuster -eq "localhost") {
    Hinweis "Kein lokales Netz erkannt - keine Firewallregel angelegt."
} else {
    $bereich = $LanMuster.Replace("%", "0/24")
    New-NetFirewallRule -DisplayName $regel -Direction Inbound -Action Allow `
        -Protocol TCP -LocalPort 3306 -RemoteAddress $bereich -Profile Any | Out-Null
    Gut "Firewall geoeffnet fuer $bereich"
}

# bind-address pruefen statt heimlich aendern: Wer die Konfiguration selbst angepasst hat,
# soll das nicht von einem Skript ueberschrieben bekommen.
$iniDatei = Get-ChildItem "C:\Program Files\MariaDB*\data\my.ini" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($iniDatei) {
    $bind = Select-String -Path $iniDatei.FullName -Pattern "^\s*bind-address" -ErrorAction SilentlyContinue
    if ($bind -and $bind.Line -match "127\.0\.0\.1") {
        Hinweis "In $($iniDatei.FullName) steht bind-address=127.0.0.1 -"
        Hinweis "dann sind nur lokale Verbindungen moeglich. Zeile auskommentieren oder"
        Hinweis "auf 0.0.0.0 setzen und den Dienst neu starten."
    } else {
        Gut "bind-address erlaubt Netzverbindungen"
    }
}

# --- 5. Zugangsdaten ablegen ---------------------------------------------
Schritt "Zugangsdaten ablegen"
$rechner = $env:COMPUTERNAME
$inhalt = @"
# Zugangsdaten der Freimarken-Datenbank.
# Erzeugt von freimarken/skripte/mariadb_einrichten.ps1 auf $rechner
#
# ACHTUNG: Dieser Ordner liegt unter data/ und ist in .gitignore - er gehoert NICHT
# ins Repository. Wer diese Datei hat, hat die Datenbank.
url=jdbc:mariadb://127.0.0.1:3306/freimarken
url.netz=jdbc:mariadb://$rechner:3306/freimarken
benutzer=freimarken
passwort=$appPasswort

# Nur fuer Wartung. Das Plugin benutzt root nie.
root.passwort=$rootPasswort
"@

$ohneBom = New-Object System.Text.UTF8Encoding($false)
foreach ($ordner in @($zugangsOrdner, $ZweitAblage)) {
    if ([string]::IsNullOrWhiteSpace($ordner)) { continue }
    try {
        New-Item -ItemType Directory -Force -Path $ordner | Out-Null
        $ziel = Join-Path $ordner "datenbank.properties"
        [System.IO.File]::WriteAllText($ziel, $inhalt, $ohneBom)
        Gut "geschrieben: $ziel"
    } catch {
        Hinweis "Konnte nicht nach $ordner schreiben: $($_.Exception.Message)"
    }
}

Write-Host "`nFertig." -ForegroundColor Green
Write-Host "Verbindung aus dem Netz: jdbc:mariadb://$rechner:3306/freimarken"
Write-Host "Die Zugangsdaten sind ein Geheimnis - nicht ins Repository, nicht in eine"
Write-Host "Sicherung, die jemand anders lesen kann."
