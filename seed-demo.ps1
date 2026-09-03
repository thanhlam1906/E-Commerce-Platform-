# Nạp dữ liệu demo (categories + products + ton kho) vao stack dev.
# Usage:  .\seed-demo.ps1
#
# Yeu cau: cac container DB dang chay (mongodb + order-postgres) -
#          neu chua co thi chay .\demo-up.ps1 truoc.
# Idempotent: upsert theo _id/sku, KHONG xoa du lieu khac trong DB.
# ASCII-only de tranh loi encoding khi chay PowerShell 5.1.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$data = Join-Path $PSScriptRoot 'scripts\demo\data'

function Is-Running([string]$name) {
    return (docker inspect -f '{{.State.Running}}' $name 2>$null) -eq 'true'
}

Write-Host '=== Seed demo data (VoltStack) ===' -ForegroundColor Cyan

# 1) Guard: DB containers dang chay?
if (-not (Is-Running 'mongodb') -or -not (Is-Running 'order-postgres')) {
    Write-Host 'Yeu cau container "mongodb" va "order-postgres" dang chay.' -ForegroundColor Yellow
    Write-Host 'Chay .\demo-up.ps1 de dung toan bo stack truoc, roi chay lai script nay.' -ForegroundColor Yellow
    exit 1
}

# 2) Guard: data da sinh chua? (node scripts/demo/generate.mjs de tao lai)
foreach ($f in 'seed-mongo.js', 'stock.sql') {
    if (-not (Test-Path (Join-Path $data $f))) {
        Write-Host "Thieu file du lieu: $f" -ForegroundColor Yellow
        Write-Host 'Chay:  node scripts/demo/generate.mjs' -ForegroundColor Yellow
        exit 1
    }
}

# 3) Nap categories + products vao MongoDB (product-catalog)
Write-Host '=> MongoDB: categories + products ...' -ForegroundColor Cyan
docker cp (Join-Path $data 'seed-mongo.js') mongodb:/tmp/seed-mongo.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker exec mongodb mongosh product-catalog --quiet --file /tmp/seed-mongo.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker exec mongodb rm -f /tmp/seed-mongo.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 4) Nap ton kho vao Postgres order (upsert theo sku)
Write-Host '=> order-postgres: ton kho (inventory) ...' -ForegroundColor Cyan
cmd /c "docker exec -i order-postgres psql -U postgres -d orders -v ON_ERROR_STOP=1 < `"$data\stock.sql`""
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 5) Tom tat
$cats = (docker exec mongodb mongosh product-catalog --quiet --eval 'db.categories.countDocuments()').Trim()
$prods = (docker exec mongodb mongosh product-catalog --quiet --eval 'db.products.countDocuments()').Trim()
$inv  = (docker exec order-postgres psql -U postgres -d orders -tAc 'SELECT count(*) FROM inventory').Trim()
$txns = (docker exec order-postgres psql -U postgres -d orders -tAc "SELECT count(*) FROM inventory_transactions WHERE reference='demo-seed'").Trim()

Write-Host "`n=== DONE ===" -ForegroundColor Green
Write-Host "  Categories: $cats"
Write-Host "  Products:   $prods"
Write-Host "  Inventory rows: $inv   (txn demo-seed: $txns)"
Write-Host "`n  Xem storefront: http://localhost:8080"
