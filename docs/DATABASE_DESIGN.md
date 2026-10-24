# Thiết kế Database chuẩn — SkillShare / Skill Rental

> Phiên bản: 3.0 — bổ sung ví nội bộ/Escrow cho luồng booking, nghiệm thu và quyết toán.  
> Hệ quản trị đích: MySQL 8.0.16+ (InnoDB, `utf8mb4`).  
> Script chính: `src/main/resources/db/schema-mysql.sql`  
> Migration: `src/main/resources/db/migration-v1-to-v2.sql` rồi `src/main/resources/db/migration-v2-to-v3-escrow.sql` cho database v1; database v2 chỉ cần migration v2→v3.

## 1. Nguyên tắc thiết kế

1. Một schema chuẩn duy nhất bao phủ các chức năng bắt buộc và tự chọn đã liệt kê trong kế hoạch nhóm.
2. Giữ lại các bảng/entity đang được source sử dụng (`users`, `provider_profiles`, `service_requests`, `applies`, `bookings`, `rental_orders`, `payments`, `reviews`, `complaints`, `conversations`, `messages`) để không phá vỡ luồng hiện hành.
3. Chuẩn hóa phần catalogue/cart/order-items để có thể hỗ trợ provider quản lý dịch vụ, tìm kiếm theo giá/kỹ năng, giỏ nhiều dịch vụ và thống kê số lượt đặt.
4. Dữ liệu lịch sử giao dịch dùng giá/tên snapshot tại thời điểm đặt, tránh thay đổi giá dịch vụ làm đổi lịch sử đơn cũ.
5. Dùng foreign key, unique key, CHECK và index để bảo vệ toàn vẹn dữ liệu. Quy tắc liên bảng như “provider_id phải là tài khoản role PROVIDER”, tổng tiền đơn hàng, giới hạn hoàn tiền theo số tiền đã thanh toán và chỉ một giỏ ACTIVE/customer phải được kiểm tra thêm trong Service/transaction vì CHECK không thể so sánh an toàn giữa nhiều bảng.
6. Không xóa vật lý dịch vụ/tài khoản đã được tham chiếu trong giao dịch; chuyển trạng thái sang `ARCHIVED`, `SUSPENDED` hoặc trạng thái phù hợp.

## 2. Cách chạy

### Database mới / rỗng

```bash
mysql -u root -p < src/main/resources/db/schema-mysql.sql
```

Script tạo database `skill_rental`. Sau đó cấu hình `DB_USERNAME`/`DB_PASSWORD` và chạy ứng dụng với profile MySQL:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Profile `mysql` dùng `spring.jpa.hibernate.ddl-auto=validate`: Hibernate chỉ kiểm tra mapping, không tự sửa schema. H2 mặc định trong `application.properties` vẫn tiếp tục được dùng cho phát triển cục bộ; bảng H2 được tạo từ entity JPA.

### Database đang dùng schema 1.0

Không chạy lại file `schema-mysql.sql` với kỳ vọng nó tự thêm cột vào bảng cũ. Hãy sao lưu, sau đó chạy:

```bash
mysql -u root -p skill_rental < src/main/resources/db/migration-v1-to-v2.sql
```

Migration chỉ dành cho schema cũ đã tạo từ bộ SQL v1. Kiểm tra trước các unique key nếu database thật có dữ liệu trùng. Không chạy migration lần thứ hai nếu đã nâng cấp thành công.

### Database đã ở schema v2

Sao lưu database, sau đó chạy `src/main/resources/db/migration-v2-to-v3-escrow.sql` một lần. Migration này thêm các cột quy trình Escrow, tăng độ dài cột trạng thái đơn lên 50 ký tự, và tạo hai bảng `user_wallets`, `wallet_transactions`.

## 3. Danh sách 26 bảng

