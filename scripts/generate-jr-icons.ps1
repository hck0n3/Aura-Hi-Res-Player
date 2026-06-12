# Generates JR-MUSIC-PRO launcher icons (all densities) from the PC app icon.
# Source: 1024x1024 PNG with transparent rounded corners, off-center vinyl artwork.
param(
    [string]$Source = "C:\Users\Hck0n3\Desktop\2\assets\icon.png",
    [string]$Res = "C:\Users\Hck0n3\Desktop\Echo-Music-5.1.8\app\src\main\res"
)

Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::FromFile($Source)

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    return @($bmp, $g)
}

function Save-Png($bmp, [string]$path) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "OK $path"
}

# 1) Legacy square icon: full artwork resized (rounded corners are baked in).
$legacy = @{ "ldpi" = 36; "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($k in $legacy.Keys) {
    $s = $legacy[$k]
    $bmp, $g = New-Canvas $s
    $g.DrawImage($src, 0, 0, $s, $s)
    $g.Dispose()
    Save-Png $bmp "$Res\mipmap-$k\ic_launcher.png"
    $bmp.Dispose()
}

# 2) Round icon: circle-clipped full-bleed artwork.
foreach ($k in $legacy.Keys) {
    $s = $legacy[$k]
    $bmp, $g = New-Canvas $s
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $s, $s)
    $g.SetClip($path)
    $g.DrawImage($src, 0, 0, $s, $s)
    $g.Dispose()
    Save-Png $bmp "$Res\mipmap-$k\ic_launcher_round.png"
    $bmp.Dispose()
}

# 3) Adaptive foreground: artwork at 67% of canvas, centered, transparent borders.
#    67% keeps the off-center vinyl (max radial extent 0.456 of source width)
#    inside the 33dp/108dp safe-zone radius: 0.456 * 0.67 = 0.306 <= 0.3055... ~ok.
$fg = @{ "mdpi" = 108; "hdpi" = 162; "xhdpi" = 216; "xxhdpi" = 324; "xxxhdpi" = 432 }
foreach ($k in $fg.Keys) {
    $s = $fg[$k]
    $art = [int]($s * 0.67)
    $off = [int](($s - $art) / 2)
    $bmp, $g = New-Canvas $s
    $g.DrawImage($src, $off, $off, $art, $art)
    $g.Dispose()
    Save-Png $bmp "$Res\mipmap-$k\ic_launcher_foreground.png"
    $bmp.Dispose()
}

# 4) Adaptive background: diagonal purple gradient matching the PC icon body.
foreach ($k in $fg.Keys) {
    $s = $fg[$k]
    $bmp, $g = New-Canvas $s
    $rect = New-Object System.Drawing.Rectangle(0, 0, $s, $s)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(255, 0x71, 0x38, 0xD6),
        [System.Drawing.Color]::FromArgb(255, 0x43, 0x19, 0x7F),
        [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
    $g.FillRectangle($brush, $rect)
    $brush.Dispose()
    $g.Dispose()
    Save-Png $bmp "$Res\mipmap-$k\ic_launcher_bg.png"
    $bmp.Dispose()
}

# 5) Monochrome (themed icon): white vinyl donut silhouette.
foreach ($k in $fg.Keys) {
    $s = $fg[$k]
    $bmp, $g = New-Canvas $s
    $outer = $s * 0.61
    $hole = $s * 0.16
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath([System.Drawing.Drawing2D.FillMode]::Alternate)
    $path.AddEllipse([float](($s - $outer) / 2), [float](($s - $outer) / 2), [float]$outer, [float]$outer)
    $path.AddEllipse([float](($s - $hole) / 2), [float](($s - $hole) / 2), [float]$hole, [float]$hole)
    $g.FillPath([System.Drawing.Brushes]::White, $path)
    $g.Dispose()
    Save-Png $bmp "$Res\mipmap-$k\ic_launcher_monochrome.png"
    $bmp.Dispose()
}

# 6) In-app logo for the top bar.
$bmp, $g = New-Canvas 256
$g.DrawImage($src, 0, 0, 256, 256)
$g.Dispose()
Save-Png $bmp "$Res\drawable-nodpi\jr_logo.png"
$bmp.Dispose()

$src.Dispose()
Write-Host "DONE"
