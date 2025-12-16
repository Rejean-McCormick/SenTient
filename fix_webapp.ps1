# ==============================================================================
# SenTient Webapp Repair Tool
# ==============================================================================
$ErrorActionPreference = "Stop"
$TargetDir = Join-Path (Get-Location) "webapp"
$ZipUrl = "https://github.com/OpenRefine/OpenRefine/releases/download/3.7.3/openrefine-win-3.7.3.zip"
$ZipFile = "openrefine_kit.zip"
$ExtractPath = "temp_kit"

Write-Host "Downloading Core UI Assets..." -ForegroundColor Cyan
Invoke-WebRequest -Uri $ZipUrl -OutFile $ZipFile -MaximumRedirection 5

Write-Host "Extracting..." -ForegroundColor Cyan
Expand-Archive -Path $ZipFile -DestinationPath $ExtractPath -Force

Write-Host "Installing missing files..." -ForegroundColor Cyan
$SourceWebapp = Join-Path $ExtractPath "openrefine-3.7.3\webapp"
# Copy Forcefully to fill in the gaps (index.html, web.xml)
Copy-Item -Path "$SourceWebapp\*" -Destination $TargetDir -Recurse -Force

Write-Host "Cleanup..." -ForegroundColor Cyan
Remove-Item $ZipFile -Force
Remove-Item $ExtractPath -Recurse -Force

Write-Host "REPAIR COMPLETE. Try running refine.bat now." -ForegroundColor Green