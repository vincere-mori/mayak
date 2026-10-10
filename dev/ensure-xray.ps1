param([string]$Destination)
$ErrorActionPreference = 'Stop'
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$Destination) { $Destination = Join-Path $Repo 'xray.exe' }
$Version = '26.9.30'
$Expected = 'b17a619343c11b89d8faf36278298856749b15c52277d875d32051b33dcda617'
$Dir = Join-Path $Repo 'build\tools\xray-windows'
$Zip = Join-Path $Repo 'build\tools\Xray-windows-64.zip'
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
if (!(Test-Path -LiteralPath $Zip)) {
    & curl.exe --fail --location --retry 2 --output $Zip "https://github.com/XTLS/Xray-core/releases/download/v$Version/Xray-windows-64.zip"
    if ($LASTEXITCODE -ne 0) { throw 'Xray download failed' }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Zip).Hash.ToLowerInvariant() -ne $Expected) { throw 'Xray checksum mismatch' }
Expand-Archive -LiteralPath $Zip -DestinationPath $Dir -Force
New-Item -ItemType Directory -Force -Path (Split-Path $Destination) | Out-Null
Copy-Item -LiteralPath (Join-Path $Dir 'xray.exe') -Destination $Destination -Force
