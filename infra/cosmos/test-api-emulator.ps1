param(
    [string]$EnvironmentFile = ".local/cosmos/emulator-env.ps1"
)

$ErrorActionPreference = "Stop"
$testScript = Join-Path $PSScriptRoot "test-emulator.ps1"
& $testScript -EnvironmentFile $EnvironmentFile -Test "CosmosCatalogoSeedIT"
& $testScript -EnvironmentFile $EnvironmentFile -Test "CosmosApiEmulatorIT"
