# Ejecuta los tests unitarios, de arquitectura, de integración y de concurrencia (requiere Docker para Testcontainers).
# Los argumentos extra van a Maven, p. ej. scripts\test.ps1 -Dgroups=concurrency
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& .\mvnw.cmd -B -ntp verify @args
exit $LASTEXITCODE
