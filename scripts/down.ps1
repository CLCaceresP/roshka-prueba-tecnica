# Baja el stack y borra sus volúmenes.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
docker compose --profile full down -v --remove-orphans
exit $LASTEXITCODE
