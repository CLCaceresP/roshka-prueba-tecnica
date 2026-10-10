# Ejecuta la prueba de carga de sobreventa de k6 contra el stack levantado (primero scripts\up.ps1).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
  Write-Error 'k6 no encontrado. Instálalo con: winget install k6 --source winget'
}
k6 run load/oversell.js @args
exit $LASTEXITCODE
