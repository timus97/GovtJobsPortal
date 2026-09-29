# Build (if needed) and start the Spring Boot product host on Temurin 21.
# Usage:  .\scripts\start-web.ps1
#         .\scripts\start-web.ps1 -Port 8090
#         .\scripts\start-web.ps1 -SkipBuild
param(
    [int]$Port = 0,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "java21.ps1")

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $RepoRoot

Write-Host "Starting student Postgres (docker compose student-db)"
docker compose up -d student-db
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed. Slice 4 needs Postgres 16 (see docker-compose.yml)."
}
$env:STUDENT_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/govtjobs_students"
$env:STUDENT_DATABASE_USER = "govtjobs"
$env:STUDENT_DATABASE_PASSWORD = "govtjobs"
if (-not $env:SESSION_SECRET) {
    $secretDir = Join-Path $RepoRoot ".local"
    $secretFile = Join-Path $secretDir "session-secret"
    if (Test-Path -LiteralPath $secretFile) {
        $env:SESSION_SECRET = (Get-Content -LiteralPath $secretFile -Raw).Trim()
    }
    if (-not $env:SESSION_SECRET) {
        New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        $env:SESSION_SECRET = ([Convert]::ToHexString($bytes)).ToLowerInvariant()
        Set-Content -LiteralPath $secretFile -Value $env:SESSION_SECRET -NoNewline
    }
}
if (-not $env:PUBLIC_SITE_URL) {
    $env:PUBLIC_SITE_URL = "http://localhost:8090"
}

if ($Port -le 0) {
    if ($env:PORT -and $env:PORT -match '^\d+$') {
        $Port = [int]$env:PORT
    } else {
        $Port = 8090
    }
}
$env:PORT = [string]$Port

$Jar = Join-Path $RepoRoot "web\target\web-1.0.0-SNAPSHOT.jar"
$Mvnw = Join-Path $RepoRoot "mvnw.cmd"
if (-not $SkipBuild -or -not (Test-Path -LiteralPath $Jar)) {
    Write-Host "Packaging web module with $env:JAVA_HOME"
    & $Mvnw -pl web -am package "-DskipTests"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$JavaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
Write-Host "Starting $Jar on http://localhost:$Port"
& $JavaExe -jar $Jar
