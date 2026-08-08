# Pre-publish gate for Aura Hi-Res Player.
# Runs the checks that must pass before tagging a release that reaches all users.
#
# Usage (from repo root):
#   .\scripts\pre-publish-check.ps1              # secrets + Gradle config + APK cert (if APK exists)
#   .\scripts\pre-publish-check.ps1 -Build       # also compiles assembleUniversalGmsRelease
#   .\scripts\pre-publish-check.ps1 -ApkPath X   # verify a specific APK
#
# Requires: gh (GitHub CLI, authenticated), JDK/Gradle wrapper, Android SDK (sdk.dir in local.properties).
# Signing for -Build: STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD in env or local.properties.

[CmdletBinding()]
param(
    [switch]$Build,
    [string]$ApkPath = "",
    [string]$Repo = "hck0n3/Aura-Hi-Res-Player",
    [switch]$SkipGh,
    [switch]$SkipCompareLatest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Script:FailCount = 0
$Script:WarnCount = 0

function Write-Check([string]$Label, [string]$Status, [string]$Detail) {
    $color = switch ($Status) {
        "PASS" { "Green" }
        "WARN" { "Yellow" }
        "FAIL" { "Red" }
        "INFO" { "Cyan" }
        default { "White" }
    }
    Write-Host ("[{0}] {1}" -f $Status, $Label) -ForegroundColor $color
    if ($Detail) { Write-Host "      $Detail" }
    if ($Status -eq "FAIL") { $Script:FailCount++ }
    if ($Status -eq "WARN") { $Script:WarnCount++ }
}

function Get-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Read-LocalProperties([string]$Path) {
    $map = @{}
    if (-not (Test-Path $Path)) { return $map }
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        $map[$key] = $value
    }
    return $map
}

function ConvertFrom-LocalPropertyPath([string]$Value) {
    return $Value -replace '\\:', ':'
}

function Get-SdkDir([hashtable]$Props) {
    if ($Props.ContainsKey("sdk.dir") -and $Props["sdk.dir"]) {
        return (ConvertFrom-LocalPropertyPath $Props["sdk.dir"])
    }
    $fallback = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $fallback) { return $fallback }
    return $null
}

function Find-ApkSigner([string]$SdkDir) {
    if (-not $SdkDir) { return $null }
    $toolsRoot = Join-Path $SdkDir "build-tools"
    if (-not (Test-Path $toolsRoot)) { return $null }
    $candidates = Get-ChildItem $toolsRoot -Directory | Sort-Object Name -Descending
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir.FullName "apksigner.bat"
        if (Test-Path $exe) { return $exe }
    }
    return $null
}

function Get-ApkCertSummary([string]$ApkSigner, [string]$Apk) {
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $ApkSigner verify --print-certs $Apk 2>&1 | Out-String
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($exit -ne 0) {
        throw "apksigner failed: $output"
    }
    $sha256 = [regex]::Match($output, 'certificate SHA-256 digest:\s*([0-9a-f:]+)', 'IgnoreCase').Groups[1].Value
    # Older apksigner prints "Owner:"; newer build-tools (36+) print "certificate DN:" instead.
    $ownerMatch = [regex]::Match($output, '(?:^|\n)\s*Owner:\s*(.+)', 'IgnoreCase')
    if (-not $ownerMatch.Success) {
        $ownerMatch = [regex]::Match($output, 'certificate DN:\s*(.+)', 'IgnoreCase')
    }
    $owner = $ownerMatch.Groups[1].Value.Trim()
    return [pscustomobject]@{
        Output = $output
        Sha256 = ($sha256 -replace ':', '').ToLowerInvariant()
        Owner = $owner
    }
}

function Test-GitHubSecrets([string]$Repo) {
    if ($SkipGh) {
        Write-Check "1/4 GitHub secrets" "INFO" "Skipped (-SkipGh)."
        return
    }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Check "1/4 GitHub secrets" "FAIL" "gh CLI not found. Install GitHub CLI or pass -SkipGh."
        return
    }
    $required = @(
        "RELEASE_KEYSTORE_BASE64",
        "SUPERPOWERED_LICENSE_KEY",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD"
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $ghRaw = & gh secret list --repo $Repo 2>&1 | Out-String
    $ghExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($ghExit -ne 0) {
        Write-Check "1/4 GitHub secrets" "FAIL" ("gh secret list failed (exit {0}). Run: gh auth login" -f $ghExit)
        return
    }
    $listed = $ghRaw -split "`n" |
        ForEach-Object { ($_ -split "\s+")[0].Trim() } |
        Where-Object { $_ }
    $missing = @()
    foreach ($name in $required) {
        if ($listed -notcontains $name) { $missing += $name }
    }
    if ($missing.Count -gt 0) {
        Write-Check "1/4 GitHub secrets" "FAIL" ("Missing in repo {0}: {1}" -f $Repo, ($missing -join ", "))
        return
    }
    Write-Check "1/4 GitHub secrets" "PASS" ("All required secrets present in {0}." -f $Repo)
}

