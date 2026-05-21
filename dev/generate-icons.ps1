$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")
$DesktopRes = Join-Path $Repo "desktop\src\main\resources"
New-Item -ItemType Directory -Force -Path $DesktopRes | Out-Null

Add-Type -AssemblyName System.Drawing

$Size = 512
$PngPath = Join-Path $DesktopRes "icon.png"
$IcoPath = Join-Path $DesktopRes "icon.ico"

$Bitmap = New-Object System.Drawing.Bitmap $Size, $Size
$Graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
$Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$Graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$Graphics.Clear([System.Drawing.Color]::FromArgb(247, 201, 72))

$Blue = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(31, 97, 180))
$BeamBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(58, 255, 255, 255))
$Dark = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(29, 37, 51))
$White = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$GoldDark = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(219, 160, 30))

$Beam = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(296, 160),
    [System.Drawing.Point]::new(426, 112),
    [System.Drawing.Point]::new(426, 226)
)
$Graphics.FillPolygon($BeamBrush, $Beam)

$Roof = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(184, 150),
    [System.Drawing.Point]::new(328, 150),
    [System.Drawing.Point]::new(294, 104),
    [System.Drawing.Point]::new(218, 104)
)
$Graphics.FillPolygon($Dark, $Roof)
$Graphics.FillRectangle($GoldDark, 212, 150, 88, 20)
$Graphics.FillEllipse($White, 291, 155, 12, 12)

$Tower = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(190, 398),
    [System.Drawing.Point]::new(322, 398),
    [System.Drawing.Point]::new(296, 170),
    [System.Drawing.Point]::new(216, 170)
)
$Graphics.FillPolygon($Dark, $Tower)
$Graphics.FillRectangle($White, 232, 218, 48, 54)
$Graphics.FillRectangle($Dark, 158, 398, 196, 42)

$Wave1 = [System.Drawing.Drawing2D.GraphicsPath]::new()
$Wave1.StartFigure()
$Wave1.AddBezier(112, 450, 158, 428, 198, 472, 248, 450)
$Wave1.AddBezier(248, 450, 300, 428, 348, 472, 400, 450)
$Wave1.AddLine(400, 512, 112, 512)
$Wave1.CloseFigure()
$Graphics.FillPath($Blue, $Wave1)

$Bitmap.Save($PngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$Graphics.Dispose()
$Bitmap.Dispose()

$PngBytes = [System.IO.File]::ReadAllBytes($PngPath)
$Ico = New-Object System.IO.MemoryStream
$Writer = New-Object System.IO.BinaryWriter($Ico)
$Writer.Write([UInt16]0)
$Writer.Write([UInt16]1)
$Writer.Write([UInt16]1)
$Writer.Write([Byte]0)
$Writer.Write([Byte]0)
$Writer.Write([Byte]0)
$Writer.Write([Byte]0)
$Writer.Write([UInt16]1)
$Writer.Write([UInt16]32)
$Writer.Write([UInt32]$PngBytes.Length)
$Writer.Write([UInt32]22)
$Writer.Write($PngBytes)
$Writer.Flush()
[System.IO.File]::WriteAllBytes($IcoPath, $Ico.ToArray())
$Writer.Dispose()
$Ico.Dispose()

Write-Output $PngPath
Write-Output $IcoPath
