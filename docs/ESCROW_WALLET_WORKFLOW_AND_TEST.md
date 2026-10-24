# SkillShare — Quy trình Escrow, ví nội bộ và kiểm thử

## 1. Phạm vi

Luồng thanh toán của bản này là **ví nội bộ dùng để mô phỏng ký quỹ và quyết toán giữa Customer/Provider**. Nạp ví là nạp demo, không kết nối ngân hàng hoặc cổng thanh toán và không tạo tiền thật. Không dùng số dư demo để vận hành production.

## 2. Trạng thái đơn và quyền thao tác

| Trạng thái | Ai thao tác chính | Ý nghĩa |
|---|---|---|
| `PENDING_PROVIDER_ACCEPTANCE` | Provider | Customer đã gửi yêu cầu; tiền tổng giá trị hợp đồng được khóa trong ví Customer. Provider có thể nhận hoặc từ chối; từ chối sẽ mở khóa toàn bộ khoản ký quỹ. |
| `PENDING_START_CONFIRMATION` | Customer và Provider | Provider đã nhận; mỗi bên xác nhận bắt đầu ở tài khoản của mình. |
| `PROCESSING` | Provider | Chỉ vào trạng thái này khi cả hai bên đều xác nhận bắt đầu. |
| `PENDING_CUSTOMER_ACCEPTANCE` | Customer | Provider đã báo hoàn thành; Customer kiểm tra và nghiệm thu hoặc yêu cầu hủy theo quy định. |
| `PENDING_PAYMENT_OTP` | Customer | Customer đã nghiệm thu; OTP được gửi tới email hoặc hiển thị cho demo local nếu SMTP chưa cấu hình. |
| `COMPLETED` | Hệ thống | OTP hợp lệ; khoản ký quỹ được chuyển sang ví Provider. |
| `CANCELLATION_REQUESTED_BY_CUSTOMER` | Provider | Customer yêu cầu hủy sau khi hai bên bắt đầu; Provider gửi đề xuất chi phí phần việc đã làm. |
| `CANCELLATION_COMPENSATION_PENDING_CUSTOMER` | Customer | Customer chấp nhận phí thì quyết toán phần phí và mở khóa phần dư; từ chối thì đơn trở lại đang thực hiện. |
| `CANCELLED` | Hệ thống | Đơn hủy. Hủy trước khi bắt đầu hoặc Provider hủy: mở khóa toàn bộ tiền đang giữ. |

### Nguyên tắc tiền

- Khi tạo booking, hệ thống kiểm tra ví khả dụng và khóa toàn bộ giá trị hợp đồng. Nếu thiếu tiền, giao dịch đặt bị rollback và Customer được hướng dẫn tới trang ví.
- Khi hai bên xác nhận bắt đầu, hệ thống ghi nhận giá trị mốc 1/3 (`start_deposit_amount`) nhưng **không giải ngân**. Đây là cách tuân theo yêu cầu cuối cùng rằng không thanh toán cho Provider ngay khi đặt và chỉ quyết toán sau khi hoàn thành, nghiệm thu và OTP.
- Tiền chuyển sang ví Provider sau OTP; phần ký quỹ còn dư (nếu có thỏa thuận phí hủy) quay về ví Customer.
- Ví nội bộ không đồng nghĩa tài khoản ngân hàng, VNPay hay MoMo. Chức năng rút tiền hiện vẫn là mô phỏng.

## 3. Chạy bằng H2 local

1. Mở thư mục `skill-rental` bằng IntelliJ IDEA và Reload Maven.
2. Chạy `com.skillrental.SkillRentalApplication` hoặc chạy `mvn spring-boot:run` từ terminal có Maven.
3. Mở `http://localhost:8080`.

Tài khoản demo:

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Customer | `customer@skillrental.vn` | `123456` |
| Provider | `provider1@skillrental.vn` | `123456` |
| Admin | `admin@skillrental.vn` | `123456` |

### Kiểm thử hoàn thành thành công

