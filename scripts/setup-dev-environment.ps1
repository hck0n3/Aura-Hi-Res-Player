# One-command dev setup for Windows (new PC, formatted disk, or fresh clone).
#
# Usage (from repo root):
#   .\scripts\setup-dev-environment.ps1
#
# Before formatting your PC, run first:
#   .\scripts\backup-dev-secrets.ps1

[CmdletBinding()]
param(
    [string]$SdkDir = "",
    [string]$BackupDir = (Join-Path $env:USERPROFILE "AuraHiResDevBackup"),
    [switch]$SkipExtensions,
    [switch]$SkipSdkPackages
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$LocalProps = Join-Path $Root "local.properties"
$LocalTemplate = Join-Path $Root "local.properties.template"
$KeystoreDir = Join-Path $Root "app\keystore"
$KeystoreFile = Join-Path $KeystoreDir "release.keystore"
$CredentialsFile = Join-Path $KeystoreDir "CREDENTIALS.txt"
$ExtensionsJson = Join-Path $Root ".vscode\extensions.json"

function Write-Step([string]$Title) {
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
}

function ConvertTo-LocalPropertyPath([string]$Path) {
    return ($Path -replace '\\', '\\')
}

function Read-LocalProperties([string]$Path) {
    $map = @{}
    $lines = @()
    if (-not (Test-Path $Path)) { return @{ Map = $map; Lines = $lines } }
    $lines = @(Get-Content $Path)
    foreach ($line in $lines) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        $map[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
    }
    return @{ Map = $map; Lines = $lines }
}

function Write-LocalProperties([hashtable]$Props, [string[]]$ExistingLines) {
    $keys = @("sdk.dir", "SUPERPOWERED_LICENSE_KEY", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD", "SUPERPOWERED_CERT_SHA256")
    $filtered = @()
    foreach ($line in $ExistingLines) {
        $drop = $false
        foreach ($key in $keys) {
            if ($line -match "^\s*$key\s*=") { $drop = $true; break }
        }
        if (-not $drop) { $filtered += $line }
    }
    while ($filtered.Count -gt 0 -and [string]::IsNullOrWhiteSpace($filtered[-1])) {
        $filtered = $filtered[0..($filtered.Count - 2)]
    }
    if ($filtered.Count -gt 0) { $filtered += "" }
    if ($Props.ContainsKey("sdk.dir") -and $Props["sdk.dir"]) {
        $filtered += "sdk.dir=$($Props['sdk.dir'])"
    }
    if ($Props.ContainsKey("SUPERPOWERED_LICENSE_KEY") -and $Props["SUPERPOWERED_LICENSE_KEY"]) {
        $filtered += ""
        $filtered += "# Superpowered commercial licence key (gitignored)."
        $filtered += "SUPERPOWERED_LICENSE_KEY=$($Props['SUPERPOWERED_LICENSE_KEY'])"
    }
    if ($Props.ContainsKey("STORE_PASSWORD") -and $Props["STORE_PASSWORD"]) {
        $filtered += ""
        $filtered += "# Release signing (gitignored)."
        $filtered += "STORE_PASSWORD=$($Props['STORE_PASSWORD'])"
        $filtered += "KEY_ALIAS=$($Props['KEY_ALIAS'])"
        $filtered += "KEY_PASSWORD=$($Props['KEY_PASSWORD'])"
    }
    Set-Content -Path $LocalProps -Value $filtered -Encoding UTF8
}

function Read-CredentialsFile([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $data = @{}
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*alias:\s*(.+)\s*$') { $data["KEY_ALIAS"] = $Matches[1].Trim() }
        elseif ($line -match '^\s*storePassword:\s*(.+)\s*$') { $data["STORE_PASSWORD"] = $Matches[1].Trim() }
        elseif ($line -match '^\s*keyPassword:\s*(.+)\s*$') { $data["KEY_PASSWORD"] = $Matches[1].Trim() }
    }
    if (-not $data["KEY_PASSWORD"] -and $data["STORE_PASSWORD"]) { $data["KEY_PASSWORD"] = $data["STORE_PASSWORD"] }
    if ($data["STORE_PASSWORD"] -and $data["KEY_ALIAS"]) { return $data }
    return $null
}

function Find-SdkDir {
    param([string]$Preferred)
    $candidates = @()
    if ($Preferred) { $candidates += $Preferred }
    $candidates += @(
        "C:\Android\Sdk",
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk")
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    return $null
}

function Find-SdkManager([string]$Sdk) {
    $latest = Join-Path $Sdk "cmdline-tools\latest\bin\sdkmanager.bat"
    if (Test-Path $latest) { return $latest }
    Get-ChildItem (Join-Path $Sdk "cmdline-tools") -Recurse -Filter "sdkmanager.bat" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}

function Restore-FromBackup([string]$Backup) {
    if (-not (Test-Path $Backup)) { return $false }
    Write-Step "Restore from backup"
    Write-Host "Using backup: $Backup"
    $restoredCount = 0
    $backupKeystore = Join-Path $Backup "keystore"
    if (Test-Path $backupKeystore) {
        New-Item -ItemType Directory -Path $KeystoreDir -Force | Out-Null
        $keystoreFiles = @(Get-ChildItem $backupKeystore -File)
        foreach ($file in $keystoreFiles) {
            Copy-Item $file.FullName (Join-Path $KeystoreDir $file.Name) -Force
            Write-Host "  restored app/keystore/$($file.Name)" -ForegroundColor Green
        }
        $restoredCount += $keystoreFiles.Count
    }
    $backupProps = Join-Path $Backup "local.properties"
    if ((Test-Path $backupProps) -and -not (Test-Path $LocalProps)) {
        Copy-Item $backupProps $LocalProps -Force
        Write-Host "  restored local.properties" -ForegroundColor Green
        $restoredCount++
    }
    return $restoredCount -gt 0
}

Write-Host ""
Write-Host "Aura Hi-Res Player - dev environment setup (Windows)" -ForegroundColor Magenta
Write-Host "Repo: $Root"

Write-Step "1/6 Restore secrets (if needed)"
if (-not (Test-Path $KeystoreFile)) {
    if (Restore-FromBackup $BackupDir) {
        Write-Host "Backup restored." -ForegroundColor Green
    } else {
        Write-Host "No keystore yet. Copy app/keystore/ from backup or decode GitHub secret RELEASE_KEYSTORE_BASE64." -ForegroundColor Yellow
    }
} else {
    Write-Host "Keystore already present." -ForegroundColor Green
}

Write-Step "2/6 Android SDK"
$sdk = Find-SdkDir $SdkDir
if (-not $sdk) {
    Write-Host "Android SDK not found." -ForegroundColor Red
    Write-Host "Install Android Studio or SDK to C:\Android\Sdk, then re-run this script."
    Write-Host "  winget install Google.AndroidStudio"
    exit 1
}
Write-Host "SDK: $sdk" -ForegroundColor Green

if (-not $SkipSdkPackages) {
    $sdkmanager = Find-SdkManager $sdk
    if ($sdkmanager) {
        Write-Host "Installing required SDK packages (may take a few minutes)..."
        $packages = @(
            "platform-tools",
            "platforms;android-36",
            "build-tools;36.0.0",
            "ndk;27.0.12077973",
            "cmake;3.22.1"
        )
        & $sdkmanager --sdk_root=$sdk $packages 2>&1 | Out-Host
    } else {
        Write-Host "sdkmanager not found. Install Android SDK Command-line Tools from Android Studio." -ForegroundColor Yellow
    }
}

Write-Step "3/6 local.properties"
$existing = Read-LocalProperties $LocalProps
$props = @{}
$props["sdk.dir"] = ConvertTo-LocalPropertyPath $sdk

if ($existing.Map.ContainsKey("SUPERPOWERED_LICENSE_KEY")) {
    $props["SUPERPOWERED_LICENSE_KEY"] = $existing.Map["SUPERPOWERED_LICENSE_KEY"]
} else {
    $backupProps = Read-LocalProperties (Join-Path $BackupDir "local.properties")
    if ($backupProps.Map.ContainsKey("SUPERPOWERED_LICENSE_KEY")) {
        $props["SUPERPOWERED_LICENSE_KEY"] = $backupProps.Map["SUPERPOWERED_LICENSE_KEY"]
    }
}

$creds = Read-CredentialsFile $CredentialsFile
if ($creds) {
    $props["STORE_PASSWORD"] = $creds["STORE_PASSWORD"]
    $props["KEY_ALIAS"] = $creds["KEY_ALIAS"]
    $props["KEY_PASSWORD"] = $creds["KEY_PASSWORD"]
    Write-Host "Signing loaded from app/keystore/CREDENTIALS.txt" -ForegroundColor Green
} elseif ($existing.Map.ContainsKey("STORE_PASSWORD")) {
    $props["STORE_PASSWORD"] = $existing.Map["STORE_PASSWORD"]
    $props["KEY_ALIAS"] = $existing.Map["KEY_ALIAS"]
    $props["KEY_PASSWORD"] = $existing.Map["KEY_PASSWORD"]
} else {
    Write-Host "No signing credentials yet. Add app/keystore/CREDENTIALS.txt or run setup-local-signing.ps1" -ForegroundColor Yellow
}

$baseLines = if ($existing.Lines.Count -gt 0) { $existing.Lines } else { @(Get-Content $LocalTemplate -ErrorAction SilentlyContinue) }
Write-LocalProperties $props $baseLines
Write-Host "Wrote local.properties" -ForegroundColor Green

Write-Step "4/6 Cursor extensions"
if ($SkipExtensions) {
    Write-Host "Skipped (-SkipExtensions)."
} elseif (-not (Get-Command cursor -ErrorAction SilentlyContinue)) {
    Write-Host "cursor CLI not found. Install extensions manually from .vscode/extensions.json" -ForegroundColor Yellow
} else {
    $exts = @()
    if (Test-Path $ExtensionsJson) {
        $json = Get-Content $ExtensionsJson -Raw | ConvertFrom-Json
        $exts = @($json.recommendations)
    }
    foreach ($ext in $exts) {
        Write-Host "  installing $ext ..."
        & cursor --install-extension $ext --force 2>&1 | Out-Null
    }
    Write-Host "Extensions installed." -ForegroundColor Green
}

Write-Step "5/6 IDE cleanup"
& (Join-Path $PSScriptRoot "fix-ide-java.ps1") | Out-Null

Write-Step "6/6 Verify"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& (Join-Path $PSScriptRoot "setup-local-signing.ps1") -VerifyOnly 2>&1 | Out-Host
$verifyExit = $LASTEXITCODE
$ErrorActionPreference = $prevEap

Write-Host ""
Write-Host "Daily commands:" -ForegroundColor Magenta
Write-Host "  Debug build:   .\gradlew.bat assembleUniversalFossDebug -Pnosub=true"
Write-Host "  Before release: .\scripts\pre-publish-check.ps1 -Build"
Write-Host "  Backup secrets: .\scripts\backup-dev-secrets.ps1"
Write-Host ""
Write-Host "Publish to ALL users: push tag vX.Y.Z (no -beta) after pre-publish-check passes." -ForegroundColor Yellow
Write-Host "GitHub Actions uses secrets RELEASE_* and SUPERPOWERED_LICENSE_KEY."
Write-Host ""
Write-Host "One-time after setup in Cursor: Java: Clean Java Language Server Workspace -> Reload Window"

if ($verifyExit -ne 0) { exit $verifyExit }
exit 0
