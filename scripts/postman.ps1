# Ejecuta la colección de Postman con Newman contra el stack levantado (primero scripts\up.ps1).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
  Write-Error 'npx no encontrado. Instala Node.js 22 o superior desde https://nodejs.org'
}
npx --yes newman run postman/pedidos-inventario.postman_collection.json `
  -e postman/local.postman_environment.json @args
exit $LASTEXITCODE