function Test-ReleaseMetadata([string]$Root) {
    $gradle = Join-Path $Root "app\build.gradle.kts"
    $releaseInfo = Join-Path $Root "RELEASE_INFO.md"
    if (-not (Test-Path $releaseInfo)) {
        Write-Check "Release metadata" "FAIL" "RELEASE_INFO.md not found."
        return
    }
    $title = (Get-Content $releaseInfo -TotalCount 1).Trim()
    if (-not $title -or $title -eq "#") {
        Write-Check "Release metadata" "FAIL" "RELEASE_INFO.md line 1 must be the release title."
        return
    }
    $versionName = [regex]::Match((Get-Content $gradle -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $versionCode = [regex]::Match((Get-Content $gradle -Raw), 'versionCode\s*=\s*(\d+)').Groups[1].Value
    if ($versionName -match '-beta|-test') {
        Write-Check "Release metadata" "WARN" ("versionName={0} looks like a prerelease tag." -f $versionName)
    } else {
        Write-Check "Release metadata" "PASS" ("versionName={0}, versionCode={1}, title OK." -f $versionName, $versionCode)
    }
}

function Test-LocalSigning([string]$Root, [hashtable]$Props) {
    $keystore = Join-Path $Root "app\keystore\release.keystore"
    if (-not (Test-Path $keystore)) {
        Write-Check "Local signing readiness" "WARN" "app/keystore/release.keystore missing (CI may still sign)."
        return
    }
    $storePassword = $env:STORE_PASSWORD
    if (-not $storePassword -and $Props.ContainsKey("STORE_PASSWORD")) { $storePassword = $Props["STORE_PASSWORD"] }
    $keyAlias = $env:KEY_ALIAS
    if (-not $keyAlias -and $Props.ContainsKey("KEY_ALIAS")) { $keyAlias = $Props["KEY_ALIAS"] }
    $keyPassword = $env:KEY_PASSWORD
    if (-not $keyPassword -and $Props.ContainsKey("KEY_PASSWORD")) { $keyPassword = $Props["KEY_PASSWORD"] }
    if (-not $storePassword -or -not $keyAlias -or -not $keyPassword) {
        Write-Check "Local signing readiness" "WARN" "Keystore exists but STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD not set. Local -Build would ship an UNBOUND Superpowered key."
        return
    }
    Write-Check "Local signing readiness" "PASS" "Release keystore + credentials available for local builds."
}

function Invoke-GradleGate([string]$Root, [switch]$DoBuild) {
    Push-Location $Root
    try {
        $task = if ($DoBuild) { "assembleUniversalGmsRelease" } else { "projects" }
        Write-Check "2/4 Gradle Superpowered gate" "INFO" ("Running .\\gradlew.bat {0} ..." -f $task)
        $output = & .\gradlew.bat $task --no-daemon 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) {
            Write-Check "2/4 Gradle Superpowered gate" "FAIL" ("Gradle failed. Last lines:`n{0}" -f (($output -split "`n")[-12..-1] -join "`n"))
            return $null
        }
        if ($output -match 'SUPERPOWERED:\s*no licence key configured') {
            Write-Check "2/4 Gradle Superpowered gate" "FAIL" "Build would ship WITHOUT the Superpowered engine."
            return $output
        }
        if ($output -match 'SUPERPOWERED:.*embedded UNBOUND') {
            Write-Check "2/4 Gradle Superpowered gate" "WARN" "Key is UNBOUND (engine works, clone protection weaker). Set STORE_PASSWORD or SUPERPOWERED_CERT_SHA256."
            return $output
        }
        if ($output -match 'Generated fallback CI keystore') {
            Write-Check "2/4 Gradle Superpowered gate" "FAIL" "Emergency keystore detected (CN=JR-MUSIC-PRO). Users cannot install over the real app."
            return $output
        }
        Write-Check "2/4 Gradle Superpowered gate" "PASS" "No blocking SUPERPOWERED warnings in Gradle output."
        return $output
    } finally {
        Pop-Location
    }
}

function Test-BuildConfigBinding([string]$Root) {
    $searchDir = Join-Path $Root "app\build\generated\source\buildConfig\universalGms\release"
    $files = if (Test-Path $searchDir) {
        Get-ChildItem -Path $searchDir -Filter "BuildConfig.java" -Recurse -ErrorAction SilentlyContinue
    } else {
        $null
    }
    if (-not $files) {
        Write-Check "Superpowered binding (BuildConfig)" "INFO" "BuildConfig not found yet. Run with -Build or pass -ApkPath after a release build."
        return
    }
    $content = Get-Content $files[0].FullName -Raw
    if ($content -match 'SUPERPOWERED_LICENSE_BOUND\s*=\s*true') {
        Write-Check "Superpowered binding (BuildConfig)" "PASS" "SUPERPOWERED_LICENSE_BOUND=true in release BuildConfig."
        return
    }
    if ($content -match 'SUPERPOWERED_LICENSE\s*=\s*""') {
        Write-Check "Superpowered binding (BuildConfig)" "FAIL" "SUPERPOWERED_LICENSE is empty in release BuildConfig."
        return
    }
    Write-Check "Superpowered binding (BuildConfig)" "WARN" "SUPERPOWERED_LICENSE_BOUND is false (unbound release)."
}

function Resolve-ReleaseApk([string]$Root, [string]$ExplicitPath) {
    if ($ExplicitPath) {
        if (-not (Test-Path $ExplicitPath)) { throw "APK not found: $ExplicitPath" }
        return (Resolve-Path $ExplicitPath).Path
    }
    $dir = Join-Path $Root "app\build\outputs\apk\universalGms\release"
    if (-not (Test-Path $dir)) { return $null }
    $apk = Get-ChildItem $dir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apk) { return $apk.FullName }
    return $null
}

