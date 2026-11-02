# Draws Padampetti's icons (the mint TV on a dark navy gradient, like Nuvio's) into the app's resources:
# Android launcher icons, the TV banner, the in-app logo, and the Windows .ico. Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File tools\make-icons.ps1
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$app = Join-Path $root "composeApp"

function Hex($h) { [System.Drawing.Color]::FromArgb(255, [Convert]::ToInt32($h.Substring(0,2),16), [Convert]::ToInt32($h.Substring(2,2),16), [Convert]::ToInt32($h.Substring(4,2),16)) }
# The background, like Nuvio's: deep navy fading to near black, with a soft indigo glow at the top left
$navy = Hex "1C1E3F"; $night = Hex "0B0C1C"; $indigo = Hex "3A2C7A"
$mintTop = Hex "6EE7D2"; $mintBottom = Hex "14B8A6"; $ink = Hex "141830"

function RoundRect($x, $y, $w, $h, $r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc($x, $y, 2*$r, 2*$r, 180, 90); $p.AddArc($x+$w-2*$r, $y, 2*$r, 2*$r, 270, 90)
    $p.AddArc($x+$w-2*$r, $y+$h-2*$r, 2*$r, 2*$r, 0, 90); $p.AddArc($x, $y+$h-2*$r, 2*$r, 2*$r, 90, 90); $p.CloseFigure()
    return $p
}

# The mark, drawn in a 330 x 400 design box (antennas on top), scaled by $k with its top-left at ($ox, $oy)
function DrawMark($g, $ox, $oy, $k) {
    $bx = $ox; $by = $oy + 110*$k; $bw = 330*$k; $bh = 250*$k
    $pen = New-Object System.Drawing.Pen $mintTop, (16*$k); $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
    $g.DrawLine($pen, [float]($bx+$bw/2), [float]($by+4*$k), [float]($bx+$bw/2-70*$k), [float]($by-80*$k))
    $g.DrawLine($pen, [float]($bx+$bw/2), [float]($by+4*$k), [float]($bx+$bw/2+62*$k), [float]($by-92*$k))
    $knob = New-Object System.Drawing.SolidBrush $mintTop
    $g.FillEllipse($knob, [float]($bx+$bw/2-85*$k), [float]($by-95*$k), [float](30*$k), [float](30*$k))
    $g.FillEllipse($knob, [float]($bx+$bw/2+47*$k), [float]($by-107*$k), [float](30*$k), [float](30*$k))
    $body = RoundRect $bx $by $bw $bh (58*$k)
    $mint = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.PointF $bx, $by), (New-Object System.Drawing.PointF $bx, ($by+$bh)), $mintTop, $mintBottom
    $g.FillPath($mint, $body)
    $g.FillPath((New-Object System.Drawing.SolidBrush $ink), (RoundRect ($bx+34*$k) ($by+34*$k) ($bw-68*$k) ($bh-68*$k) (38*$k)))
    $cx = $bx + $bw/2 + 8*$k; $cy = $by + $bh/2
    $tri = New-Object System.Drawing.Drawing2D.GraphicsPath
    $tri.AddPolygon([System.Drawing.PointF[]]@((New-Object System.Drawing.PointF ($cx-34*$k), ($cy-44*$k)), (New-Object System.Drawing.PointF ($cx+46*$k), $cy), (New-Object System.Drawing.PointF ($cx-34*$k), ($cy+44*$k))))
    $g.FillPath((New-Object System.Drawing.SolidBrush $mintTop), $tri)
    $g.FillPath($mint, (RoundRect ($bx+58*$k) ($by+$bh-6*$k) (56*$k) (30*$k) (12*$k)))
    $g.FillPath($mint, (RoundRect ($bx+$bw-114*$k) ($by+$bh-6*$k) (56*$k) (30*$k) (12*$k)))
}

# Paints the background into $shape (a path) over a $w x $h box at the origin
function Backdrop($g, $shape, $w, $h) {
    $p1 = New-Object System.Drawing.Point 0, 0
    $p2 = New-Object System.Drawing.Point $w, $h
    $g.FillPath((New-Object System.Drawing.Drawing2D.LinearGradientBrush $p1, $p2, $navy, $night), $shape)
    $glow = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glow.AddEllipse((-0.16 * $w), (-0.6 * $h), (0.81 * $w), (1.55 * $h))
    $pg = New-Object System.Drawing.Drawing2D.PathGradientBrush $glow
    $pg.CenterColor = [System.Drawing.Color]::FromArgb(110, $indigo)
    $pg.SurroundColors = @([System.Drawing.Color]::FromArgb(0, $indigo))
    $g.SetClip($shape); $g.FillPath($pg, $glow); $g.ResetClip()
}

function NewCanvas($w, $h) {
    $b = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'; $g.InterpolationMode = 'HighQualityBicubic'
    $g.Clear([System.Drawing.Color]::Transparent)
    return @($b, $g)
}

