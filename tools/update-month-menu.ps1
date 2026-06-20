$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sourceBase = "http://www.shlu.or.kr"
$listUrl = "$sourceBase/2015/MonthMenu"
$assetDir = Join-Path $root "app\src\main\assets"
$tesseract = "C:\Program Files\Tesseract-OCR\tesseract.exe"
$tessdata = Join-Path $root "tools\tessdata"

Add-Type -AssemblyName System.Drawing

function Get-Text($url) {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing
    return $response.Content
}

function Get-LatestMenuPost($html) {
    $match = [regex]::Match($html, '<a href="(?<href>/2015/MonthMenu/\d+)" class="subject">(?<title>[^<]+)</a>')
    if (-not $match.Success) {
        throw "Could not find latest MonthMenu post."
    }
    $title = [System.Net.WebUtility]::HtmlDecode($match.Groups["title"].Value)
    $yearMatch = [regex]::Match($title, '(20\d{2})')
    $titleWithoutYear = $title -replace '(20\d{2})', ''
    $monthMatch = [regex]::Match($titleWithoutYear, '(\d{1,2})')
    if (-not $yearMatch.Success -or -not $monthMatch.Success) {
        throw "Could not parse year/month from title: $title"
    }
    return @{
        title = $title
        url = $sourceBase + $match.Groups["href"].Value
        year = [int]$yearMatch.Groups[1].Value
        month = [int]$monthMatch.Groups[1].Value
    }
}

function Get-ImageUrls($html) {
    $urls = New-Object System.Collections.Generic.List[string]
    $matches = [regex]::Matches($html, '<img\s+src="(?<src>[^"]+)"')
    foreach ($match in $matches) {
        $src = [System.Net.WebUtility]::HtmlDecode($match.Groups["src"].Value)
        if ($src -like "*files/attach/images*" -and ($src -like "*.jpg" -or $src -like "*.jpeg" -or $src -like "*.png")) {
            if ($src.StartsWith("/")) {
                $src = $sourceBase + $src
            }
            if (-not $urls.Contains($src)) {
                $urls.Add($src)
            }
        }
    }
    return $urls
}

function Get-MondayOnOrBefore([datetime]$date) {
    $offset = ([int]$date.DayOfWeek + 6) % 7
    return $date.AddDays(-$offset)
}

function Get-WeekdayLabel([datetime]$date) {
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

function Ensure-OcrReady() {
    if (-not (Test-Path $tesseract)) {
        throw "Tesseract is not installed at $tesseract"
    }
    if (-not (Test-Path (Join-Path $tessdata "kor.traineddata"))) {
        throw "Korean tessdata is missing at $tessdata"
    }
}

Ensure-OcrReady

$listHtml = Get-Text $listUrl
$post = Get-LatestMenuPost $listHtml
$monthKey = "{0:D4}-{1:D2}" -f $post.year, $post.month
$monthDirName = "menu-" + $monthKey
$monthDir = Join-Path (Join-Path $root "source-data") $monthDirName
$cropDir = Join-Path $monthDir "crops"
New-Item -ItemType Directory -Force -Path $monthDir | Out-Null
New-Item -ItemType Directory -Force -Path $cropDir | Out-Null

$postHtml = Get-Text $post.url
$imageUrls = Get-ImageUrls $postHtml
if ($imageUrls.Count -lt 1) {
    throw "No menu images found in $($post.url)"
}

$weeklyImages = @($imageUrls | Select-Object -First 5)
for ($i = 0; $i -lt $weeklyImages.Count; $i++) {
    $outFile = Join-Path $monthDir ("page-{0}.jpg" -f ($i + 1))
    Invoke-WebRequest -Uri $weeklyImages[$i] -OutFile $outFile
}

$mealBands = @(
    @{ key = "breakfast"; label = "breakfast"; y = 138; h = 220 },
    @{ key = "lunch"; label = "lunch"; y = 358; h = 340 },
    @{ key = "dinner"; label = "dinner"; y = 698; h = 292 }
)

$tableLeft = 73
$tableRight = 766
$colWidth = [math]::Floor(($tableRight - $tableLeft) / 7)
$firstDay = Get-Date -Year $post.year -Month $post.month -Day 1
$firstWeekMonday = Get-MondayOnOrBefore $firstDay
$daysInMonth = [DateTime]::DaysInMonth($post.year, $post.month)
$days = New-Object System.Collections.Generic.List[object]

for ($weekIndex = 0; $weekIndex -lt $weeklyImages.Count; $weekIndex++) {
    $srcPath = Join-Path $monthDir ("page-{0}.jpg" -f ($weekIndex + 1))
    $weekStart = $firstWeekMonday.AddDays($weekIndex * 7)
    for ($col = 0; $col -lt 7; $col++) {
        $date = $weekStart.AddDays($col)
        if ($date.Month -ne $post.month -or $date.Year -ne $post.year) { continue }
        if ($date.Day -lt 1 -or $date.Day -gt $daysInMonth) { continue }

        $dateKey = $date.ToString("yyyy-MM-dd")
        $dayObject = [ordered]@{
            date = $dateKey
            weekday = Get-WeekdayLabel $date
            sourceImage = "$monthDirName/page-$($weekIndex + 1).jpg"
            meals = [ordered]@{}
        }

        foreach ($meal in $mealBands) {
            $x = $tableLeft + ($col * $colWidth)
            $cropPath = Join-Path $cropDir ("$dateKey-$($meal.key).png")
            Crop-Image $srcPath $cropPath $x $meal.y $colWidth $meal.h
            $raw = Invoke-Ocr $cropPath
            $items = Clean-Lines $raw
            $dayObject.meals[$meal.key] = [ordered]@{
                label = $meal.label
                rawText = $raw
                items = @($items)
                crop = "$monthDirName/crops/" + [IO.Path]::GetFileName($cropPath)
            }
        }
        $days.Add($dayObject)
    }
}

$monthJsonName = "meal_{0:D4}_{1:D2}.json" -f $post.year, $post.month
$monthJson = Join-Path $assetDir $monthJsonName
$currentJson = Join-Path $assetDir "meal_current.json"
$metadataJson = Join-Path $assetDir "meal_metadata.json"
$json = $days | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($monthJson, $json, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($currentJson, $json, [System.Text.UTF8Encoding]::new($false))

$metadata = [ordered]@{
    title = $post.title
    sourceUrl = $post.url
    year = $post.year
    month = $post.month
    monthKey = $monthKey
    updatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    days = $days.Count
    generatedFiles = @($monthJsonName, "meal_current.json")
    imageCount = $weeklyImages.Count
}
[System.IO.File]::WriteAllText($metadataJson, ($metadata | ConvertTo-Json -Depth 4), [System.Text.UTF8Encoding]::new($false))

Write-Host "Updated $monthKey from $($post.url)"
Write-Host "Wrote $monthJson"
Write-Host "Wrote $currentJson"
