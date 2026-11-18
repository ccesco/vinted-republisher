# Démarre PostgreSQL, le back (API) et le front (Angular), chacun dans sa fenêtre.
# Usage : .\scripts\demarrer.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Set-Location $root
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose a échoué (Docker est-il lancé ?)' }

# Régénère le client TypeScript depuis le contrat OpenAPI
mvn -q generate-sources
if ($LASTEXITCODE -ne 0) { throw 'mvn generate-sources a échoué' }

Start-Process powershell -WorkingDirectory $root -ArgumentList '-NoExit', '-Command', 'mvn spring-boot:run'

$frontend = Join-Path $root 'frontend'
$frontCommand = 'npm start'
if (-not (Test-Path (Join-Path $frontend 'node_modules'))) { $frontCommand = 'npm install; if ($?) { npm start }' }
Start-Process powershell -WorkingDirectory $frontend -ArgumentList '-NoExit', '-Command', $frontCommand

Write-Host 'Back : http://localhost:8080/api — Front : http://localhost:4200'
