param(
    [string]$Destination
)

$ErrorActionPreference = "Stop"

$Repo = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $Repo "sing-box.exe"
}

$SingBoxVersion = "1.13.11"
$SingBoxSha256 = "30ecceaebb659195aa67d0a9a398c75c42fb263e079f5499a5f1dcecfa138507"
$ToolsDir = Join-Path $Repo "build\tools"
$ZipPath = Join-Path $ToolsDir "sing-box-$SingBoxVersion-windows-amd64.zip"
$ExtractDir = Join-Path $ToolsDir "sing-box-$SingBoxVersion-windows-amd64"
$ExePath = Join-Path $ExtractDir "sing-box-$SingBoxVersion-windows-amd64\sing-box.exe"

function Get-CheckedFile($Uri, $Path, $Sha256) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
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

Get-CheckedFile `
    "https://github.com/SagerNet/sing-box/releases/download/v$SingBoxVersion/sing-box-$SingBoxVersion-windows-amd64.zip" `
    $ZipPath `
    $SingBoxSha256

if (!(Test-Path $ExePath)) {
    $ResolvedTools = [System.IO.Path]::GetFullPath($ToolsDir)
    $ResolvedExtract = [System.IO.Path]::GetFullPath($ExtractDir)
    if (!$ResolvedExtract.StartsWith($ResolvedTools, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "unsafe extract path: $ExtractDir"
    }
    if (Test-Path $ExtractDir) {
        Remove-Item -LiteralPath $ExtractDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null
    Expand-Archive -Path $ZipPath -DestinationPath $ExtractDir -Force
}

$DestinationParent = Split-Path $Destination
if (![string]::IsNullOrWhiteSpace($DestinationParent)) {
    New-Item -ItemType Directory -Force -Path $DestinationParent | Out-Null
}
Copy-Item $ExePath $Destination -Force
Write-Output $Destination