| Nhóm | Bảng | Vai trò |
|---|---|---|
| Tài khoản | `users` | Thông tin tài khoản, role, trạng thái, xác minh email và reset mật khẩu |
| Đăng nhập xã hội | `oauth_identities` | Liên kết tài khoản nội bộ với subject ID do Google/OAuth provider cấp |
| Hồ sơ | `customer_profiles` | Thông tin mở rộng của khách hàng |
| Hồ sơ | `provider_profiles` | Headline, giá giờ, rating, kỹ năng và trạng thái provider |
| Kỹ năng | `skills` | Danh mục kỹ năng chuẩn hóa |
| Kỹ năng | `provider_skills` | Quan hệ N-N provider profile và kỹ năng |
| Catalogue | `services` | Dịch vụ do provider tạo, giá, loại giá, mô tả, trạng thái và xếp hạng |
| Catalogue | `service_skills` | Quan hệ N-N dịch vụ và kỹ năng |
| Yêu cầu | `service_requests` | Yêu cầu do khách hàng đăng theo luồng hiện có |
| Yêu cầu | `service_request_skills` | Kỹ năng yêu cầu đã chuẩn hóa; `required_skills` dạng text vẫn giữ để tương thích form cũ |
| Yêu cầu | `applies` | Đề xuất/báo giá của provider cho một yêu cầu |
| Giỏ hàng | `carts` | Giỏ của khách hàng, gồm trạng thái ACTIVE/CHECKED_OUT/ABANDONED |
| Giỏ hàng | `cart_items` | Dịch vụ trong giỏ, số lượng và snapshot giá |
| Booking | `bookings` | Lịch hẹn cụ thể, customer/provider, ngày giờ, trạng thái |
| Đơn hàng | `rental_orders` | Tổng tiền, giảm giá, trạng thái và lịch sử hoàn tất/hủy |
| Đơn hàng | `rental_order_items` | Từng dịch vụ trong đơn, provider, tên/giá snapshot, booking tương ứng |
| Đơn hàng | `rental_order_status_history` | Nhật ký chuyển trạng thái đơn hàng và tác nhân thay đổi |
| Thanh toán | `payments` | Trạng thái tổng hợp của thanh toán, phương thức, gateway và mã giao dịch gần nhất |
| Thanh toán | `payment_attempts` | Lịch sử từng lần khởi tạo thanh toán, reference/idempotency, mã phản hồi và lỗi gateway |
| Hoàn tiền | `refunds` | Số tiền, lý do, người yêu cầu/duyệt, trạng thái và mã hoàn tiền gateway |
| Ví | `user_wallets` | Số dư khả dụng và số dư ký quỹ được khóa theo từng tài khoản |
| Ví | `wallet_transactions` | Sổ cái biến động ví theo từng đơn/dự án, gồm khóa, hoàn, nạp demo và quyết toán |
| Đánh giá | `reviews` | Đánh giá đơn hàng/provider và tùy chọn liên kết dịch vụ |
| Báo cáo | `complaints` | Báo cáo đơn hàng, dịch vụ hoặc người dùng; người gửi/người bị báo cáo/người xử lý |
| Chat | `conversations` | Hội thoại customer-provider |
| Chat | `messages` | Tin nhắn, người gửi và thời gian gửi |

## 4. Quan hệ nghiệp vụ chính

- `users` 1–0..1 `customer_profiles` / `provider_profiles`.
- `provider_profiles` N–N `skills` qua `provider_skills`.
- `users(provider)` 1–N `services`; `services` N–N `skills` qua `service_skills`.
- `users(customer)` 1–N `carts`; `carts` 1–N `cart_items`; mỗi `cart_item` trỏ đến một `services`.
- `users(customer/provider)` 1–N `bookings`. `bookings.service_id` là nullable để giữ tương thích các bản ghi cũ vốn chỉ lưu `service_name`.
- `rental_orders` 1–N `rental_order_items`. Mỗi item lưu provider riêng nên một đơn giỏ có thể chứa dịch vụ từ nhiều provider. Với đơn một booking cũ, `rental_orders.booking_id` và `provider_id` tiếp tục được hỗ trợ; với đơn nhiều dịch vụ, dùng `rental_order_items` và để trường shortcut `rental_orders.provider_id` rỗng khi cần.
- `users` 1–0..1 `user_wallets`; `user_wallets` 1–N `wallet_transactions`; mỗi giao dịch ví có thể liên kết `rental_orders` để đối soát theo dự án.
- `rental_orders` 1–0..1 `payments` (bản ghi tổng hợp cho tương thích entity hiện có); `payments` 1–N `payment_attempts` để theo dõi nhiều lần thử qua gateway. `refunds` 1–N theo payment để lưu các yêu cầu hoàn tiền từng phần/hoàn nhiều lần.
- `rental_orders` 1–0..1 `reviews`; `reviews.service_id` cho phép xếp hạng theo dịch vụ khi có catalogue.
- `complaints.reporter_id` và `reported_user_id` cho phép báo cáo theo cả hai chiều; `customer_id` vẫn được giữ nullable để tương thích entity luồng khiếu nại đơn hàng hiện có.
- `oauth_identities` tách riêng định danh bên ngoài, không lưu access token hay client secret trong database.

## 5. Coverage theo kế hoạch nhóm

