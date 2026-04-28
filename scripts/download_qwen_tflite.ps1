param(
    [Parameter(Mandatory = $true)]
    [string]$ModelUrl,

    [string]$OutputPath = "app/src/main/assets/qwen35_mm_q8_ekv2048.tflite"
)

$ErrorActionPreference = "Stop"

$target = Resolve-Path "." | ForEach-Object { Join-Path $_ $OutputPath }
$targetDir = Split-Path -Parent $target

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

Write-Host "Downloading model from: $ModelUrl"
Invoke-WebRequest -Uri $ModelUrl -OutFile $target

$size = (Get-Item $target).Length
if ($size -lt 10485760) {
    throw "Downloaded file is too small ($size bytes). It may be an HTML error page instead of a .tflite model."
}

Write-Host "Model downloaded successfully: $target ($size bytes)"