1. Đăng nhập Customer, vào Marketplace và đặt một Provider.
2. Nếu số dư khả dụng thiếu, xác nhận hệ thống chuyển đến trang ví và không tạo đơn mới. Cộng số dư demo nếu cần; ghi nhớ thao tác này không nạp tiền ngân hàng.
3. Sau khi đủ số dư, đặt lại. Kiểm tra ví Customer giảm phần khả dụng và tăng khoản khóa; đơn có trạng thái `Chờ Provider xác nhận`.
4. Đăng nhập Provider, mở `/provider/bookings`, bấm **Nhận yêu cầu**. Nếu không thể nhận, bấm **Từ chối & hoàn tiền**; đơn bị hủy và toàn bộ khoản ký quỹ được mở khóa cho Customer.
5. Customer và Provider lần lượt bấm **Xác nhận bắt đầu** từ tài khoản của mình. Trạng thái chỉ thành `Đang thực hiện` sau xác nhận của cả hai.
6. Provider bấm **Báo hoàn thành**. Customer mở tiến độ đơn, bấm **Nghiệm thu và nhận OTP**.
7. Với H2 local chưa cấu hình SMTP, mã OTP demo xuất hiện một lần trên trang tiến độ; nhập đủ 6 chữ số. Sau OTP hợp lệ, đơn thành `Đã hoàn thành và quyết toán`, khoản khóa giảm, số dư Provider tăng.
8. Đăng nhập Provider → **Ví & Thu nhập**. Đối chiếu mã đơn, tên dự án, giá trị hợp đồng, tiền đã nhận, tiền còn khóa và ngày quyết toán.

### Kiểm thử hủy / hoàn tiền

- **Customer hủy trước khi Provider nhận hoặc trước khi cả hai xác nhận bắt đầu:** toàn bộ khoản khóa mở lại cho Customer.
- **Customer hủy sau khi công việc bắt đầu:** trạng thái chuyển sang chờ Provider đề xuất chi phí. Provider gửi số tiền và lý do; Customer chấp nhận thì Provider nhận đúng số tiền đã chấp nhận và phần còn lại được mở khóa. Nếu Customer từ chối, đơn trở về đang thực hiện.
- **Provider hủy:** toàn bộ khoản ký quỹ chưa quyết toán được mở lại cho Customer.
- **Kiểm tra chống lách:** sau khi Customer đã yêu cầu hủy, gửi POST hủy lần nữa phải bị chặn; không được mở khóa toàn bộ bằng cách gọi lặp endpoint.
- **OTP sai:** không quyết toán; lần nhập sai được ghi nhận và giới hạn ở 5 lần. OTP hết hạn thì dùng nút gửi lại; gửi lại mã không đặt lại bộ đếm nhập sai. Sau 5 lần sai, cần hỗ trợ xác minh.

### Kiểm thử Admin / biểu đồ

1. Đăng nhập Admin → `/admin/statistics`.
2. Chọn `Từ ngày` và `Đến ngày` rồi bấm **Xem giai đoạn**.
3. Kiểm tra KPI tổng đơn/đơn hoàn thành/doanh thu quyết toán và biểu đồ cột (số đơn + tiền đã quyết toán).
4. Kiểm tra biểu đồ tròn/legend thể hiện các trạng thái hiện tại của đơn được tạo trong giai đoạn.
5. Đổi sang một khoảng không có đơn: trang vẫn phải hiển thị, biểu đồ tròn có nền trống và thông báo không có dữ liệu.

## 4. MySQL

- Database mới: chạy `src/main/resources/db/schema-mysql.sql`, sau đó nếu cần dữ liệu demo thì chạy `src/main/resources/db/seed-demo-data.sql`. Script seed v3 tạo ví demo, ghi sổ quyết toán theo từng dự án và khóa tiền cho các đơn đang hoạt động.
- Database schema v2 đã tồn tại: sao lưu trước, chạy `src/main/resources/db/migration-v2-to-v3-escrow.sql`, rồi bật profile `mysql`.
- Database v1: chạy migration v1→v2 trước, sau đó v2→v3.
- Nên kiểm thử trên database mới hoặc bản sao lưu. Migration không tự biến các khoản thanh toán cũ thành giao dịch Escrow thực.

## 5. Email và môi trường production

- Email OTP cần cấu hình SMTP bằng biến môi trường/thông tin của nhà cung cấp email.
- Ở chế độ H2 local, nếu SMTP chưa có, ứng dụng hiển thị OTP demo để kiểm thử.
- `application-prod.properties` tắt cộng ví demo và không cho hiển thị OTP demo. Khi bật profile `prod`, cần có cấu hình email thật và một cổng nạp tiền được xác minh trước khi dùng cho tiền thật.
- Chưa có tích hợp nạp tiền/rút tiền ngân hàng thật, VNPay hoặc MoMo trong luồng ví nội bộ này.

## 6. Giới hạn xác minh trong gói này

Các file Java, XML và HTML đã được rà tĩnh. Môi trường đóng gói không có Maven và cache dependency rỗng, vì vậy chưa chạy được `mvn test` hoặc boot integration test; cần chạy lại trong IntelliJ/Maven trên máy của bạn trước khi đánh dấu hoàn tất 100%.
