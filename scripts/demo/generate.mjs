// Sinh dataset demo cho VoltStack storefront.
// Đọc ảnh đã resolve (data/images.json) + catalog khai báo bên dưới
// → xuất data/seed-data.json (categories/products/stock) dùng cho seed-demo.ps1.
//
// Chạy lại:  node scripts/demo/generate.mjs     (cần Node ≥ 18)

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DATA = path.join(__dirname, 'data')
const IMG = JSON.parse(fs.readFileSync(path.join(DATA, 'images.json'), 'utf8'))

// ── helpers ─────────────────────────────────────────────────────────────
const T0 = '2026-09-03T00:00:00Z' // cột mốc thời gian cố định cho toàn bộ seed
const url = (t) => {
  const u = IMG[t]
  if (!u) { console.warn('MISSING IMG:', t); return '' }
  return u.split('?')[0] // bỏ tracking param utm_* của Commons API
}

// bỏ dấu tiếng Việt + thành slug ascii (khớp toSlug của backend)
function slugify(name) {
  return name
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase().replace(/đ/g, 'd')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

const vnd = (n) => String(n) // backend lưu BigDecimal dưới dạng chuỗi

// variant helper: label = "<nhóm> · <thuộc tính 1> · <thuộc tính 2>" ngắn gọn
function V({ sku, price, attrs = {}, img, label }) {
  const parts = Object.values(attrs)
  return {
    sku, name: label || parts.join(' · '),
    price: vnd(price), attributes: attrs,
    images: (Array.isArray(img) ? img : [img]).filter(Boolean).map(url),
  }
}

const doc = ({ _id, slug, createdAt = T0, ...rest }) => ({ _id, slug, createdAt, updatedAt: T0, ...rest })

// ── categories ──────────────────────────────────────────────────────────
// Category cha/con (parentId). Cha bấm vào sẽ gom sản phẩm của mọi con.
const CATS = [
  { _id: 'cat-dien-thoai', name: 'Điện thoại thông minh', slug: 'dien-thoai' },
  { _id: 'cat-dien-thoai-apple', name: 'Điện thoại Apple', slug: 'dien-thoai-apple', parentId: 'cat-dien-thoai' },
  { _id: 'cat-dien-thoai-samsung', name: 'Điện thoại Samsung', slug: 'dien-thoai-samsung', parentId: 'cat-dien-thoai' },
  { _id: 'cat-may-tinh-bang', name: 'Máy tính bảng', slug: 'may-tinh-bang' },
  { _id: 'cat-laptop', name: 'Laptop', slug: 'laptop' },
  { _id: 'cat-dong-ho', name: 'Đồng hồ', slug: 'dong-ho' },
  { _id: 'cat-tai-nghe', name: 'Tai nghe & Loa', slug: 'tai-nghe-loa' },
  { _id: 'cat-giay', name: 'Giày thể thao', slug: 'giay-the-thao' },
  { _id: 'cat-may-anh', name: 'Máy ảnh & Ống kính', slug: 'may-anh' },
  { _id: 'cat-thoi-trang-nam', name: 'Thời trang nam', slug: 'thoi-trang-nam' },
  { _id: 'cat-ao-khoac', name: 'Áo khoác', slug: 'ao-khoac', parentId: 'cat-thoi-trang-nam' },
  { _id: 'cat-ao-blazer', name: 'Áo blazer', slug: 'ao-blazer', parentId: 'cat-thoi-trang-nam' },
  { _id: 'cat-quan-jean', name: 'Quần jean', slug: 'quan-jean', parentId: 'cat-thoi-trang-nam' },
]

// ── products ────────────────────────────────────────────────────────────
// imgs: tên file Wikimedia đã resolve trong images.json (ảnh đúng chủ đề theo tên file)
const P = (id, catId, name, brand, desc, variants) =>
  doc({
    _id: id,
    slug: slugify(name),
    categoryId: catId, name, brand,
    description: desc, isActive: true,
    variants,
    _class: 'com.example.productcatalogservice.model.Product',
  })

const DTA = 'cat-dien-thoai-apple', DTS = 'cat-dien-thoai-samsung', TB = 'cat-may-tinh-bang', LP = 'cat-laptop'
const DH = 'cat-dong-ho', TN = 'cat-tai-nghe', GY = 'cat-giay', MA = 'cat-may-anh'
const AK = 'cat-ao-khoac', AB = 'cat-ao-blazer', QJ = 'cat-quan-jean' // Thời trang nam

const PRODUCTS = [
  // ── Điện thoại ────────────────────────────────────────────────────────
  P('sp-iphone-15-pro-max', DTA, 'Apple iPhone 15 Pro Max 256GB', 'Apple',
    'iPhone 15 Pro Max với khung titan siêu nhẹ, chip A17 Pro mạnh nhất từ trước đến nay, màn hình Super Retina XDR 6,7" và camera chính 48MP. Hàng chính hãng VN/A, bảo hành 12 tháng.',
    [
      V({ sku: 'IP15PM-256-NAT', price: 33990000, attrs: { 'Màu sắc': 'Titan Tự Nhiên', 'Dung lượng': '256GB' },
          label: '256GB · Titan Tự Nhiên',
          img: ['File:Front of iPhone 15 Pro Max.jpg', 'File:IPhone 15 Pro Max Camera.jpg'] }),
      V({ sku: 'IP15PM-256-BLU', price: 33990000, attrs: { 'Màu sắc': 'Titan Xanh', 'Dung lượng': '256GB' },
          label: '256GB · Titan Xanh', img: ['File:Front of iPhone 15 Pro Max.jpg'] }),
      V({ sku: 'IP15PM-512-NAT', price: 39990000, attrs: { 'Màu sắc': 'Titan Tự Nhiên', 'Dung lượng': '512GB' },
          label: '512GB · Titan Tự Nhiên', img: ['File:Front of iPhone 15 Pro Max.jpg'] }),
    ]),
  P('sp-iphone-15', DTA, 'Apple iPhone 15 128GB', 'Apple',
    'iPhone 15 với Dynamic Island, camera chính 48MP và màu phấn mới. Chip A16 Bionic, sạc USB-C. Hàng chính hãng, bảo hành 12 tháng.',
    [
      V({ sku: 'IP15-128-BLU', price: 20990000, attrs: { 'Màu sắc': 'Xanh Dương', 'Dung lượng': '128GB' },
          label: '128GB · Xanh Dương', img: ['File:Apple iPhone 15.jpeg'] }),
      V({ sku: 'IP15-128-PNK', price: 20990000, attrs: { 'Màu sắc': 'Hồng', 'Dung lượng': '128GB' },
          label: '128GB · Hồng', img: ['File:Apple iPhone 15.jpeg'] }),
      V({ sku: 'IP15-256-BLK', price: 23990000, attrs: { 'Màu sắc': 'Đen', 'Dung lượng': '256GB' },
          label: '256GB · Đen', img: ['File:Apple iPhone 15.jpeg'] }),
    ]),
  P('sp-galaxy-s24-ultra', DTS, 'Samsung Galaxy S24 Ultra 256GB', 'Samsung',
    'Galaxy S24 Ultra với khung Titan, bút S Pen, Galaxy AI và camera zoom 5x. Màn hình 6,8" QHD+ 120Hz, pin 5000mAh. Hàng chính hãng, bảo hành 12 tháng.',
    [
      V({ sku: 'GS24U-256-TBL', price: 30990000, attrs: { 'Màu sắc': 'Titan Đen', 'Dung lượng': '256GB' },
          label: '256GB · Titan Đen', img: ['File:Samsung Galaxy S24 Ultra - by-RaBoe 001.jpg'] }),
      V({ sku: 'GS24U-512-TVL', price: 34990000, attrs: { 'Màu sắc': 'Titan Tím', 'Dung lượng': '512GB' },
          label: '512GB · Titan Tím', img: ['File:Samsung Galaxy S24 Ultra - by-RaBoe 001.jpg'] }),
    ]),
  P('sp-galaxy-a55', DTS, 'Samsung Galaxy A55 5G 256GB', 'Samsung',
    'Galaxy A55 5G với khung nhôm chắc chắn, màn hình Super AMOLED 6,6" 120Hz và 4 năm cập nhật hệ điều hành. Camera 50MP chống rung OIS.',
    [
      V({ sku: 'GA55-256-BLK', price: 12490000, attrs: { 'Màu sắc': 'Đen', 'Dung lượng': '256GB' },
          label: '256GB · Đen', img: ['File:Samsung Galaxy A55 5G 2024.jpg'] }),
      V({ sku: 'GA55-256-LBL', price: 12490000, attrs: { 'Màu sắc': 'Xanh Nhạt', 'Dung lượng': '256GB' },
          label: '256GB · Xanh Nhạt', img: ['File:Samsung Galaxy A55 5G 2024.jpg'] }),
    ]),
  P('sp-galaxy-z-fold-4', DTS, 'Samsung Galaxy Z Fold 4 256GB', 'Samsung',
    'Điện thoại gập Galaxy Z Fold 4: màn hình chính 7,6" khi mở ra như một chiếc máy tính bảng, đa nhiệm cùng lúc nhiều ứng dụng.',
    [
      V({ sku: 'GZF4-256-GRY', price: 29990000, attrs: { 'Màu sắc': 'Xám', 'Dung lượng': '256GB' },
          label: '256GB · Xám', img: ['File:Front of the Samsung Galaxy Z Fold 4.jpg'] }),
    ]),

  // ── Máy tính bảng ─────────────────────────────────────────────────────
  P('sp-galaxy-tab-s8', TB, 'Samsung Galaxy Tab S8 (WiFi) 128GB', 'Samsung',
    'Máy tính bảng Galaxy Tab S8 11" LTPS 120Hz đi kèm bút S Pen trong hộp, chip Snapdragon 8 Gen 1, 4 loa AKG — lựa chọn cho học tập và giải trí.',
    [
      V({ sku: 'GTABS8-128-GPH', price: 16990000, attrs: { 'Màu sắc': 'Xám', 'Bộ nhớ': '128GB' },
          label: '128GB · Xám', img: ['File:Samsung Galaxy Tab S8.jpg'] }),
    ]),
  P('sp-galaxy-tab-s8-ultra', TB, 'Samsung Galaxy Tab S8 Ultra 512GB', 'Samsung',
    'Tab S8 Ultra màn hình Super AMOLED 14,6" 120Hz — màn hình lớn nhất dòng Galaxy Tab, phù hợp vẽ, họp và làm việc chuyên sâu.',
    [
      V({ sku: 'GTABS8U-512-GPH', price: 23990000, attrs: { 'Màu sắc': 'Xám', 'Bộ nhớ': '512GB' },
          label: '512GB · Xám', img: ['File:Samsung Galaxy Tab S8 Ultra.jpg'] }),
    ]),

  // ── Laptop ────────────────────────────────────────────────────────────
  P('sp-macbook-air-15-m4', LP, 'Apple MacBook Air 15" M4 16GB/256GB', 'Apple',
    'MacBook Air 15 inch mỏng nhẹ với chip M4, màn hình Liquid Retina, thời lượng pin lên đến 18 giờ. Silicon Apple: êm, mát, mượt.',
    [
      V({ sku: 'MBA15-M4-16256-SLV', price: 34990000, attrs: { 'Màu': 'Bạc', 'Chip': 'Apple M4', 'RAM': '16GB' },
          label: 'M4 · 16GB · 256GB · Bạc', img: ['File:MacBook Air (15-inch, M4, Silver).jpg'] }),
    ]),
  P('sp-macbook-pro-14-m3', LP, 'Apple MacBook Pro 14" M3 Pro 18GB/512GB', 'Apple',
    'MacBook Pro 14 inch với chip M3 Pro, màn hình Liquid Retina XDR, phù hợp creator và lập trình viên chuyên nghiệp.',
    [
      V({ sku: 'MBP14-M3P-512-SPG', price: 46990000, attrs: { 'Màu': 'Xám Không Gian', 'Chip': 'Apple M3 Pro', 'RAM': '18GB' },
          label: 'M3 Pro · 18GB · 512GB', img: ['File:M3 Macbook Pro 14 inch Space Grey model (cropped).jpg'] }),
    ]),
  P('sp-hp-spectre-x360', LP, 'HP Spectre x360 13.5" i7 16GB/512GB', 'HP',
    'Laptop 2-trong-1 cao cấp HP Spectre x360: gập xoay 360°, màn hình OLED cảm ứng, viền kim loại cắt kim cương sang trọng.',
    [
      V({ sku: 'HPSPX13-16512-NBL', price: 32990000, attrs: { 'Màu': 'Xanh Đêm', 'CPU': 'Intel Core i7', 'RAM': '16GB' },
          label: 'i7 · 16GB · 512GB', img: ['File:Hp Spectre x360 13t.jpg'] }),
    ]),
  P('sp-lenovo-thinkpad-t14', LP, 'Lenovo ThinkPad T14 Gen 4 14" 16GB/512GB', 'Lenovo',
    'ThinkPad T14 bền bỉ chuẩn doanh nghiệp: bàn phím nổi tiếng, vỏ sợi carbon, vô số cổng kết nối — trợ thủ cho dân văn phòng.',
    [
      V({ sku: 'LTPT14-16512-BLK', price: 23990000, attrs: { 'Màu': 'Đen', 'CPU': 'Intel Core i5', 'RAM': '16GB' },
          label: 'i5 · 16GB · 512GB', img: ['File:ThinkPad T14.jpg'] }),
    ]),
  P('sp-asus-vivobook-s14', LP, 'ASUS Vivobook S14 OLED 14" 16GB/512GB', 'ASUS',
    'Vivobook S14 màn hình OLED 2.8K 120Hz, hiệu năng Intel Core Ultra, thiết kế trẻ trung — cân cả học tập lẫn công việc.',
    [
      V({ sku: 'ASVSB14-16512-SLV', price: 21990000, attrs: { 'Màu': 'Bạc', 'CPU': 'Intel Core Ultra 5', 'RAM': '16GB' },
          label: 'Ultra 5 · 16GB · 512GB', img: ['File:A ASUS VivoBook on the desk.jpg'] }),
    ]),

  // ── Đồng hồ ───────────────────────────────────────────────────────────
  P('sp-apple-watch-s8', DH, 'Apple Watch Series 8 45mm GPS', 'Apple',
    'Apple Watch Series 8 với cảm biến nhiệt độ, đo ECG, theo dõi giấc ngủ và khả năng chống nước 50m. Vỏ nhôm Midnight, dây thể thao.',
    [
      V({ sku: 'AW8-45-GPS-MID', price: 12490000, attrs: { 'Màu': 'Midnight', 'Kết nối': 'GPS', 'Kích thước': '45mm' },
          label: '45mm · Midnight · GPS', img: ['File:Apple Watch Series 8 Midnight Aluminium Case.jpg'] }),
    ]),
  P('sp-galaxy-watch6', DH, 'Samsung Galaxy Watch6 40mm Bluetooth', 'Samsung',
    'Galaxy Watch6 theo dõi sức khỏe toàn diện: đo nhịp tim, ECG, chỉ số cơ thể và giấc ngủ. Màn hình AMOLED sáng rõ, viền mỏng.',
    [
      V({ sku: 'GW6-40-BT-GPH', price: 6990000, attrs: { 'Màu': 'Xám', 'Kết nối': 'Bluetooth', 'Kích thước': '40mm' },
          label: '40mm · Xám', img: ['File:Samsung Galaxy Watch 6 2.jpg'] }),
    ]),
  P('sp-casio-gshock-ga2100', DH, 'Casio G-Shock GA-2100', 'Casio',
    'G-Shock GA-2100 huyền thoại “CasiOak” — kháng sốc, kháng nước 200m, thiết kế bát giác góc cạnh mang phong cách streetwear.',
    [
      V({ sku: 'CGA2100-1A-BLK', price: 2790000, attrs: { 'Màu': 'Đen' },
          label: 'Đen', img: ['File:Casio G-Shock.jpg'] }),
    ]),
  P('sp-citizen-nam', DH, 'Citizen Nam Automatic Dây Thép', 'Citizen',
    'Đồng hồ Citizen nam máy Automatic Nhật Bản, mặt số thanh lịch, dây thép không gỉ — phù hợp công sở và đi làm.',
    [
      V({ sku: 'CZNAUTO-STL', price: 4590000, attrs: { 'Máy': 'Automatic', 'Dây': 'Thép không gỉ' },
          label: 'Mặt trắng · Dây thép', img: ['File:Citizen wristwatch.jpg'] }),
    ]),
  P('sp-fossil-nam', DH, 'Fossil Nam Chronograph Dây Da', 'Fossil',
    'Đồng hồ Fossil nam thiết kế Chronograph thể thao, dây da cao cấp, phù hợp phối đồ hằng ngày.',
    [
      V({ sku: 'FSLCHRONO-LTHR', price: 3990000, attrs: { 'Máy': 'Quartz', 'Dây': 'Da' },
          label: 'Mặt đen · Dây da', img: ['File:Fossil wristwatch with white background.jpg'] }),
    ]),

  // ── Tai nghe & Loa ────────────────────────────────────────────────────
  P('sp-airpods-pro-2', TN, 'Apple AirPods Pro 2 (USB-C)', 'Apple',
    'AirPods Pro 2 với chip H2, chống ồn chủ động gấp đôi, âm thanh không gian cá nhân hoá và hộp sạc MagSafe USB-C.',
    [
      V({ sku: 'APPR2-USBC-WHT', price: 5490000, attrs: { 'Màu': 'Trắng' },
          label: 'Trắng · USB-C', img: ['File:2025-10-24 AirPods Pro 2 dunkelblau.jpg'] }),
    ]),
  P('sp-bose-qc45', TN, 'Bose QuietComfort 45', 'Bose',
    'Tai nghe chụp đầu Bose QC45 với chống ồn chủ động hàng đầu, âm thanh cân bằng, thời lượng pin 24 giờ.',
    [
      V({ sku: 'BQC45-BLK', price: 7990000, attrs: { 'Màu': 'Đen' },
          label: 'Đen', img: ['File:Bose Headphones.jpg'] }),
    ]),
  P('sp-jbl-flip-6', TN, 'Loa Bluetooth JBL Flip 6', 'JBL',
    'Loa JBL Flip 6 âm bass sâu, chống nước IP67, ghép nối nhiều loa cùng lúc, pin 12 giờ — nghe nhạc mọi nơi.',
    [
      V({ sku: 'JBFLIP6-SQD', price: 2990000, attrs: { 'Màu': 'Xanh Quân Đội' },
          label: 'Xanh Quân Đội', img: ['File:JBL speaker, 2021.jpg'] }),
    ]),
  P('sp-anker-soundcore-motion', TN, 'Anker Soundcore Motion+', 'Anker',
    'Loa Anker Soundcore Motion+ công suất 30W, Hi-Res Audio, 2 loa tweeter và passive radiator — âm thanh chi tiết, pin 12 giờ.',
    [
      V({ sku: 'ANSMOTION+-BLK', price: 1990000, attrs: { 'Màu': 'Đen' },
          label: 'Đen', img: ['File:Anker SoundCore - 1.jpg'] }),
    ]),

  // ── Giày thể thao ─────────────────────────────────────────────────────
  P('sp-nike-air-max-dn', GY, 'Nike Air Max Dn', 'Nike',
    'Air Max Dn với hệ thống đệm khí 4 ống Dynamic Air, thiết kế tương lai — thoải mái cho mọi hoạt động thường ngày.',
    [
      V({ sku: 'NKAMDn-40', price: 4590000, attrs: { 'Màu sắc': 'Đa sắc' },
          label: 'Đa sắc', img: ['File:Nike Air Max Dn 02.jpg'] }),
    ]),
  P('sp-nike-air-force-1', GY, 'Nike Air Force 1 Low', 'Nike',
    'AF1 Low kinh điển với da trắng phối màu Flax, đế dày êm ái — item không thể thiếu trong tủ giày.',
    [
      V({ sku: 'NKAF1-FLAX', price: 3290000, attrs: { 'Màu sắc': 'Trắng / Nâu' },
          label: 'Trắng / Nâu Flax', img: ['File:Nike Air Force 1 Low Flax 2019.jpg'] }),
    ]),
  P('sp-adidas-yeezy-350', GY, 'Adidas Yeezy Boost 350 V2', 'Adidas',
    'Yeezy Boost 350 V2 với đế Boost cực êm, upper dệt Primeknit thoáng khí — đôi giày được săn đón nhất nhì dòng Yeezy.',
    [
      V({ sku: 'ADY350-V2', price: 8990000, attrs: { 'Màu sắc': 'Onyx' },
          label: 'Onyx', img: ['File:2023 Adidas Yeezy 350 V2 ID4811 (1).jpg'] }),
    ]),
  P('sp-converse-chuck-70', GY, 'Converse Chuck Taylor All Star', 'Converse',
    'Chuck Taylor All Star cổ điển với vải canvas bền, mũi giày cao su đặc trưng — vẻ đẹp vượt thời gian.',
    [
      V({ sku: 'CNCT70-WHT', price: 1790000, attrs: { 'Màu sắc': 'Trắng' },
          label: 'Trắng', img: ['File:Converse Chuck Taylor All-Stars (51091002425).jpg'] }),
    ]),
  P('sp-asics-gel-cumulus', GY, 'ASICS Gel-Cumulus 26', 'ASICS',
    'Gel-Cumulus 26 với đệm FF Blast+ và gel ở gót — đôi giày chạy bộ linh hoạt cho đường dài hằng ngày.',
    [
      V({ sku: 'ASGC26', price: 2990000, attrs: { 'Màu sắc': 'Xanh' },
          label: 'Xanh', img: ['File:Asics Gel-Cumulus 22.jpg'] }),
    ]),
  P('sp-puma-suede-classic', GY, 'Puma Suede Classic XXI', 'Puma',
    'Suede Classic với chất liệu da lộn mềm, form cổ điển từ thập niên 80 — phối đồ hip-hop, retro.',
    [
      V({ sku: 'PMSC21-RED', price: 2190000, attrs: { 'Màu sắc': 'Đỏ' },
          label: 'Đỏ', img: ['File:Puma Suede.jpg'] }),
    ]),

  // ── Máy ảnh ───────────────────────────────────────────────────────────
  P('sp-canon-eos-r5', MA, 'Canon EOS R5 Body', 'Canon',
    'EOS R5 45MP full-frame, quay video 8K, chống rung 5 trục 8 stops — máy ảnh mirrorless toàn năng cho cả ảnh lẫn video.',
    [
      V({ sku: 'CNR5-BODY', price: 62990000, attrs: { 'Loại': 'Body (không kèm ống kính)' },
          label: 'Body', img: ['File:Canon EOS R5.jpg'] }),
    ]),
  P('sp-canon-eos-r6-mk3', MA, 'Canon EOS R6 Mark III Body', 'Canon',
    'EOS R6 Mark III 24MP full-frame, lấy nét Dual Pixel thông minh, chụp liên tiếp 40fps — lựa chọn của tay săn ảnh thể thao.',
    [
      V({ sku: 'CNR6M3-BODY', price: 48990000, attrs: { 'Loại': 'Body (không kèm ống kính)' },
          label: 'Body', img: ['File:Canon EOS R6 Mark III 26 nov 2025a.jpg'] }),
    ]),
  P('sp-sony-alpha-7', MA, 'Sony Alpha 7 Body', 'Sony',
    'Sony A7 full-frame 24MP, ngàm E-mount, cảm biến Exmor CMOS — bước vào thế giới full-frame với trọng lượng nhẹ.',
    [
      V({ sku: 'SNA7-BODY', price: 32990000, attrs: { 'Loại': 'Body (không kèm ống kính)' },
          label: 'Body', img: ['File:Sony Alpha 7.jpg'] }),
    ]),
  P('sp-nikon-d850', MA, 'Nikon D850 Body', 'Nikon',
    'DSLR Nikon D850 45.7MP với khẩu độ sáng viewfinder, chất lượng ảnh tuyệt đỉnh — huyền thoại máy ảnh DSLR full-frame.',
    [
      V({ sku: 'NKD850-BODY', price: 56990000, attrs: { 'Loại': 'Body (không kèm ống kính)' },
          label: 'Body', img: ['File:Nikon DSLR camera D850.jpg'] }),
    ]),

  // ── Thời trang nam ─────────────────────────────────────────────────────
  P('sp-levis-501-jean', QJ, "Quần jean nam Levi's 501 Original", "Levi's",
    "Quần jean Levi's 501 huyền thoại với denim 100% cotton, cúc đồng và nhãn đỏ Red Tab đặc trưng. Form regular thoải mái, bền đẹp theo thời gian — item không thể thiếu của tủ đồ nam.",
    [
      V({ sku: 'LEV501-32-WASH', price: 1490000, attrs: { 'Kích cỡ': '32', 'Ống quần': 'Đứng' },
          label: 'Kích cỡ 32', img: ["File:Levi's 501 jeans 'big E' Red Tab (2025-12-19) 3.jpg"] }),
      V({ sku: 'LEV501-34-WASH', price: 1490000, attrs: { 'Kích cỡ': '34', 'Ống quần': 'Đứng' },
          label: 'Kích cỡ 34', img: ["File:Levi's 501 jeans 'small e' Red Tab (2025-12-19) 4.jpg"] }),
    ]),
  P('sp-tnf-mountain-light', AK, 'Áo khoác The North Face Mountain Light Triclimate', 'The North Face',
    'Áo khoác 3-trong-1 Mountain Light của The North Face: lớp vỏ chống gió/nước + lớp lót ấm có thể tháo rời, đeo riêng hoặc ghép tùy thời tiết. Phù hợp leo núi và đi làm hằng ngày.',
    [
      V({ sku: 'TNFML-S', price: 6990000, attrs: { 'Kích cỡ': 'S', 'Màu': 'Đen' }, label: 'S', img: ['File:The North Face Mountain Light Triclimate Down Jacket.jpg'] }),
      V({ sku: 'TNFML-M', price: 6990000, attrs: { 'Kích cỡ': 'M', 'Màu': 'Đen' }, label: 'M', img: ['File:The North Face Mountain Light Triclimate Down Jacket.jpg'] }),
      V({ sku: 'TNFML-L', price: 6990000, attrs: { 'Kích cỡ': 'L', 'Màu': 'Đen' }, label: 'L', img: ['File:The North Face Mountain Light Triclimate Down Jacket.jpg'] }),
    ]),
  P('sp-tnf-arctic-parka', AK, 'Áo khoác phao The North Face Arctic Parka', 'The North Face',
    'Áo khoác phao The North Face giữ ấm cực tốt cho mùa đông lạnh, vỏ chống nước, mũ trùm ấm — phối đồ streetwear đẹp mà vẫn ấm.',
    [
      V({ sku: 'TNFAP-S', price: 5490000, attrs: { 'Kích cỡ': 'S', 'Màu': 'Nhiều họa tiết' }, label: 'S', img: ['File:The North Face Arctic Swirl Down Parka.jpg'] }),
      V({ sku: 'TNFAP-M', price: 5490000, attrs: { 'Kích cỡ': 'M', 'Màu': 'Nhiều họa tiết' }, label: 'M', img: ['File:The North Face Arctic Swirl Down Parka.jpg'] }),
      V({ sku: 'TNFAP-L', price: 5490000, attrs: { 'Kích cỡ': 'L', 'Màu': 'Nhiều họa tiết' }, label: 'L', img: ['File:The North Face Arctic Swirl Down Parka.jpg'] }),
    ]),
  P('sp-alpha-ma2', AK, 'Áo khoác bomber quân đội Alpha Industries MA-2', 'Alpha Industries',
    'Áo khoác bomber Alpha Industries phong cách phi công Mỹ: chất liệu nylon bền, lót bông ấm, khóa kéo + túi áo đặc trưng. Item streetwear kinh điển.',
    [
      V({ sku: 'AIMA2-M', price: 3990000, attrs: { 'Kích cỡ': 'M', 'Màu': 'Xanh ô liu' }, label: 'M', img: ['File:MA-2 jacket.jpg'] }),
      V({ sku: 'AIMA2-L', price: 3990000, attrs: { 'Kích cỡ': 'L', 'Màu': 'Xanh ô liu' }, label: 'L', img: ['File:MA-2 jacket.jpg'] }),
      V({ sku: 'AIMA2-XL', price: 3990000, attrs: { 'Kích cỡ': 'XL', 'Màu': 'Xanh ô liu' }, label: 'XL', img: ['File:MA-2 jacket.jpg'] }),
    ]),
  P('sp-blazer-navy', AB, 'Áo blazer nam navy Massimo Dutti', 'Massimo Dutti',
    'Áo blazer nam màu navy thanh lịch, dáng slim ôm, cúc đồng vàng, may từ vải tuýt cao cấp — phù hợp công sở và tiệc tùng. Phối quần tây hoặc jean đều đẹp.',
    [
      V({ sku: 'BZN-M', price: 2590000, attrs: { 'Kích cỡ': 'M', 'Màu': 'Xanh navy' }, label: 'M', img: ['File:Navy blazer jacket.jpg'] }),
      V({ sku: 'BZN-L', price: 2590000, attrs: { 'Kích cỡ': 'L', 'Màu': 'Xanh navy' }, label: 'L', img: ['File:Navy blazer jacket.jpg'] }),
    ]),
]

// ── stock (tồn kho ban đầu cho từng SKU) ────────────────────────────────
// một vài SKU để mức thấp (< ngưỡng 5) để demo cảnh báo tồn kho
const STOCK = {
  'IP15PM-256-NAT': 15, 'IP15PM-256-BLU': 8, 'IP15PM-512-NAT': 5,
  'IP15-128-BLU': 20, 'IP15-128-PNK': 12, 'IP15-256-BLK': 9,
  'GS24U-256-TBL': 14, 'GS24U-512-TVL': 4,
  'GA55-256-BLK': 25, 'GA55-256-LBL': 18,
  'GZF4-256-GRY': 6,
  'GTABS8-128-GPH': 12, 'GTABS8U-512-GPH': 3,
  'MBA15-M4-16256-SLV': 10, 'MBP14-M3P-512-SPG': 7,
  'HPSPX13-16512-NBL': 5, 'LTPT14-16512-BLK': 15, 'ASVSB14-16512-SLV': 11,
  'AW8-45-GPS-MID': 13, 'GW6-40-BT-GPH': 16,
  'CGA2100-1A-BLK': 22, 'CZNAUTO-STL': 9, 'FSLCHRONO-LTHR': 14,
  'APPR2-USBC-WHT': 30, 'BQC45-BLK': 12, 'JBFLIP6-SQD': 19, 'ANSMOTION+-BLK': 8,
  'NKAMDn-40': 2, 'NKAF1-FLAX': 17, 'ADY350-V2': 5, 'CNCT70-WHT': 26, 'ASGC26': 10, 'PMSC21-RED': 4,
  'CNR5-BODY': 3, 'CNR6M3-BODY': 4, 'SNA7-BODY': 6, 'NKD850-BODY': 2,
  'LEV501-32-WASH': 12, 'LEV501-34-WASH': 10,
  'TNFML-S': 6, 'TNFML-M': 9, 'TNFML-L': 7,
  'TNFAP-S': 5, 'TNFAP-M': 8, 'TNFAP-L': 4,
  'AIMA2-M': 11, 'AIMA2-L': 9, 'AIMA2-XL': 6,
  'BZN-M': 8, 'BZN-L': 7,
}

// kiểm tra SKU nào khai báo tồn kho nhưng không có trong sản phẩm (tránh "kho ma")
const allSkus = new Set(PRODUCTS.flatMap((p) => p.variants.map((v) => v.sku)))
for (const sku of Object.keys(STOCK)) {
  if (!allSkus.has(sku)) console.warn('STOCK cho SKU không tồn tại trong catalog:', sku)
}

const categories = CATS.map((c) => ({
  _id: c._id, name: c.name, slug: c.slug, parentId: c.parentId ?? null,
  status: 'ACTIVE', createdAt: T0, updatedAt: T0,
  _class: 'com.example.productcatalogservice.model.Category',
}))

// ── seed-mongo.js: script mongosh tự chứa data (không cần đọc file ngoài) ──
const script = `// AUTO-GENERATED by scripts/demo/generate.mjs - dung cho seed-demo.ps1
// Upsert theo _id (replaceOne) - chay lai bao nhieu lan cung duoc.
const toDate = (s) => new Date(s)
const prep = (list, keys) => list.map((doc) => {
  const c = { ...doc }
  for (const k of keys) if (c[k]) c[k] = toDate(c[k])
  return c
})
const categories = prep(${JSON.stringify(categories)}, ['createdAt', 'updatedAt'])
const products = prep(${JSON.stringify(PRODUCTS)}, ['createdAt', 'updatedAt'])
let upserted = 0
for (const c of categories) if (db.categories.replaceOne({ _id: c._id }, c, { upsert: true }).upsertedCount) upserted++
for (const p of products) if (db.products.replaceOne({ _id: p._id }, p, { upsert: true }).upsertedCount) upserted++
const variants = products.reduce((n, p) => n + p.variants.length, 0)
print('[mongo] upserted=' + upserted +
  ' categories=' + db.categories.countDocuments() +
  ' products=' + db.products.countDocuments() +
  ' seedVariants=' + variants)
`
fs.writeFileSync(path.join(DATA, 'seed-mongo.js'), script, 'utf8')

// SQL upsert tồn kho cho order-postgres (đã seed bằng ID cố định để chạy lại không nhân đôi)
const skuRows = Object.entries(STOCK).sort(([a], [b]) => a.localeCompare(b))
const invSql = []
invSql.push('-- demo stock, generated by scripts/demo/generate.mjs')
invSql.push('BEGIN;')
for (const [sku, qty] of skuRows) {
  invSql.push(`INSERT INTO inventory (sku, quantity) VALUES ('${sku}', ${qty})
  ON CONFLICT (sku) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now();`)
}
invSql.push('DELETE FROM inventory_transactions WHERE reference = \'demo-seed\';')
for (const [sku, qty] of skuRows) {
  invSql.push(`INSERT INTO inventory_transactions (id, sku, type, quantity, reference)
  VALUES (gen_random_uuid(), '${sku}', 'IMPORT', ${qty}, 'demo-seed');`)
}
invSql.push('COMMIT;')
fs.writeFileSync(path.join(DATA, 'stock.sql'), invSql.join('\n'), 'utf8')

console.log(`OK  categories=${categories.length}  products=${PRODUCTS.length}  variants=${allSkus.size}  sku-with-stock=${skuRows.length}`)
console.log('->  data/seed-mongo.js  +  data/stock.sql')
