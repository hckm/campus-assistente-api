param(
    [string]$HostName = "localhost",
    [int]$Port = 8081,
    [string]$StorePassword = "changeit"
)

$ErrorActionPreference = "Stop"
$allowedHosts = @("localhost", "127.0.0.1", "[::1]")
if ($allowedHosts -notcontains $HostName -or $Port -lt 1 -or $Port -gt 65535) {
    throw "Exportacao do certificado exige host de loopback e porta valida"
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$outputDirectory = Join-Path $projectRoot ".local/cosmos"
$certificatePath = Join-Path $outputDirectory "cosmos-emulator.cer"
$trustStorePath = Join-Path $outputDirectory "cosmos-emulator-truststore.p12"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

& keytool -printcert -sslserver "${HostName}:${Port}" -rfc |
    Set-Content -LiteralPath $certificatePath -Encoding ascii
if ($LASTEXITCODE -ne 0) {
    throw "Falha ao exportar certificado do emulador Cosmos"
}

if (Test-Path $trustStorePath) {
    Remove-Item -LiteralPath $trustStorePath -Force
}

& keytool -importcert -alias cosmos-emulator -file $certificatePath -keystore $trustStorePath `
    -storetype PKCS12 -storepass $StorePassword -noprompt
if ($LASTEXITCODE -ne 0) {
    throw "Falha ao criar truststore local do emulador Cosmos"
}

Write-Output $trustStorePath