| Chức năng | Bảng chính | Ghi chú triển khai |
|---|---|---|
| Đăng ký / xác nhận email | `users` | Lưu cờ xác minh, token, hạn token; cần AuthService tạo/gửi/xác minh token |
| Login/logout | `users`, Spring Security | Đăng xuất thường do session/security xử lý, không cần bảng riêng |
| Quên/đặt lại mật khẩu | `users` | `reset_token` và `reset_token_expires_at`; Service phải kiểm tra hết hạn và xóa token sau dùng |
| Google login | `oauth_identities`, `users` | OAuth flow/config/handler cần được cài đặt ở tầng Security; database chỉ lưu mapping bền vững |
| Xem/CRUD service-provider | `services`, `provider_profiles`, `skills`, `service_skills` | Có thể lọc/sắp xếp giá, trạng thái, kỹ năng, rating |
| Giỏ nhiều dịch vụ | `carts`, `cart_items` | Một order có thể chứa nhiều dòng dịch vụ |
| Booking/đơn hàng | `bookings`, `rental_orders`, `rental_order_items` | Hỗ trợ cả luồng booking đơn hiện tại và luồng cart mới |
| Thanh toán / ký quỹ | `user_wallets`, `wallet_transactions`, `rental_orders` | Khóa toàn bộ tiền khi gửi yêu cầu; Provider nhận việc; hai bên xác nhận bắt đầu; Provider báo hoàn thành; Customer nghiệm thu + OTP trước khi quyết toán. Ví/nạp/rút trong bản này là demo nội bộ. |
| Lịch sử đơn | `bookings`, `rental_orders`, `rental_order_items`, `payments` | Phải lọc theo người đăng nhập và kiểm tra quyền sở hữu |
| Biểu đồ thời gian / tròn | `rental_orders`, `payments`, `rental_order_items` | Định nghĩa rõ thống kê theo doanh thu đã thanh toán hay tổng giá trị đơn |
| Admin quản lý user/provider/service/order | `users`, `provider_profiles`, `services`, `rental_orders` | Cần kiểm tra role tại Security/Service |
| Provider quản lý dịch vụ | `services`, `service_skills` | Bắt buộc ràng buộc provider sở hữu dịch vụ trước khi sửa/xóa |
| Provider quản lý booking/đơn | `bookings`, `rental_orders`, `rental_order_items`, `rental_order_status_history` | Ghi lại trạng thái mới và người thay đổi |
| Provider doanh thu | `user_wallets`, `wallet_transactions`, `rental_orders` | Chỉ ghi thu nhập sau quyết toán; sổ cái chỉ rõ mã đơn và dự án. Ví nội bộ hiện là demo, chưa kết nối ngân hàng. |
| Filter/sort/recommend | `services`, `service_skills`, `reviews`, `provider_profiles` | Gợi ý là logic truy vấn; database cung cấp tín hiệu |
| Dịch vụ đặt nhiều | `rental_order_items` | Đếm item/dịch vụ từ đơn hợp lệ đã thanh toán theo quy tắc nhóm thống nhất |
| Hủy đơn | `rental_orders`, `bookings`, `rental_order_status_history` | Lưu lý do/người hủy/thời điểm; hoàn tiền phải là quy trình riêng |
| Review | `reviews`, `rental_orders`, `services` | Service phải xác nhận order thuộc customer và đã hoàn tất |
| Report customer/provider | `complaints`, `users` | Lưu reporter, target, nội dung, status và người xử lý |
| Refund | `refunds`, `payments`, `rental_orders` | Service phải kiểm soát số tiền hoàn cộng dồn không vượt tổng đã thanh toán |

## 6. Enum / trạng thái chuẩn

- `Role`: `CUSTOMER`, `PROVIDER`, `ADMIN`.
- `AccountStatus`: `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `LOCKED`, `DELETED`. Bản source hiện tại mặc định `ACTIVE` để giữ luồng đăng ký cũ; khi tích hợp gửi/xác minh email, `AuthService` phải gán `PENDING_VERIFICATION` trước khi tạo tài khoản mới.
- `ServicePricingType`: `HOURLY`, `FIXED`.
- `ServiceStatus`: `DRAFT`, `PUBLISHED`, `PAUSED`, `ARCHIVED`.
- `CartStatus`: `ACTIVE`, `CHECKED_OUT`, `ABANDONED`.
- `RequestStatus`: `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- `ApplyStatus`: `PENDING`, `ACCEPTED`, `REJECTED`.
- `BookingStatus`: `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- `OrderStatus`: `PENDING_PROVIDER_ACCEPTANCE`, `PENDING_START_CONFIRMATION`, `PROCESSING`, `PENDING_CUSTOMER_ACCEPTANCE`, `PENDING_PAYMENT_OTP`, `CANCELLATION_REQUESTED_BY_CUSTOMER`, `CANCELLATION_COMPENSATION_PENDING_CUSTOMER`, `COMPLETED`, `CANCELLED`; giữ `PENDING_PAYMENT` và `PAID` để tương thích dữ liệu cũ.
- `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `PARTIALLY_REFUNDED`, `REFUNDED`.
- `PaymentAttemptStatus`: `CREATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
- `RefundStatus`: `REQUESTED`, `APPROVED`, `REJECTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
- `ComplaintType`: `ORDER`, `USER`, `PROVIDER`, `SERVICE`, `PAYMENT`, `OTHER`.
- `ComplaintStatus`: `OPEN`, `PROCESSING`, `RESOLVED`, `REJECTED`.

