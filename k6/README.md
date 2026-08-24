# Load test: Create order (checkout)

Load test cho luồng **create order (checkout)**: add item vào cart → checkout, đi qua API Gateway `localhost:8080`. Mục tiêu đối chiếu **exit criteria Phase 2**: **~100 req/s, p95 < 500ms, 0 lỗi nghiệp vụ, không oversell (bán quá tồn kho)**.

Script chính: [`order-checkout.js`](order-checkout.js) — chạy kịch bản `ramping-arrival-rate` (nhắm tới `TARGET_RPS` iteration/s, 1 iteration = 1 checkout).

## Prerequisites

### 1. Stack đang chạy (đầy đủ)

Mọi service phải UP trước khi chạy. Kiểm tra nhanh: `curl http://localhost:8080/health` (gateway trả danh sách service UP/DOWN theo Eureka registry).

| Thành phần | Port | Ghi chú |
|-----------|------|---------|
| Infra docker compose (Kafka, Redis, Postgres, Mongo) | — | `docker compose up -d` ở root |
| Eureka | 8761 / 8762 | peer; nhớ `eureka.instance.hostname=localhost` (Windows/Hyper-V) |
| Config Server | 8888 | optional |
| ApiGateway | 8080 | điểm vào duy nhất — KHÔNG gọi thẳng cổng service (thiếu `X-User-Roles` → 401) |
| Identity | 8082 | register/login + JWKS |
| Product-Catalog | 8081 | product + variant (SKU) |
| Order (cart + order + inventory) | 8083 | Redis cart, checkout reserve atomic |
| Payment | — | chỉ cần khi dùng `PAYMENT_METHOD=VNPAY_QR` |
| Notification | — | không ảnh hưởng checkout |

### 2. k6

Kiểm tra: `k6 version`. Nếu chưa có, cài (Windows):

```powershell
winget install k6 --source winget
# hoặc choco install k6
# hoặc tải bản release từ https://github.com/grafana/k6/releases
```

### 3. Data seed (quan trọng)

Script giả định **SKU đã tồn tại trong Product-Catalog** (product + variant có `sku`). Cách tạo product qua Postman collection `postman/ecommerce.postman_collection.json` → folder *2. Products & Categories → Create product* (cần role `PRODUCT_ADMIN`). Dùng SKU riêng cho mỗi lần load test (xem phần Stock).

**Stock**: `setup()` chỉ seed stock khi có `ADMIN_TOKEN` (role `ORDER_ADMIN`/`SUPER_ADMIN` — gán trong DB, vì register luôn tạo CUSTOMER):

```bash
# 1. gán role trong DB cho 1 user test (thủ công, 1 lần)
# 2. login lấy token -> export ADMIN_TOKEN
k6 run order-checkout.js -e ADMIN_TOKEN=eyJ... -e SKU=SKU-001 -e STOCK_QTY=5000
```

Nếu không truyền `ADMIN_TOKEN`, script **bỏ qua seed** và giả định SKU đã có đủ stock — bạn tự seed thủ công trước.

> ⚠️ Endpoint `POST /api/v1/inventory/import` chỉ **cộng dồn** quantity. Chạy lại nhiều lần trên cùng SKU → stock tăng, làm sai ngưỡng "không oversell". Dùng **SKU mới cho mỗi run**, hoặc reset stock trong DB.

## Cách chạy

Chạy từ thư mục `k6/`:

```bash
# Baseline sạch (dưới rate limit gateway, không có 429):
k6 run order-checkout.js -e ADMIN_TOKEN=eyJ... -e TARGET_RPS=50 -e STOCK_QTY=5000

# Nhắm exit criteria 100 req/s:
k6 run order-checkout.js -e ADMIN_TOKEN=eyJ... -e TARGET_RPS=100 -e STOCK_QTY=5000
```

### Tham số (`-e KEY=value`)

| KEY | Mặc định | Ý nghĩa |
|-----|---------|---------|
| `BASE_URL` | `http://localhost:8080` | Gateway — luôn qua cổng này |
| `TARGET_RPS` | `100` | iteration/s nhắm tới (= checkout RPS mục tiêu) |
| `RAMP` | `60` | thời gian ramp lên TARGET_RPS (giây) |
| `HOLD` | `120` | thời gian giữ tải (giây) |
| `USERS` | `100` | số user sẽ register trong setup (mỗi VU 1 user) |
| `SKU` | `SKU-001` | SKU dùng để add cart + checkout |
| `STOCK_QTY` | `5000` | stock seed (khi có ADMIN_TOKEN) + ngưỡng không oversell |
| `ADMIN_TOKEN` | *(trống)* | token admin để seed stock; để trống = giả định stock đã có |
| `PAYMENT_METHOD` | `COD` | `COD` = không gọi VNPay ngoài (ổn định cho load); `VNPAY_QR` = gọi sandbox VNPay |
| `EMAIL_DOMAIN` | `k6load.local` | domain email đăng ký (tránh trùng email giữa các run) |

