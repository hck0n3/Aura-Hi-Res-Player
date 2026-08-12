# Publish an owner notice to Aura (announcements.json on main) without an APK or Firebase.
#
# Usage (from repo root):
#   .\scripts\publish-announcement.ps1 -Title "Mantenimiento YTM" -Body "Si falla el stream, reinicia Aura." -Priority important
#   .\scripts\publish-announcement.ps1 -Title "..." -Body "..." -Url "https://..." -DryRun
#   .\scripts\publish-announcement.ps1   # interactive prompts for title/body
#
# What it does:
#   1. Optionally git pull --ff-only on main
#   2. Prepends a new item to announcements.json (id, date UTC, updatedAt)
#   3. Commits only that file
#   4. Asks before git push origin main (skipped with -DryRun)
#
# Users see the notice when they open/bring Aura to the foreground (inbox under avatar/gear).
# No system push while the app is closed -- by design (no Firebase).
#
# Requires: git on PATH, repo on main (or will checkout), write access to origin.

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Title = "",
    [string]$Body = "",
    [ValidateSet("info", "important", "urgent")]
    [string]$Priority = "info",
    [string]$Url = "",
    [switch]$SkipPull,
    [switch]$Yes,
    # Write local JSON only -- no commit/push.
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Require-Git {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "git not found on PATH. Install Git for Windows and retry."
    }
}

function Read-Required([string]$Prompt, [string]$Current) {
    if (-not [string]::IsNullOrWhiteSpace($Current)) { return $Current.Trim() }
    $value = Read-Host $Prompt
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Value required: $Prompt"
    }
    return $value.Trim()
}

$repoRoot = Get-RepoRoot
$jsonPath = Join-Path $repoRoot "announcements.json"
$isDryRun = [bool]$DryRun
Require-Git

Push-Location $repoRoot
try {
    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($branch -ne "main") {
        Write-Host "Current branch is '$branch'. Checking out main..." -ForegroundColor Yellow
        git checkout main
        if ($LASTEXITCODE -ne 0) { throw "Failed to checkout main." }
    }

    if (-not $SkipPull) {
        Write-Host "Pulling latest main (ff-only)..." -ForegroundColor Cyan
        git pull --ff-only origin main
        if ($LASTEXITCODE -ne 0) {
            throw "git pull --ff-only failed. Resolve divergence, then retry."
        }
    }

    if (-not (Test-Path $jsonPath)) {
        throw "Missing announcements.json at $jsonPath"
    }

    $Title = Read-Required "Title" $Title
    $Body = Read-Required "Body" $Body
    if ($Url) { $Url = $Url.Trim() }

    $now = [DateTime]::UtcNow
    $id = "aviso-{0}" -f $now.ToString("yyyyMMdd-HHmmss")
    $date = $now.ToString("yyyy-MM-dd")
    $updatedAt = $now.ToString("yyyy-MM-ddTHH:mm:ssZ")

    $raw = Get-Content -Path $jsonPath -Raw -Encoding UTF8
    $doc = $raw | ConvertFrom-Json
    if (-not $doc.items) {
        $doc | Add-Member -NotePropertyName items -NotePropertyValue @() -Force
    }

    $item = [ordered]@{
        id          = $id
        date        = $date
        publishedAt = $updatedAt
        title       = $Title
        body        = $Body
        priority    = $Priority
    }
    if ($Url) { $item.url = $Url }

    $existing = @($doc.items)
    $doc.updatedAt = $updatedAt
    $doc.items = @([pscustomobject]$item) + $existing

    $jsonOut = $doc | ConvertTo-Json -Depth 8
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($jsonPath, $jsonOut + "`n", $utf8NoBom)

    Write-Host ""
    Write-Host "Preview:" -ForegroundColor Cyan
    Write-Host ("  id:       {0}" -f $id)
    Write-Host ("  date:     {0}" -f $date)
    Write-Host ("  priority: {0}" -f $Priority)
    Write-Host ("  title:    {0}" -f $Title)
    Write-Host ("  body:     {0}" -f $Body)
    if ($Url) { Write-Host ("  url:      {0}" -f $Url) }
    Write-Host ""

    if ($isDryRun) {
        Write-Host "[DryRun] Wrote local announcements.json only -- no commit/push." -ForegroundColor Yellow
        Write-Host "Restore with: git checkout -- announcements.json" -ForegroundColor Yellow
        return
    }

    $porcelainOut = & git status --porcelain -- announcements.json
    if ($LASTEXITCODE -ne 0) {
        throw "git status failed."
    }
    if ($null -eq $porcelainOut) {
        $porcelainOut = ""
    }
    $statusText = [string](($porcelainOut | ForEach-Object { "$_" }) -join "`n")
    if ([string]::IsNullOrWhiteSpace($statusText)) {
        throw "announcements.json unchanged after write - aborting."
    }

    git add -- announcements.json
    if ($LASTEXITCODE -ne 0) { throw "git add failed." }

    $commitMsg = "announce: $Title"
    git commit -m $commitMsg
    if ($LASTEXITCODE -ne 0) { throw "git commit failed." }

    Write-Host "Committed: $commitMsg" -ForegroundColor Green

    $doPush = [bool]$Yes
    if (-not $doPush) {
        $answer = Read-Host "Push to origin main now? [y/N]"
        $doPush = ($answer -eq "y" -or $answer -eq "Y" -or $answer -eq "yes")
    }

    if (-not $doPush) {
        Write-Host "Commit kept locally. Push later with: git push origin main" -ForegroundColor Yellow
        return
    }

    if ($PSCmdlet.ShouldProcess("origin/main", "git push announcements.json")) {
        git push origin main
        if ($LASTEXITCODE -ne 0) {
            throw "git push failed. Check credentials (Git Credential Manager / gh auth) and retry."
        }
        Write-Host "Pushed. Users see it when they open Aura (or Avisos). CDN may lag a few seconds." -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
