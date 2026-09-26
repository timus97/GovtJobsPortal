# Load Temurin 21 from project steering into this PowerShell session.
# Usage:  . .\scripts\java21.ps1
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Steering = Join-Path $RepoRoot ".grok\java-home.txt"
if (-not (Test-Path -LiteralPath $Steering)) {
    throw "Missing JDK steering file: $Steering"
}

$JavaHome = Get-Content -LiteralPath $Steering | ForEach-Object { $_.Trim() } | Where-Object {
    $_ -and -not $_.StartsWith("#")
} | Select-Object -First 1

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw "JDK steering file is empty: $Steering"
}

$JavaExe = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $JavaExe)) {
    throw "Steering JAVA_HOME is not a JDK (java.exe missing): $JavaHome"
}

$env:JAVA_HOME = $JavaHome
$jreBin = Join-Path $JavaHome "bin"
$env:Path = "$jreBin;" + (($env:Path -split ';' | Where-Object { $_ -and ($_ -ne $jreBin) }) -join ';')

Write-Host "JAVA_HOME=$env:JAVA_HOME"
& $JavaExe -version
