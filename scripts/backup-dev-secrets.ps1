# Backs up everything you need after cloning on a new PC or formatted disk.
# Saves OUTSIDE the repo (never commit these files).
#
# Usage:
#   .\scripts\backup-dev-secrets.ps1
#   .\scripts\backup-dev-secrets.ps1 -Destination D:\Backups\AuraHiRes

[CmdletBinding()]
param(
    [string]$Destination = (Join-Path $env:USERPROFILE "AuraHiResDevBackup")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$KeystoreDir = Join-Path $Root "app\keystore"
$LocalProps = Join-Path $Root "local.properties"

New-Item -ItemType Directory -Path $Destination -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Destination "keystore") -Force | Out-Null

$copied = @()

if (Test-Path $KeystoreDir) {
    Get-ChildItem $KeystoreDir -File -Force | ForEach-Object {
        $destFile = Join-Path (Join-Path $Destination "keystore") $_.Name
        Copy-Item $_.FullName $destFile -Force
        $copied += "keystore/$($_.Name)"
    }
} else {
    Write-Warning "No app/keystore folder found."
}

if (Test-Path $LocalProps) {
    Copy-Item $LocalProps (Join-Path $Destination "local.properties") -Force
    $copied += "local.properties"
} else {
    Write-Warning "No local.properties found."
}

$manifest = @(
    "Aura Hi-Res Player - developer backup"
    "Created: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    "Machine: $env:COMPUTERNAME"
    ""
    "Restore on a new PC:"
    "  1. Clone the repo"
    "  2. Run: .\scripts\setup-dev-environment.ps1"
    ""
    "Files:"
) + ($copied | ForEach-Object { "  - $_" })

Set-Content -Path (Join-Path $Destination "README.txt") -Value $manifest -Encoding UTF8

Write-Host ""
Write-Host "Backup saved to:" -ForegroundColor Green
Write-Host "  $Destination"
Write-Host ""
Write-Host "Copy this entire folder to USB / cloud BEFORE formatting your PC." -ForegroundColor Yellow
Write-Host "GitHub secrets (RELEASE_*, SUPERPOWERED_*) stay in GitHub; no need to back them up here."
