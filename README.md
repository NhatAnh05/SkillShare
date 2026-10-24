# Skill Rental — Nhóm A / Customer Marketplace

Website Java Spring Boot hoàn chỉnh cho phạm vi Customer / Marketplace.

## Stack
- Java 17
- Spring Boot 3.3.5
- Spring Web MVC + Thymeleaf
- Spring Security (session-based)
- Spring Data JPA + Bean Validation
- H2 mặc định; có profile MySQL
- Maven
- CSS thuần, không cần Node.js

## 17 màn hình
Login, Register, Forgot Password, Change Password, Home/Marketplace, Search Provider, Customer Profile, My Requests, Create Request, Apply List, Provider Detail, Booking, Payment, Orders, Progress, Chat, Review/Complaint.

## UC đã bao phủ
- UC-01→UC-09: đăng ký, đăng nhập, đăng xuất, quên/đổi mật khẩu, tài khoản/profile, tìm kiếm & xem Provider.
- UC-10→UC-17: tạo/quản lý request, trạng thái, Apply list, chọn Apply, theo dõi luồng request.
- UC-18→UC-23 + UC-25: booking thường, premium +25%, direct booking, date/time slot, order, payment/coupon, xác nhận hoàn thành.
- UC-24 + UC-26→UC-28: chat polling, review/phản hồi, complaint.

## Chạy H2
```bash
mvn spring-boot:run
```
Mở http://localhost:8080

H2 console: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:skillrental`  
User: `sa`, password trống.

## Tài khoản demo
Customer:
- `customer@skillrental.vn`
- `123456`

Provider demo:
- `provider1@skillrental.vn`
- `123456`

Admin demo:
- `admin@skillrental.vn`
- `123456`

These are the three primary usable demo logins. When a database is empty, the seed also adds 98 synthetic customer/provider rows in `LOCKED` status (using `.invalid` email addresses and random BCrypt hashes) so one-to-one profile tables and foreign-key relationships can each contain at least 50 records. The synthetic records are not documented login accounts. All payment/OAuth/refund values are placeholders for demonstrations only.

## Coupon demo
- `WELCOME10` giảm 10%
- `SKILL20` giảm 20%

## Database-first với MySQL

- SQL schema cập nhật Escrow/ví nội bộ (26 bảng): `src/main/resources/db/schema-mysql.sql`.
- Dữ liệu mẫu mở rộng: `src/main/resources/db/seed-demo-data.sql` (26/26 bảng có ít nhất 50 bản ghi; chỉ chạy một lần trên schema trống).
- Dữ liệu bổ sung tự động cho H2/MySQL trống: `src/main/resources/db/seed-expand-demo-data.sql`, được gọi sau khi seed gốc bởi `DemoDataConfig`.
- Migration từ schema v1 trước đó: `src/main/resources/db/migration-v1-to-v2.sql` (chỉ chạy một lần trên database v1, sau khi backup).
- Migration từ schema v2 sang v3 (thêm ví nội bộ/Escrow, sổ giao dịch và cập nhật trạng thái đơn): `src/main/resources/db/migration-v2-to-v3-escrow.sql` (chỉ chạy một lần sau khi backup).
- Tài liệu thiết kế, quan hệ, enum và đối chiếu chức năng nhóm: [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md).
- Thống kê số bản ghi từng bảng và thông tin đăng nhập: [`docs/DEMO_SEED_DATA_SUMMARY.md`](docs/DEMO_SEED_DATA_SUMMARY.md).
- Câu SQL kiểm tra số lượng bản ghi: [`docs/verify-seed-counts.sql`](docs/verify-seed-counts.sql).
- ERD: [`docs/skillshare-erd.png`](docs/skillshare-erd.png), SVG và nguồn Mermaid/DOT trong `docs/`.

Khởi tạo database mới (MySQL 8.0.16+):
```bash
mysql -u root -p < src/main/resources/db/schema-mysql.sql
```

Để chèn dữ liệu mẫu trực tiếp bằng MySQL Workbench/CLI, **trước khi chạy ứng dụng lần đầu**, thực hiện thêm:
```bash
mysql -u root -p < src/main/resources/db/seed-demo-data.sql
```
Nếu chạy ứng dụng trước, `DemoDataConfig` sẽ tạo bộ dữ liệu mẫu mở rộng khi `users` rỗng; trong trường hợp đó không chạy thêm `seed-demo-data.sql` lần nữa. Các bảng đã có dữ liệu sẽ không tự động seed bổ sung.

Sau đó cấu hình `DB_USERNAME` và `DB_PASSWORD`, bật profile `mysql` và chạy:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Profile MySQL sử dụng `spring.jpa.hibernate.ddl-auto=validate` để kiểm tra entity có khớp schema đã tạo. Database mới dùng `schema-mysql.sql`; database đã tồn tại từ schema cũ phải dùng migration v1→v2 sau khi sao lưu. Không chạy script khởi tạo trực tiếp lên database có dữ liệu quan trọng.

## Cấu trúc
```text
src/main/java/com/skillrental
├── config
├── controller
├── dto
├── entity
├── repository
└── service

