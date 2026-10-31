# MariaDB fuer Freimarken einrichten.
#
# Was dieses Skript tut:
#   1. MariaDB ueber winget als Windows-Dienst installieren (Port 3306, Autostart)
#   2. Schema "freimarken" anlegen
#   3. Einen eigenen Datenbankbenutzer "freimarken" anlegen - NICHT root, und nur mit
#      Rechten auf dieses eine Schema
#   4. Beide Passwoerter zufaellig erzeugen und nach data/freimarken/datenbank.properties
#      schreiben
#
# Warum die Passwoerter nicht hier stehen: Dieses Skript liegt im oeffentlichen
# Repository. Die Zugangsdaten landen unter data/, und data/ ist in .gitignore.
#
# Ausfuehren: mariadb_einrichten.bat doppelklicken (hebt sich selbst auf Adminrechte).

$ErrorActionPreference = "Stop"

function Schritt($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Gut($text)     { Write-Host "    $text" -ForegroundColor Green }
function Schlecht($text){ Write-Host "    $text" -ForegroundColor Red }

# --- Lage bestimmen -------------------------------------------------------
$skriptOrdner = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoWurzel   = Resolve-Path (Join-Path $skriptOrdner "..\..")
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

# --- Passwoerter erzeugen -------------------------------------------------
function NeuesPasswort([int]$laenge = 28) {
    # Ohne Zeichen, die in Konsole, YAML oder JDBC-URL Aerger machen.
    $zeichen = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789-_".ToCharArray()
    $rohdaten = New-Object byte[] $laenge
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($rohdaten)
    -join ($rohdaten | ForEach-Object { $zeichen[$_ % $zeichen.Length] })
}

if (Test-Path $zugangsDatei) {
    Schritt "Zugangsdaten sind schon da - bestehende werden weiterbenutzt"
    $vorhanden = @{}
    Get-Content $zugangsDatei | Where-Object { $_ -match "^\s*[^#].*=" } | ForEach-Object {
        $teile = $_.Split("=", 2); $vorhanden[$teile[0].Trim()] = $teile[1].Trim()
    }
    $rootPasswort = $vorhanden["root.passwort"]
    $appPasswort  = $vorhanden["passwort"]
} else {
    $rootPasswort = NeuesPasswort
    $appPasswort  = NeuesPasswort
}

# --- 1. Installieren ------------------------------------------------------
Schritt "MariaDB installieren"
$dienst = Get-Service -Name "MariaDB" -ErrorAction SilentlyContinue
if ($dienst) {
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
        Schlecht "mariadb.org/download herunterladen und den Assistenten benutzen -"
        Schlecht "Dienstname MariaDB, Port 3306. Danach dieses Skript erneut starten."
        exit 1
    }
    Gut "installiert"
}

# --- 2. Auf den Dienst warten --------------------------------------------
Schritt "Auf den Dienst warten"
$mysql = $null
foreach ($pfad in @("C:\Program Files\MariaDB*\bin\mariadb.exe",
                    "C:\Program Files\MariaDB*\bin\mysql.exe")) {
    $treffer = Get-ChildItem $pfad -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($treffer) { $mysql = $treffer.FullName; break }
}
if (-not $mysql) { Schlecht "mariadb.exe nicht gefunden."; exit 1 }
Gut "Werkzeug: $mysql"

Start-Service -Name "MariaDB" -ErrorAction SilentlyContinue
$bereit = $false
foreach ($versuch in 1..30) {
    $pruefung = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue
    if ($pruefung.TcpTestSucceeded) { $bereit = $true; break }
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
FLUSH PRIVILEGES;
SELECT CONCAT('Schema bereit: ', SCHEMA_NAME) AS stand
  FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'freimarken';
"@
$sql | & $mysql --user=root "--password=$rootPasswort" --host=127.0.0.1 --port=3306
if ($LASTEXITCODE -ne 0) {
    Schlecht "Das Anlegen hat nicht geklappt (Code $LASTEXITCODE)."
    Schlecht "Wenn root ein anderes Passwort hat als erwartet, die Zeile 'root.passwort'"
    Schlecht "in $zugangsDatei anpassen und erneut starten."
    exit 1
}
Gut "Schema 'freimarken' und Benutzer 'freimarken'@'localhost' bereit"

# --- 4. Zugangsdaten ablegen ---------------------------------------------
Schritt "Zugangsdaten ablegen"
New-Item -ItemType Directory -Force -Path $zugangsOrdner | Out-Null
@"
# Zugangsdaten der Freimarken-Datenbank.
# Erzeugt von freimarken/skripte/mariadb_einrichten.ps1
#
# ACHTUNG: Dieser Ordner liegt unter data/ und ist in .gitignore - er gehoert
# NICHT ins Repository. Wer diese Datei hat, hat die Datenbank.
url=jdbc:mariadb://127.0.0.1:3306/freimarken
benutzer=freimarken
passwort=$appPasswort

# Nur fuer Wartung. Das Plugin benutzt root nie.
root.passwort=$rootPasswort
"@ | Set-Content -Path $zugangsDatei -Encoding UTF8
Gut "geschrieben: $zugangsDatei"

Write-Host "`nFertig." -ForegroundColor Green
Write-Host "Die Zugangsdaten stehen in data\freimarken\datenbank.properties."
Write-Host "Diese Datei ist ein Geheimnis - sie gehoert nicht ins Repository und"
Write-Host "nicht in eine Sicherung, die jemand anders lesen kann."
