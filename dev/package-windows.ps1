param(
    [string]$Version = "0.2.0",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")
$CleanVersion = $Version.TrimStart("v")
if ($CleanVersion -notmatch "^\d+\.\d+\.\d+$") {
    $CleanVersion = "0.2.0"
}

$WixSha256 = "2c1888d5d1dba377fc7fa14444cf556963747ff9a0a289a3599cf09da03b9e2e"

$BuildDir = Join-Path $Repo "build"
$ToolsDir = Join-Path $BuildDir "tools"
$PackageDir = Join-Path $BuildDir "windows-package"
$InputDir = Join-Path $PackageDir "input"
$ReleaseDir = Join-Path $BuildDir "release"
$IconPath = Join-Path $Repo "desktop\src\main\resources\icon.ico"

function Reset-Directory($Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

function Get-CheckedFile($Uri, $Path, $Sha256) {
    if (!(Test-Path $Path)) {
        Invoke-WebRequest -Uri $Uri -OutFile $Path
    }

    $Hash = (Get-FileHash -Algorithm SHA256 $Path).Hash.ToLowerInvariant()
    if ($Hash -ne $Sha256) {
        Remove-Item -LiteralPath $Path -Force
        Invoke-WebRequest -Uri $Uri -OutFile $Path
        $Hash = (Get-FileHash -Algorithm SHA256 $Path).Hash.ToLowerInvariant()
    }
    if ($Hash -ne $Sha256) {
        throw "checksum mismatch: $Path"
    }
}

function Ensure-Wix {
    if ((Get-Command candle.exe -ErrorAction SilentlyContinue) -and
        (Get-Command light.exe -ErrorAction SilentlyContinue)) {
        return
    }

    $WixZip = Join-Path $ToolsDir "wix311-binaries.zip"
    $WixDir = Join-Path $ToolsDir "wix311"
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    Get-CheckedFile `
        "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip" `
        $WixZip `
        $WixSha256

    Reset-Directory $WixDir
    Expand-Archive -Path $WixZip -DestinationPath $WixDir -Force
    $env:PATH = "$WixDir;$env:PATH"
}

if (!$SkipBuild) {
    & (Join-Path $Repo "gradlew.bat") ":core:test" ":desktop:test" ":desktop:installDist"
}

Ensure-Wix

Reset-Directory $InputDir
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

$LibDir = Join-Path $Repo "desktop\build\install\desktop\lib"
if (!(Test-Path (Join-Path $LibDir "desktop.jar"))) {
    throw "desktop installDist not found"
}

Copy-Item (Join-Path $LibDir "*") $InputDir -Recurse -Force

& (Join-Path $PSScriptRoot "ensure-sing-box.ps1") -Destination (Join-Path $InputDir "sing-box.exe") | Out-Null

$TempOutput = Join-Path $PackageDir "output"
Reset-Directory $TempOutput

& jpackage `
    --type exe `
    --name Beacon `
    --app-version $CleanVersion `
    --vendor Beacon `
    --description "VLESS Reality client" `
    --input $InputDir `
    --main-jar desktop.jar `
    --main-class app.beacon.desktop.BeaconDesktopKt `
    --icon $IconPath `
    --dest $TempOutput `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --win-per-user-install `
    --win-upgrade-uuid "6B9A3E90-9B7E-4D1D-9C32-5E3F3A6E4F51" `
    --java-options "-Dfile.encoding=UTF-8"

$Installer = Get-ChildItem $TempOutput -Filter "*.exe" | Select-Object -First 1
if ($null -eq $Installer) {
    throw "installer exe not found"
}

$Target = Join-Path $ReleaseDir "Beacon-Windows-v$CleanVersion.exe"
Copy-Item $Installer.FullName $Target -Force
Write-Output $Target
