# Configures release signing credentials in local.properties (gitignored).
# Validates the keystore with keytool and checks the certificate owner before saving.
#
# Usage (from repo root):
#   .\scripts\setup-local-signing.ps1
#   .\scripts\setup-local-signing.ps1 -VerifyOnly
#
# -StorePassword/-KeyPassword are plain strings by design: they end up as plain command-line
# arguments to keytool.exe and as plain values in local.properties (read by Gradle as plain text
# too), so a SecureString here would only be decrypted again a few lines later with no real gain.
[Diagnostics.CodeAnalysis.SuppressMessageAttribute(
    'PSAvoidUsingPlainTextForPassword', '',
    Justification = 'Values are forwarded as plain args to keytool.exe and stored as plain text in local.properties; SecureString would be decrypted immediately with no security benefit.'
)]
[CmdletBinding()]
param(
    [switch]$VerifyOnly,
    [string]$StorePassword = "",
    [string]$KeyAlias = "",
    [string]$KeyPassword = "",
    [switch]$NonInteractive
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Keystore = Join-Path $Root "app\keystore\release.keystore"
$LocalProps = Join-Path $Root "local.properties"

function Read-LocalProperties([string]$Path) {
    $map = @{}
    $lines = @()
    if (Test-Path $Path) {
        $lines = Get-Content $Path
        foreach ($line in $lines) {
            if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
            $idx = $line.IndexOf('=')
            if ($idx -lt 1) { continue }
            $map[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
        }
    }
    return [pscustomobject]@{ Map = $map; Lines = $lines }
}

function Read-Secret([string]$Message) {
    $secure = Read-Host $Message -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Test-KeystoreCredentials {
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute(
        'PSAvoidUsingPlainTextForPassword', '',
        Justification = 'Forwarded as-is to keytool.exe, which only accepts plain-text -storepass arguments.'
    )]
    param(
        [string]$StorePath,
        [string]$StorePassword,
        [string]$KeyAlias
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    # JDK 25 can throw MissingFormatArgumentException on `-list -v`; `-list` alone is enough to validate.
    $output = & keytool -list -keystore $StorePath -storepass $StorePassword -alias $KeyAlias 2>&1 | Out-String
    $exit = $LASTEXITCODE
    if ($exit -ne 0 -and $output -match 'Alias name:\s*\Q' + [regex]::Escape($KeyAlias) + '\E') {
        $exit = 0
    }
    if ($exit -ne 0) {
        $ErrorActionPreference = $prevEap
        return [pscustomobject]@{ Ok = $false; Output = $output.Trim() }
    }
    $owner = ""
    $verbose = & keytool -list -v -keystore $StorePath -storepass $StorePassword -alias $KeyAlias 2>&1 | Out-String
    $ownerMatch = [regex]::Match($verbose, '(?m)^Owner:\s*(.+)$')
    if ($ownerMatch.Success) {
        $owner = $ownerMatch.Groups[1].Value.Trim()
    } elseif ($verbose -match 'MissingFormatArgumentException' -and $output -match 'PrivateKeyEntry') {
        $owner = "UNKNOWN (keytool -v failed on this JDK; credentials accepted)"
    }
    $ErrorActionPreference = $prevEap
    return [pscustomobject]@{ Ok = $true; Output = $output.Trim(); Owner = $owner }
}

function Write-LocalSigningProps {
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute(
        'PSAvoidUsingPlainTextForPassword', '',
        Justification = 'Written as plain text into local.properties, which Gradle also reads as plain text; a SecureString here would be decrypted before writing anyway.'
    )]
    param(
        [string]$Path,
        [string[]]$ExistingLines,
        [string]$StorePassword,
        [string]$KeyAlias,
        [string]$KeyPassword
    )
    $keys = @("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
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
    $filtered += ""
    $filtered += "# Release signing (gitignored). Same values as GitHub secrets RELEASE_*."
    $filtered += "STORE_PASSWORD=$StorePassword"
    $filtered += "KEY_ALIAS=$KeyAlias"
    $filtered += "KEY_PASSWORD=$KeyPassword"
    Set-Content -Path $Path -Value $filtered -Encoding UTF8
}

if (-not (Test-Path $Keystore)) {
    Write-Host "ERROR: Keystore not found at app/keystore/release.keystore" -ForegroundColor Red
    Write-Host "Copy your release keystore there, or decode RELEASE_KEYSTORE_BASE64 from GitHub secrets."
    exit 1
}

$props = Read-LocalProperties $LocalProps
$storePassword = $props.Map["STORE_PASSWORD"]
$keyAlias = $props.Map["KEY_ALIAS"]
$keyPassword = $props.Map["KEY_PASSWORD"]

if ($VerifyOnly) {
    if (-not $storePassword -or -not $keyAlias -or -not $keyPassword) {
        Write-Host "ERROR: STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD missing in local.properties" -ForegroundColor Red
        exit 1
    }
} elseif ($NonInteractive) {
    if ([string]::IsNullOrWhiteSpace($StorePassword) -or [string]::IsNullOrWhiteSpace($KeyAlias)) {
        Write-Host "ERROR: -NonInteractive requires -StorePassword and -KeyAlias." -ForegroundColor Red
        exit 1
    }
    $storePassword = $StorePassword
    $keyAlias = $KeyAlias.Trim()
    if ([string]::IsNullOrWhiteSpace($KeyPassword)) {
        $keyPassword = $storePassword
    } else {
        $keyPassword = $KeyPassword
    }
} else {
    Write-Host ""
    Write-Host "Aura Hi-Res Player - local signing setup" -ForegroundColor Magenta
    Write-Host "Keystore: app/keystore/release.keystore"
    Write-Host ""
    Write-Host "Enter the SAME credentials stored in GitHub secrets:"
    Write-Host "  RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
    Write-Host ""

    $defaultAlias = if ($keyAlias) { $keyAlias } else { "jrmusic" }
    $enteredAlias = Read-Host ("Key alias [{0}]" -f $defaultAlias)
    if ([string]::IsNullOrWhiteSpace($enteredAlias)) { $keyAlias = $defaultAlias } else { $keyAlias = $enteredAlias.Trim() }

    $storePassword = Read-Secret "Keystore password (STORE_PASSWORD)"
    if ([string]::IsNullOrWhiteSpace($storePassword)) {
        Write-Host "ERROR: empty password." -ForegroundColor Red
        exit 1
    }

    $same = Read-Host "Use the same password for the key entry? [Y/n]"
    if ($same -eq "" -or $same -match '^[Yy]') {
        $keyPassword = $storePassword
    } else {
        $keyPassword = Read-Secret "Key password (KEY_PASSWORD)"
    }
}

Write-Host ""
Write-Host "Validating keystore..." -ForegroundColor Cyan
$result = Test-KeystoreCredentials $Keystore $storePassword $keyAlias
if (-not $result.Ok) {
    Write-Host "ERROR: keytool rejected the credentials." -ForegroundColor Red
    Write-Host $result.Output
    exit 1
}

Write-Host "Certificate owner: $($result.Owner)" -ForegroundColor Green
if ($result.Owner -match 'CN=JR-MUSIC-PRO') {
    Write-Host "ERROR: This is the emergency CI keystore (CN=JR-MUSIC-PRO)." -ForegroundColor Red
    Write-Host "Replace app/keystore/release.keystore with your real release keystore (CN=JR MUSIC PRO)."
    exit 1
}
if ($result.Owner -notmatch 'CN=JR MUSIC PRO' -and $result.Owner -notmatch '^UNKNOWN \(keytool') {
    Write-Host "WARNING: Expected CN=JR MUSIC PRO. Confirm this is the keystore your users already have installed." -ForegroundColor Yellow
    if (-not $NonInteractive -and -not $VerifyOnly) {
        $cont = Read-Host "Continue anyway? [y/N]"
        if ($cont -notmatch '^[Yy]') { exit 1 }
    }
}

if (-not $VerifyOnly) {
    Write-LocalSigningProps $LocalProps $props.Lines $storePassword $keyAlias $keyPassword
    Write-Host ""
    Write-Host "Saved STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD to local.properties" -ForegroundColor Green
}

Write-Host ""
Write-Host "Running pre-publish check (Gradle config only)..." -ForegroundColor Cyan
& (Join-Path $PSScriptRoot "pre-publish-check.ps1") -SkipGh -SkipCompareLatest
exit $LASTEXITCODE
