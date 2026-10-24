# SkillShare — dữ liệu mẫu mở rộng

## Mục tiêu

- Mỗi bảng có ít nhất 50 dòng dữ liệu mẫu sau khi chạy schema + seed trên database mới hoặc sau khi ứng dụng tự tạo bộ seed gốc.
- Ba tài khoản đăng nhập chính được giữ nguyên: `customer@skillrental.vn`, `provider1@skillrental.vn`, `admin@skillrental.vn`; mật khẩu `123456`.
- Để thỏa điều kiện tối thiểu 50 dòng cho `customer_profiles`, `provider_profiles` và các bảng có khóa ngoại tới `users`, có bổ sung 98 tài khoản dữ liệu giả lập ở trạng thái `LOCKED` (49 customer, 49 provider). Email dùng miền `.invalid`, không phải tài khoản thật và không dùng đăng nhập. Ba tài khoản chính vẫn là các tài khoản hoạt động được cung cấp cho demo.
- Các mã giao dịch thanh toán, OAuth và hoàn tiền đều là placeholder demo, không phát sinh giao dịch thật.

## Số dòng sau khi seed

Tổng cộng **1.441 bản ghi trên 26 bảng**; bảng ít nhất có 50 bản ghi. `users` có 101 dòng vì giữ 3 tài khoản chính và thêm 98 tài khoản thử nghiệm bị khóa để thỏa các quan hệ hồ sơ một-một.

| Bảng | Số dòng dự kiến |
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

## Cách sử dụng

### MySQL
Trên database mới, chạy `schema-mysql.sql` trước rồi chạy `seed-demo-data.sql`. Script seed đầy đủ bao gồm các dòng gốc và dòng mở rộng; chỉ chạy một lần.

### Spring Boot/H2 hoặc MySQL trống có schema sẵn
`DemoDataConfig` tạo ba tài khoản chính và bộ dữ liệu gốc, sau đó chạy `seed-expand-demo-data.sql` để bổ sung các dòng còn thiếu. Bộ mở rộng chỉ chạy khi database chưa có người dùng (không chạy trên database đã có dữ liệu).

## Lưu ý

- Không dùng dữ liệu seed giả lập làm dữ liệu nghiệp vụ thật.
- Nếu chạy seed trên database đã có dữ liệu, hãy sao lưu và reset database demo trước; không chạy đi chạy lại để tránh trùng khóa.
- Các tài khoản test ở trạng thái `LOCKED` và email có miền `.invalid`.
