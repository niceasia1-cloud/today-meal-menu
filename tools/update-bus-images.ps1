$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$sourceDir = Join-Path $root "source-data"
$resDir = Join-Path $root "app\src\main\res\drawable-nodpi"

New-Item -ItemType Directory -Force -Path $sourceDir | Out-Null
New-Item -ItemType Directory -Force -Path $resDir | Out-Null

$files = @(
    @{ Url = "http://www.shlu.or.kr/bus/2025/2025-map.jpg"; Source = "boarding-map.jpg"; Resource = "boarding_map.jpg" },
    @{ Url = "http://www.shlu.or.kr/bus/2026/26-01.jpg"; Source = "weekday-go.jpg"; Resource = "weekday_go.jpg" },
    @{ Url = "http://www.shlu.or.kr/bus/2026/26-02.jpg"; Source = "weekday-return.jpg"; Resource = "weekday_return.jpg" },
    @{ Url = "http://www.shlu.or.kr/bus/2026/26-03.jpg"; Source = "saturday-go.jpg"; Resource = "saturday_go.jpg" },
    @{ Url = "http://www.shlu.or.kr/bus/2026/26-04.jpg"; Source = "saturday-return.jpg"; Resource = "saturday_return.jpg" },
    @{ Url = "http://www.shlu.or.kr/bus/2026/26-05.jpg"; Source = "sunday.jpg"; Resource = "sunday.jpg" }
)

foreach ($file in $files) {
    $sourcePath = Join-Path $sourceDir $file.Source
    $resourcePath = Join-Path $resDir $file.Resource
    Invoke-WebRequest -Uri $file.Url -OutFile $sourcePath
    Copy-Item -LiteralPath $sourcePath -Destination $resourcePath -Force
    Write-Host "Updated $($file.Source)"
}
