$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$imageDir = Join-Path $root "source-data\menu-2026-06"
$cropDir = Join-Path $imageDir "crops"
$outJson = Join-Path $root "app\src\main\assets\meal_2026_06.json"
$tesseract = "C:\Program Files\Tesseract-OCR\tesseract.exe"
$tessdata = Join-Path $root "tools\tessdata"

New-Item -ItemType Directory -Force -Path $cropDir | Out-Null
Add-Type -AssemblyName System.Drawing

$weeks = @(
    @{ file = "page-1.jpg"; startDay = 1 },
    @{ file = "page-2.jpg"; startDay = 8 },
    @{ file = "page-3.jpg"; startDay = 15 },
    @{ file = "page-4.jpg"; startDay = 22 },
    @{ file = "page-5.jpg"; startDay = 29 }
)

$mealBands = @(
    @{ key = "breakfast"; label = "breakfast"; y = 138; h = 220 },
    @{ key = "lunch"; label = "lunch"; y = 358; h = 340 },
    @{ key = "dinner"; label = "dinner"; y = 698; h = 292 }
)

function Get-Weekday([int]$day) {
    $date = Get-Date -Year 2026 -Month 6 -Day $day
    return @("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[[int]$date.DayOfWeek]
}

function Crop-Image($srcPath, $destPath, [int]$x, [int]$y, [int]$w, [int]$h) {
    $src = [System.Drawing.Image]::FromFile($srcPath)
    try {
        $scale = 4
        $crop = New-Object System.Drawing.Bitmap $w, $h
        $g = [System.Drawing.Graphics]::FromImage($crop)
        $rect = New-Object System.Drawing.Rectangle $x, $y, $w, $h
        $g.DrawImage($src, 0, 0, $rect, [System.Drawing.GraphicsUnit]::Pixel)
        $g.Dispose()

        $up = New-Object System.Drawing.Bitmap ($w * $scale), ($h * $scale)
        $g2 = [System.Drawing.Graphics]::FromImage($up)
        $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g2.Clear([System.Drawing.Color]::White)
        $g2.DrawImage($crop, 0, 0, $up.Width, $up.Height)
        $g2.Dispose()
        $crop.Dispose()

        $up.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $up.Dispose()
    }
    finally {
        $src.Dispose()
    }
}

function Invoke-Ocr($path) {
    $tempBase = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), [System.IO.Path]::GetRandomFileName())
    & $tesseract $path $tempBase --tessdata-dir $tessdata -l kor+eng --psm 6 2>$null | Out-Null
    $txtPath = $tempBase + ".txt"
    try {
        return ([System.IO.File]::ReadAllText($txtPath, [System.Text.Encoding]::UTF8) -replace "`r", "").Trim()
    }
    finally {
        if (Test-Path $txtPath) {
            Remove-Item $txtPath -Force
        }
    }
}

function Clean-Lines($text) {
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($text -split "`n")) {
        $clean = ($line -replace "\s+", " ").Trim()
        if ($clean.Length -lt 2) { continue }
        if ($clean.ToLower().Contains("weekly")) { continue }
        if ($clean.ToLower().Contains("menu")) { continue }
        if ([regex]::Matches($clean, "\p{IsHangulSyllables}").Count -lt 2) { continue }
        if (-not $lines.Contains($clean)) {
            $lines.Add($clean)
        }
    }
    return $lines
}

$days = New-Object System.Collections.Generic.List[object]
$tableLeft = 73
$tableRight = 766
$colWidth = [math]::Floor(($tableRight - $tableLeft) / 7)

foreach ($week in $weeks) {
    $srcPath = Join-Path $imageDir $week.file
    for ($col = 0; $col -lt 7; $col++) {
        $day = [int]$week.startDay + $col
        if ($day -gt 30) { continue }

        $dayObject = [ordered]@{
            date = ("2026-06-{0:D2}" -f $day)
            weekday = Get-Weekday $day
            sourceImage = "menu-2026-06/" + $week.file
            meals = [ordered]@{}
        }

        foreach ($meal in $mealBands) {
            $x = $tableLeft + ($col * $colWidth)
            $cropPath = Join-Path $cropDir ("2026-06-{0:D2}-{1}.png" -f $day, $meal.key)
            Crop-Image $srcPath $cropPath $x $meal.y $colWidth $meal.h
            $raw = Invoke-Ocr $cropPath
            $items = Clean-Lines $raw
            $dayObject.meals[$meal.key] = [ordered]@{
                label = $meal.label
                rawText = $raw
                items = @($items)
                crop = ("menu-2026-06/crops/" + [IO.Path]::GetFileName($cropPath))
            }
        }
        $days.Add($dayObject)
    }
}

$json = $days | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($outJson, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $outJson"
