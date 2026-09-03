# One command to bring up the full VoltStack demo in Docker.
# Usage:  .\demo-up.ps1        (build + start + wait healthy + print URLs)
#         .\demo-up.ps1 -Down  (stop everything, keep data)
param([switch]$Down)

Set-Location $PSScriptRoot

if ($Down) {
    docker compose -f docker-compose.dev.yml down
    Write-Host "`n=== Stack stopped. Data kept (named volumes). ===" -ForegroundColor Green
    exit 0
}

Write-Host "=== Building + starting full stack (first run takes a while) ===" -ForegroundColor Cyan
docker compose -f docker-compose.dev.yml up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Wait until every service is healthy (or timeout after ~5 min).
$names = docker compose -f docker-compose.dev.yml ps --services
$deadline = (Get-Date).AddMinutes(6)
while ((Get-Date) -lt $deadline) {
    $unhealthy = docker compose -f docker-compose.dev.yml ps --services --filter "status=running" |
        ForEach-Object { $s = $_; docker inspect -f "{{if ne .State.Health.Status \"healthy\"}}{{.Name}}{{end}}" $s 2>$null } |
        Where-Object { $_ } |
        Where-Object { $_ -notmatch "eureka-server-2" }
    if (-not $unhealthy) {
        $out = docker compose -f docker-compose.dev.yml ps --format "table {{.Name}}\t{{.Status}}"
        Write-Host "`n$out" -ForegroundColor Green
        Write-Host "`n=== STACK UP ===" -ForegroundColor Green
        Write-Host "  Gateway:   http://localhost:8080"
        Write-Host "  Eureka:    http://localhost:8761"
        Write-Host "  Config:    http://localhost:8888"
        Write-Host "  Products:  http://localhost:8081"
        Write-Host "  Identity:  http://localhost:8082"
        Write-Host "  Order:     http://localhost:8083"
        Write-Host "  Payment:   http://localhost:8084"
        Write-Host "  Notif:     http://localhost:8085"
        Write-Host "  Stop with: .\demo-up.ps1 -Down"
        exit 0
    }
    Start-Sleep -Seconds 5
}
Write-Host "`n=== TIMEOUT: some services not healthy. Check logs: ===" -ForegroundColor Yellow
docker compose -f docker-compose.dev.yml ps
exit 1
