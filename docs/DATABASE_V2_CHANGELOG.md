# Database v2 — danh sách thay đổi

## SQL / tài liệu
- Thay `src/main/resources/db/schema-mysql.sql` bằng schema chuẩn MySQL gồm 24 bảng.
- Thêm `src/main/resources/db/migration-v1-to-v2.sql` để nâng cấp database từ bộ schema MySQL v1 trước đó.
- Cập nhật ERD dạng PNG/SVG, Mermaid và Graphviz DOT.
- Thêm tài liệu thiết kế, mapping  chức năng–bảng và các quy tắc cần thực hiện tại Service layer.

## Entity và repository mới
- `ServiceCatalog`, `ServicePricingType`, `ServiceStatus` → catalogue, giá, trạng thái dịch vụ, kỹ năng dịch vụ.
- `Cart`, `CartItem`, `CartStatus` → giỏ hàng và các dòng dịch vụ với snapshot giá.
- `OAuthIdentity` → mapping Google/OAuth ID với tài khoản nội bộ.
- `RentalOrderItem` → nhiều dịch vụ trong một đơn và provider riêng trên từng dòng.
- `PaymentAttempt`, `PaymentAttemptStatus` → lịch sử từng lần gọi gateway, reference/idempotency và lỗi thanh toán.
- `Refund`, `RefundStatus` → yêu cầu và kết quả hoàn tiền.
- `RentalOrderStatusHistory` → nhật ký thay đổi trạng thái đơn hàng.
- Bổ sung các field xác minh email, thời hạn reset token, trạng thái tài khoản, hủy booking/order và complaint reporter/target.
- Bổ sung repository JPA cho catalogue, cart, OAuth identity, order items, payment attempts, refunds và lịch sử trạng thái.

## Tương thích và giới hạn
- Luồng đặt trực tiếp hiện tại tiếp tục dùng các trường `Booking.serviceName`, `RentalOrder.booking` và `RentalOrder.provider`.
- Những trường legacy trong `rental_orders` được để nullable khi cần cho đơn nhiều dịch vụ/provider; code xử lý giỏ hàng mới phải dùng `rental_order_items` thay vì chỉ đọc `order.booking`/`order.provider`.
- Thiết kế DB/entity không thay thế việc cài đặt email transport, OAuth Security handler, checkout cart, payment gateway callback hoặc quy trình hoàn tiền. Những luồng nghiệp vụ này cần được tích hợp trong các Service/Controller tương ứng trước khi tuyên bố feature hoàn tất.
- Đã kiểm tra tĩnh số bảng (24), mục tiêu foreign key đều tồn tại, tất cả entity/join table trong source đều có trong schema và dấu ngoặc Java cân bằng. Không thể chạy Maven/MySQL integration test trong môi trường tạo artifact này vì không có lệnh Maven/MySQL cài đặt.


## Bổ sung dữ liệu demo (seed)
- Giữ nguyên ba tài khoản đăng nhập chính: `customer@skillrental.vn`, `provider1@skillrental.vn`, `admin@skillrental.vn` (mật khẩu `123456`).
- Tạo thêm 98 tài khoản dữ liệu giả lập ở trạng thái `LOCKED` để có đủ quan hệ một-một cho 50 customer profiles và 50 provider profiles mà không thay đổi ba thông tin đăng nhập chính.
- Mở rộng `DemoDataConfig` và thêm `seed-expand-demo-data.sql` để tất cả 24 bảng có ít nhất 50 bản ghi.
- `seed-demo-data.sql` là bản SQL đầy đủ cho MySQL mới; thêm `verify-seed-counts.sql` để kiểm tra số lượng từng bảng.
- Các tài khoản giả lập dùng email miền `.invalid`, mật khẩu băm ngẫu nhiên và trạng thái khóa; các OAuth/gateway/refund identifiers là placeholder minh họa, không phải giao dịch thật.