function Test-ApkCertificate([string]$ApkSigner, [string]$Apk, [string]$Repo, [switch]$SkipCompare) {
    if (-not $Apk) {
        Write-Check "3/4 APK signing certificate" "INFO" "No release APK found. Use -Build or -ApkPath."
        return
    }
    $cert = Get-ApkCertSummary $ApkSigner $Apk
    if ($cert.Owner -match 'CN=JR-MUSIC-PRO') {
        Write-Check "3/4 APK signing certificate" "FAIL" "Emergency CI keystore detected (CN=JR-MUSIC-PRO). Expected CN=JR MUSIC PRO."
        return
    }
    if ($cert.Owner -notmatch 'CN=JR MUSIC PRO') {
        Write-Check "3/4 APK signing certificate" "FAIL" ("Unexpected signer: {0}" -f $cert.Owner)
        return
    }
    Write-Check "3/4 APK signing certificate" "PASS" ("Signer OK. SHA-256={0}" -f $cert.Sha256)

    if ($SkipCompare -or $SkipGh) { return }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { return }

    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("aura-pre-publish-{0}" -f [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    try {
        & gh release download --repo $Repo --pattern "*.apk" --dir $tmp 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Check "Compare with latest release" "WARN" "Could not download latest release APK for cert compare."
            return
        }
        $published = Get-ChildItem $tmp -Filter "*.apk" | Select-Object -First 1
        if (-not $published) {
            Write-Check "Compare with latest release" "WARN" "Latest GitHub release has no APK asset."
            return
        }
        $pubCert = Get-ApkCertSummary $ApkSigner $published.FullName
        if ($pubCert.Sha256 -eq $cert.Sha256) {
            Write-Check "Compare with latest release" "PASS" "Certificate matches the currently published APK."
        } else {
            Write-Check "Compare with latest release" "FAIL" "Certificate differs from latest published APK. In-place updates will fail for all users."
        }
    } finally {
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Show-OnDeviceReminder {
    $detail = @(
        "Install the candidate APK on a test device, play one track, then open Ajustes > Registros."
        "Search for: SUPERPOWERED licence=ok binding=certificate"
        "Red flags: licence=absent, signature_mismatch, engine=DEGRADED"
    ) -join " "
    Write-Check "4/4 On-device Superpowered log" "INFO" $detail
}

$root = Get-RepoRoot
$props = Read-LocalProperties (Join-Path $root "local.properties")
$sdkDir = Get-SdkDir $props
$apksigner = Find-ApkSigner $sdkDir

Write-Host ""
Write-Host "Aura Hi-Res Player - pre-publish check" -ForegroundColor Magenta
Write-Host ("Repo: {0}" -f $root)
Write-Host ""

if (-not $apksigner) {
    Write-Check "Android SDK / apksigner" "FAIL" "apksigner not found. Set sdk.dir in local.properties."
    exit 1
}
Write-Check "Android SDK / apksigner" "PASS" $apksigner

Test-GitHubSecrets $Repo
Test-ReleaseMetadata $root
Test-LocalSigning $root $props
Invoke-GradleGate $root -DoBuild:$Build | Out-Null
Test-BuildConfigBinding $root

$apk = Resolve-ReleaseApk $root $ApkPath
Test-ApkCertificate $apksigner $apk $Repo -SkipCompare:$SkipCompareLatest
Show-OnDeviceReminder

Write-Host ""
if ($Script:FailCount -gt 0) {
    Write-Host ("BLOCKED: {0} failure(s), {1} warning(s). Do NOT publish." -f $Script:FailCount, $Script:WarnCount) -ForegroundColor Red
    exit 1
}
if ($Script:WarnCount -gt 0) {
    Write-Host ("CAUTION: {0} warning(s). Review before publishing." -f $Script:WarnCount) -ForegroundColor Yellow
    exit 2
}
Write-Host "READY: all automated checks passed." -ForegroundColor Green
exit 0
