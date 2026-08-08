# Clears stale Eclipse/Buildship state that causes:
# "Cannot add nature org.eclipse.buildship.core.gradleprojectnature"
#
# Usage: .\scripts\fix-ide-java.ps1
# Then in Cursor: Ctrl+Shift+P -> "Developer: Reload Window"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$removed = @()

foreach ($name in @(".project", ".classpath")) {
    Get-ChildItem $Root -Recurse -Force -Filter $name -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item $_.FullName -Force
        $removed += $_.FullName
    }
}

Get-ChildItem $Root -Recurse -Force -Directory -Filter ".settings" -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item $_.FullName -Recurse -Force
    $removed += $_.FullName
}

if ($removed.Count -eq 0) {
    Write-Host "No Eclipse metadata files found under $Root"
} else {
    Write-Host "Removed:"
    $removed | ForEach-Object { Write-Host "  $_" }
}

Write-Host ""
Write-Host "Next steps in Cursor:"
Write-Host "  1. Ctrl+Shift+P -> Java: Clean Java Language Server Workspace"
Write-Host "  2. Choose Restart and delete"
Write-Host "  3. Ctrl+Shift+P -> Developer: Reload Window"
Write-Host ""
Write-Host "This project uses Kotlin LS + Gradle extension for builds."
Write-Host "Java Buildship import is disabled in .vscode/settings.json."