Không tự ý đổi chữ hoa/thường hoặc đặt enum khác nhau cho cùng một trạng thái giữa Java, SQL và dữ liệu demo. Trạng thái dài nhất hiện dài 42 ký tự, nên cột `rental_orders.status` được đặt thành `VARCHAR(50)`.

## 7. Dữ liệu demo cho các bảng

Khi ứng dụng khởi động trên database rỗng, `DemoDataConfig` tạo dữ liệu gốc qua JPA rồi thực thi `db/seed-expand-demo-data.sql`. Với MySQL Workbench/CLI, chạy `schema-mysql.sql` và `seed-demo-data.sql` trên database mới. Không chạy seed lần hai.

Ba tài khoản đăng nhập demo được giữ nguyên: `customer@skillrental.vn`, `provider1@skillrental.vn`, `admin@skillrental.vn`; mật khẩu cho cả ba là `123456`. Để đáp ứng yêu cầu tối thiểu 50 bản ghi ở tất cả bảng, seed thêm 98 tài khoản customer/provider thử nghiệm trong trạng thái `LOCKED`, dùng email miền `.invalid` và mật khẩu băm ngẫu nhiên. Đây là dữ liệu liên kết để tạo hồ sơ và giao dịch mẫu, không phải tài khoản demo sử dụng đăng nhập.

Số bản ghi dự kiến sau seed:

| Bảng | Bản ghi |
|---|---:|
| `users` | 101 |
| `oauth_identities` | 50 |
| `customer_profiles` | 50 |
| `provider_profiles` | 50 |
| `skills` | 50 |
| `provider_skills` | 54 |
| `services` | 50 |
| `service_skills` | 54 |
| `service_requests` | 50 |
| `service_request_skills` | 52 |
| `applies` | 50 |
| `carts` | 50 |
| `cart_items` | 51 |
| `bookings` | 53 |
| `rental_orders` | 53 |
| `rental_order_items` | 53 |
| `rental_order_status_history` | 56 |
| `payments` | 53 |
| `payment_attempts` | 53 |
| `refunds` | 50 |
| `reviews` | 50 |
| `complaints` | 50 |
| `conversations` | 50 |
| `messages` | 52 |
| `user_wallets` | 101 |
| `wallet_transactions` | 55 |

Gateway transaction IDs, refund IDs and Google subject values beginning with `DEMO-` are simulated placeholders, not real transactions or Google authorizations.

## 8. Tương thích với source hiện tại và phạm vi còn cần làm

- Entity hiện có tiếp tục giữ đường đi legacy: `Booking.serviceName`, `RentalOrder.booking`, `RentalOrder.provider` vẫn tồn tại; các cột liên quan được để nullable trong schema để luồng giỏ hàng mới có thể sử dụng order items.
- Đã bổ sung entity/repository nền tảng cho `ServiceCatalog`, cart/cart items, OAuth identity, order items, refund và order status history để H2/JPA có thể tạo mapping tương ứng.
- `schema-mysql.sql` đã bao gồm các cột xác minh email, OAuth, dịch vụ, giỏ hàng, nhiều dòng đơn, trạng thái/hủy, giao dịch và hoàn tiền.
- Đây là thiết kế dữ liệu và lớp mapping; không đồng nghĩa mọi chức năng đã có đầy đủ Controller/Service/UI. Xác nhận email, Google OAuth, checkout giỏ nhiều dịch vụ, callback thanh toán, hoàn tiền thực và báo cáo hai chiều vẫn cần được triển khai ở tầng nghiệp vụ.
- Trong mô hình nhiều provider, `rental_orders.provider_id` chỉ là shortcut tương thích đơn một provider; các báo cáo doanh thu mới phải tổng hợp qua `rental_order_items.provider_id`.
- Script canonical dành cho MySQL 8.0.16+. Hãy chạy test `mvn test` và khởi động profile `mysql` trên database rỗng/đã migrate trước khi dùng demo; trong phiên xử lý này môi trường không có lệnh Maven/MySQL để chạy kiểm thử tích hợp thực tế.


### MySQL Error 3823 compatibility note

The `complaints` table retains `ck_complaints_reporter`, but the `fk_complaints_customer` and `fk_complaints_reporter` definitions omit explicit `ON UPDATE`/`ON DELETE` clauses because MySQL prohibits referential-action clauses on columns referenced by a CHECK constraint. With InnoDB, the omitted action defaults to `NO ACTION` (RESTRICT-equivalent), so referenced `users.id` values cannot be updated or deleted while complaints point to them.
