# Levanta solamente el front local; Ctrl+C lo cierra sin bajar Docker.
param([int]$Puerto = 5173, [string]$Gateway = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$servidor = Join-Path $PSScriptRoot '..\front\servidor.mjs'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Se necesita Node.js 22 o superior para ejecutar el front.'
}
& node $servidor --puerto $Puerto --gateway $Gateway
exit $LASTEXITCODE
