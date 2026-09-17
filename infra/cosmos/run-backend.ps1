param(
    [string]$EnvironmentFile = ".local/cosmos/emulator-env.ps1"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$environmentPath = Join-Path $projectRoot $EnvironmentFile
$trustStoreFile = Join-Path $projectRoot ".local/cosmos/cosmos-emulator-truststore.p12"
$trustStorePath = ".local/cosmos/cosmos-emulator-truststore.p12"
if (-not (Test-Path $environmentPath)) {
    throw "Arquivo local do ambiente Cosmos nao encontrado"
}
if (-not (Test-Path $trustStoreFile)) {
    throw "Truststore local do emulador Cosmos nao encontrado"
}

$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$previousCepLookupEnabled = $env:CEP_LOOKUP_ENABLED
$previousProvisioningEnabled = $env:AZURE_COSMOS_PROVISIONING_ENABLED
$previousSeedEnabled = $env:AZURE_COSMOS_SEED_ENABLED
try {
    . $environmentPath
    $endpointUri = [Uri]$env:COSMOS_EMULATOR_ENDPOINT
    $allowedHosts = @("localhost", "127.0.0.1", "[::1]")
    if ($endpointUri.Scheme -ne "https" -or $allowedHosts -notcontains $endpointUri.Host `
            -or $endpointUri.Port -lt 1 -or $endpointUri.Port -gt 65535 `
            -or $endpointUri.UserInfo -or $endpointUri.Query -or $endpointUri.Fragment `
            -or ($endpointUri.AbsolutePath -ne "/" -and $endpointUri.AbsolutePath -ne "") `
            -or [string]::IsNullOrWhiteSpace($env:COSMOS_EMULATOR_KEY)) {
        throw "Emulador Cosmos exige endpoint HTTPS de loopback e chave local"
    }
    Push-Location $projectRoot
    try {
        $env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStore=$trustStorePath -Djavax.net.ssl.trustStorePassword=changeit"
        $env:CEP_LOOKUP_ENABLED = "false"
        $env:AZURE_COSMOS_PROVISIONING_ENABLED = "true"
        $env:AZURE_COSMOS_SEED_ENABLED = "true"
        & mvn -B spring-boot:run "-Dspring-boot.run.profiles=local,cosmos"
        if ($LASTEXITCODE -ne 0) {
            throw "Execucao local com Cosmos falhou"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    if ($null -eq $previousCepLookupEnabled) {
        Remove-Item Env:CEP_LOOKUP_ENABLED -ErrorAction SilentlyContinue
    } else {
        $env:CEP_LOOKUP_ENABLED = $previousCepLookupEnabled
    }
    if ($null -eq $previousProvisioningEnabled) {
        Remove-Item Env:AZURE_COSMOS_PROVISIONING_ENABLED -ErrorAction SilentlyContinue
    } else {
        $env:AZURE_COSMOS_PROVISIONING_ENABLED = $previousProvisioningEnabled
    }
    if ($null -eq $previousSeedEnabled) {
        Remove-Item Env:AZURE_COSMOS_SEED_ENABLED -ErrorAction SilentlyContinue
    } else {
        $env:AZURE_COSMOS_SEED_ENABLED = $previousSeedEnabled
    }
    Remove-Item Env:COSMOS_EMULATOR_TEST -ErrorAction SilentlyContinue
    Remove-Item Env:COSMOS_EMULATOR_ENDPOINT -ErrorAction SilentlyContinue
    Remove-Item Env:COSMOS_EMULATOR_KEY -ErrorAction SilentlyContinue
}