## Cách đọc kết quả

Sau khi chạy, k6 in ra **threshold summary** + **summary metrics**. Đối chiếu exit criteria:

| Exit criteria | Đọc ở đâu | Đạt khi |
|---------------|-----------|---------|
| p95 < 500ms | `http_req_duration{name:checkout,checkout_status:ok}` — dòng `p(95)` | `< 500` |
| 0 lỗi nghiệp vụ | `checkout_biz_err` (lỗi business bất ngờ) và `checkout_5xx` (server crash) | cả hai `0` |
| Không oversell | `checkout_ok` so với `STOCK_QTY` | `checkout_ok.count <= STOCK_QTY` |
| ~100 req/s | dòng `iterations` / thời lượng — rate = `iterations`/s trong phase HOLD | ≈ `TARGET_RPS` |

Các counter khác:

- `checkout_stockout` — **409 "Sản phẩm không đủ tồn kho"**. Đây là **hành vi đúng** khi stock cạn (không oversell), **không tính là lỗi nghiệp vụ**. Nếu counter này > 0 nghĩa là test đã tiêu hết stock — tăng `STOCK_QTY` hoặc dùng script restock bên dưới nếu muốn chạy lâu hơn.
- `checkout_ratelimited` — **429 từ gateway** (rate limiter Redis). Xem lưu ý dưới.
- `cart_add_fail` — add item vào cart thất bại.

### Lưu ý quan trọng: rate limit gateway

Route `order` của gateway gộp `/api/v1/orders/**`, `/api/v1/cart/**`, `/api/v1/inventory/**` với rate limit **100 req/s / burst 200 theo IP**. Mọi VU chạy từ localhost → chung 1 IP, nên cart-add + checkout **cùng chia 1 ngân sách 100 req/s**.

Hệ quả: chạy full flow (1 cart + 1 checkout mỗi iteration) thì throughput checkout bền vững tối đa là **~50/s** (50 cart + 50 checkout = 100). Nếu `TARGET_RPS > 50`, gateway trả **429** cho phần vượt — k6 đếm vào `checkout_ratelimited` (không fail threshold), nhưng số checkout thật sẽ chững lại quanh 50/s.

Đây là **giới hạn thật của hệ thống**, không phải lỗi script. Để kiểm chứng đúng 100 checkout RPS sạch (không 429), có 2 cách:

1. **Nâng rate limit** route `order` trong `ApiGateway/src/main/resources/application.yml` (vd `replenishRate: 200`) rồi chạy lại — đây là tuning Phase 2.
2. **Đưa cart-add ra khỏi hot path**: pre-fill cart trong setup, hot loop chỉ còn checkout. Script hiện tại không làm sẵn — nếu cần, nhân bản `order-checkout.js`, trong `setup()` thêm vòng lặp `POST /api/v1/cart/items` (SKU, quantity=1) `N` lần cho mỗi user, và bỏ bước add-cart trong `default()`. Cần `USERS × N ≥ TARGET_RPS × HOLD` để giữ tải suốt HOLD.

## Mở rộng quy mô (tăng stock khi cạn)

Khi `checkout_stockout` > 0 (stock đã tiêu hết) mà muốn chạy tiếp, seed thêm stock bằng chính endpoint import — gọi 1 lần ngoài k6, hoặc chạy vòng lặp song song:

```bash
# thêm 5000 vào SKU-001 khi đang chạy test (ADMIN_TOKEN có role ORDER_ADMIN/SUPER_ADMIN)
curl -X POST http://localhost:8080/api/v1/inventory/import \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"sku":"SKU-001","quantity":5000,"reference":"k6-restock"}'
```

> ⚠️ Restock khi test đang chạy sẽ làm `checkout_ok.count` có thể vượt `STOCK_QTY` gốc → threshold không-oversell fail. Chỉ nên làm khi đã hiểu điều này (ngưỡng theo tổng stock seed ban đầu), hoặc dùng `STOCK_QTY` = tổng stock bạn định cấp cho SKU trong cả run.

## Assumption tóm tắt

- Product + variant với `SKU` đã tồn tại trong Product-Catalog (gRPC snapshot khi checkout).
- Stock seed qua `inventory/import` cần token admin; không có thì giả định stock đã đủ.
- Mỗi checkout thành công reserve đúng 1 đơn vị → `checkout_ok.count` = tổng reserved, ngưỡng `<= STOCK_QTY` = không oversell.
- `PAYMENT_METHOD=COD` (mặc định) → không có payment fail/release stock trong run → ngưỡng không-oversell không bị nhiễu. Nếu dùng `VNPAY_QR` mà payment lỗi (release stock), ngưỡng có thể false-alarm.
- Stack chưa chạy → script chưa verify runtime (xem báo cáo).
