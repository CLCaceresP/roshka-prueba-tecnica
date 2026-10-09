# Compila los jars y levanta el stack. Uso: scripts\up.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& .\mvnw.cmd -B -q -ntp -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose --profile full up -d --build --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker compose --profile full ps