src/main/resources
├── application.properties
├── application-mysql.properties
├── db/
├── static/css/app.css
└── templates/
    ├── auth/
    ├── booking/
    ├── customer/
    ├── interaction/
    ├── marketplace/
    ├── orders/
    └── requests/
```

## Design System
Giữ đúng palette:
`#16A34A`, `#15803D`, `#DCFCE7`, `#0F172A`, `#F8FAFC`, `#FFFFFF`, `#1E293B`, `#64748B`, `#E2E8F0`, `#22C55E`, `#F59E0B`, `#EF4444`, `#3B82F6`.
Font Inter; button 44px/radius 8px; card radius 12px/padding 20px.


## Điều chỉnh giao diện sau review (bản này)
- Đã chỉnh sửa trực tiếp trên source đính kèm; không thay kiến trúc Controller / Service / Repository / Thymeleaf.
- Login: căn chiều cao viewport, sửa `<h1>` tên website, thêm ghi nhớ đăng nhập, chú thích và dùng Spring Security Remember Me. Remember Me lưu cookie đăng nhập, **không** lưu mật khẩu vào trình duyệt.
- Phân nhóm layout: các trang công khai sau đăng nhập (Trang chủ, Tìm Provider, Chi tiết Provider, Đặt lịch, Thanh toán) dùng Header + Footer, không Sidebar. Các trang quản lý (Hồ sơ, Cài đặt, Request, Đơn hàng, Tiến độ, Đổi mật khẩu, Đánh giá, Khiếu nại) dùng Sidebar + Footer, không Header. Trang xác thực dùng layout riêng.
- Chat chuyển thành widget nổi trên mọi trang sau đăng nhập: GET `/chat/conversations`, GET `/chat/{id}/messages`, POST `/chat/{id}/send-ajax`, GET `/chat` chuyển về `/marketplace?chat=open` để giữ tương thích link cũ.
- Bộ lọc kỹ năng sử dụng danh sách `Skill` hiện có và cho phép tìm trong danh sách; minPrice/maxPrice là 2 slider giá được kiểm tra giới hạn trong giao diện và service.
- Trang Đơn hàng có modal tiến độ/chi tiết và POST cập nhật trạng thái thông qua AJAX; Review và Complaint có trang/form tách biệt. Form thanh toán vẫn là **mô phỏng**, chưa có tích hợp cổng thanh toán thực.
- Tệp `UI_CHANGES.md`: danh sách tệp sửa cùng hướng dẫn đối chiếu mục A-G.


## Escrow ví nội bộ (demo)

- Customer đặt Provider: hệ thống kiểm tra số dư ví khả dụng và khóa toàn bộ giá trị đơn; thiếu tiền thì chặn đặt và hướng dẫn nạp ví demo.
- Provider phải nhận yêu cầu; khi cả Customer và Provider xác nhận bắt đầu thì đơn mới chuyển sang đang thực hiện. Khoản 1/3 được ghi nhận là mốc/giá trị tạm tính, không giải ngân trước, phù hợp quy tắc chỉ quyết toán khi hoàn thành.
- Provider báo hoàn thành → Customer nghiệm thu → OTP → chuyển tiền từ khoản đang khóa sang ví Provider.
- Customer hủy trước lúc bắt đầu: mở khóa toàn bộ. Customer hủy sau khi bắt đầu: Provider đề xuất phí phần việc đã làm, Customer chấp nhận thì thanh toán phí đó và trả phần còn lại. Provider hủy: hoàn 100% khoản đang khóa.
- Nạp ví hiện là **nạp demo nội bộ**, không kết nối ngân hàng/VNPay và không phát sinh tiền thật. Khi chưa cấu hình SMTP, OTP demo có thể hiển thị trong giao diện và được ghi log; không dùng cơ chế này trong production.
- Biểu đồ Admin lọc theo ngày và thể hiện số đơn/doanh thu quyết toán; ví Provider liệt kê mã đơn, dự án, khoản ký quỹ, số tiền đã nhận và ngày quyết toán.

## Chức năng được bổ sung trong bản hoàn thiện tiếp theo

- Xác nhận email khi đăng ký: token 24 giờ, chỉ đăng nhập sau xác nhận; Gmail SMTP dùng `SKILLSHARE_MAIL_USERNAME` và `SKILLSHARE_MAIL_PASSWORD`.
- Khôi phục mật khẩu qua liên kết email có hạn 30 phút, token dùng một lần.
- Danh mục dịch vụ đã xuất bản (`/services`) và danh sách đã chọn (`/cart`) hỗ trợ thêm/xóa/cập nhật số lượng và tổng tạm tính. Checkout/thanh toán nhiều dịch vụ không bật theo phạm vi loại trừ thanh toán.
- Provider gợi ý dựa trên kỹ năng ưu tiên trong hồ sơ Customer; dịch vụ phổ biến thống kê theo đơn không bị hủy từ `rental_order_items`.
- Chi tiết phạm vi và giới hạn kiểm thử: [`FUNCTIONS_COMPLETION_STATUS.md`](FUNCTIONS_COMPLETION_STATUS.md).
