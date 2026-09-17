param(
    [string]$EnvironmentFile = ".local/cosmos/emulator-env.ps1",
    [string]$Test = "CosmosCatalogoEmulatorIT"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$environmentPath = Join-Path $projectRoot $EnvironmentFile
$trustStoreFile = Join-Path $projectRoot ".local/cosmos/cosmos-emulator-truststore.p12"
$trustStorePath = ".local/cosmos/cosmos-emulator-truststore.p12"
$mavenRepository = Join-Path $env:USERPROFILE ".m2/repository"
if (-not (Test-Path $environmentPath)) {
    throw "Arquivo local do ambiente Cosmos nao encontrado"
}
if (-not (Test-Path $trustStoreFile)) {
    throw "Truststore local do emulador Cosmos nao encontrado"
}

$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
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
        & mvn -o -B "-Dmaven.repo.local=$mavenRepository" "-Dtest=$Test" test
        if ($LASTEXITCODE -ne 0) {
            throw "Prova do emulador Cosmos falhou"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    Remove-Item Env:COSMOS_EMULATOR_TEST -ErrorAction SilentlyContinue
    Remove-Item Env:COSMOS_EMULATOR_ENDPOINT -ErrorAction SilentlyContinue
    Remove-Item Env:COSMOS_EMULATOR_KEY -ErrorAction SilentlyContinue
}