# The app icon: the mark on the blue gradient, as a rounded square (or a circle for the round icon)
function Icon($size, $round) {
    $c = NewCanvas $size $size; $b = $c[0]; $g = $c[1]
    if ($round) { $shape = New-Object System.Drawing.Drawing2D.GraphicsPath; $shape.AddEllipse(0, 0, $size, $size) }
    else { $shape = RoundRect 0 0 $size $size ($size*0.22) }
    Backdrop $g $shape $size $size
    $k = $size * ($(if ($round) { 0.56 } else { 0.62 })) / 400
    DrawMark $g (($size - 330*$k)/2) (($size - 400*$k)/2 + 6*$k) $k
    $g.Dispose(); return $b
}

# The TV banner: the full logo (mark, padampetti, the Malayalam name) on the blue gradient, 16:9
function Banner($w, $h) {
    $c = NewCanvas 1500 400; $logo = $c[0]; $g = $c[1]
    DrawMark $g 20 0 1
    $font = New-Object System.Drawing.Font ("Segoe UI Black", 176, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $tx = 400; $ty = 70
    $g.DrawString("padam", $font, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)), $tx, $ty, $fmt)
    $pw = $g.MeasureString("padam", $font, 3000, $fmt).Width
    $g.DrawString("petti", $font, (New-Object System.Drawing.SolidBrush $mintTop), ($tx + $pw + 2), $ty, $fmt)
    $wordW = $pw + 2 + $g.MeasureString("petti", $font, 3000, $fmt).Width
    $ml = New-Object System.Drawing.Font ("Nirmala UI", 68, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $malayalam = -join ([char[]](0x0D2A, 0x0D1F, 0x0D02, 0x0D2A, 0x0D46, 0x0D1F, 0x0D4D, 0x0D1F, 0x0D3F))   # padampetti
    $g.DrawString($malayalam, $ml, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230,255,255,255))), ($tx + 6), ($ty + 228), $fmt)
    $g.Dispose()
    $lw0 = [int]($tx + $wordW + 20)
    $c2 = NewCanvas $w $h; $b = $c2[0]; $bg2 = $c2[1]
    $full = New-Object System.Drawing.Drawing2D.GraphicsPath; $full.AddRectangle((New-Object System.Drawing.Rectangle 0, 0, $w, $h))
    Backdrop $bg2 $full $w $h
    $s = [Math]::Min($w*0.78/$lw0, $h*0.5/400)
    $dest = New-Object System.Drawing.RectangleF ([float](($w-$lw0*$s)/2)), ([float](($h-400*$s)/2)), ([float]($lw0*$s)), ([float](400*$s))
    $srcRect = New-Object System.Drawing.RectangleF 0, 0, $lw0, 400
    $bg2.DrawImage($logo, $dest, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $bg2.Dispose(); $logo.Dispose(); return $b
}

function SavePng($bmp, $path) { $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose(); "  $path" }

$res = Join-Path $app "src\androidMain\res"
foreach ($d in @(@("mdpi",48), @("hdpi",72), @("xhdpi",96), @("xxhdpi",144), @("xxxhdpi",192))) {
    SavePng (Icon $d[1] $false) (Join-Path $res "mipmap-$($d[0])\ic_launcher.png")
    SavePng (Icon $d[1] $true) (Join-Path $res "mipmap-$($d[0])\ic_launcher_round.png")
}
SavePng (Banner 640 360) (Join-Path $res "drawable\tv_banner.png")
SavePng (Banner 320 180) (Join-Path $res "drawable-xhdpi\tv_banner.png")
SavePng (Icon 512 $false) (Join-Path $app "src\commonMain\composeResources\drawable\app_logo.png")
SavePng (Icon 1024 $false) (Join-Path $app "icons\Padampetti-1024.png")

# Windows .ico: PNG-compressed entries, 16 to 256 px
$sizes = 16, 24, 32, 48, 64, 128, 256
$pngs = foreach ($s in $sizes) { $ms = New-Object System.IO.MemoryStream; $i = Icon $s $false; $i.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png); $i.Dispose(); ,$ms.ToArray() }
$ico = New-Object System.IO.MemoryStream; $w = New-Object System.IO.BinaryWriter $ico
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]; $len = $pngs[$i].Length
    $w.Write([byte]($s % 256)); $w.Write([byte]($s % 256)); $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([UInt16]1); $w.Write([UInt16]32); $w.Write([UInt32]$len); $w.Write([UInt32]$offset); $offset += $len
}
foreach ($p in $pngs) { $w.Write($p) }
$w.Flush(); [System.IO.File]::WriteAllBytes((Join-Path $app "icons\Padampetti.ico"), $ico.ToArray()); "  icons\Padampetti.ico"
