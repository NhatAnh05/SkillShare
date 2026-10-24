# Thiết lập nạp ví VNPay Sandbox và OTP Gmail thật

## 1. Ba số dư ví Customer
- **Ví tiền gốc** = Ví khả dụng + Tiền đang khóa ký quỹ.
- **Ví khả dụng**: tiền có thể dùng để đặt Provider.
- **Đang khóa ký quỹ**: tiền giữ cho đơn đang chạy.

## 2. Nạp ví qua VNPay Sandbox
1. Đăng ký/đăng nhập cổng merchant thử nghiệm VNPay: https://sandbox.vnpayment.vn/devreg/.
2. Lấy `TmnCode` và `HashSecret` của môi trường Sandbox.
3. Trong IntelliJ IDEA > Run > Edit Configurations > Environment variables, đặt:
   - `VNPAY_TMN_CODE=...`
   - `VNPAY_HASH_SECRET=...`
   - `VNPAY_RETURN_URL=http://localhost:8080/customer/wallet/vnpay/return`
4. Chạy lại ứng dụng, đăng nhập Customer > Ví > Nạp qua VNPay Sandbox.
5. Hoàn tất thanh toán bằng thông tin thẻ thử nghiệm mà VNPay cung cấp trong tài liệu sandbox.
6. Chỉ khi chữ ký, mã giao dịch, trạng thái và số tiền trả về khớp, ví mới được cộng tiền. Nạp ví sandbox không trừ tiền thật.

Lưu ý: localhost chỉ phù hợp cho bài tập/demo. Môi trường thật cần HTTPS, thông tin merchant production và endpoint IPN server-to-server công khai để xác nhận giao dịch; không dùng callback local làm cổng thu tiền production.

## 3. OTP gửi email thật qua Gmail SMTP
OTP được gửi đến địa chỉ email đang lưu trong hồ sơ người dùng (`User.email`). Địa chỉ Gmail cấu hình SMTP là tài khoản gửi thư của ứng dụng, không nhất thiết phải trùng email người dùng.

1. Bật xác minh 2 bước cho tài khoản Gmail dùng gửi thư.
2. Tạo Google App Password; không dùng mật khẩu Gmail thường.
3. Thiết lập biến môi trường:
   - `SKILLSHARE_MAIL_USERNAME=your-sender@gmail.com`
   - `SKILLSHARE_MAIL_PASSWORD=google-app-password`
4. Khởi động lại ứng dụng. Mặc định `skillshare.escrow.show-demo-otp=false`, nên không hiển thị OTP demo nếu email gửi thất bại.
5. Đảm bảo hồ sơ Customer/Provider có email chính xác và có thể nhận thư. Kiểm tra thư Spam/Promotions nếu chưa thấy OTP.

Không commit mật khẩu ứng dụng hoặc khóa VNPay lên Git. Khi thay email trong hồ sơ, cập nhật email chính xác trước khi bắt đầu thao tác yêu cầu OTP.

## 4. Database
Bảng `wallet_topups` lưu giao dịch nạp ví và ngăn cộng tiền trùng cho cùng mã giao dịch. Với database cũ, chạy `src/main/resources/db/migration-v4-to-v5-wallet-topup.sql` trước khi bật `ddl-auto=validate`. Database demo H2 tạo lại bảng tự động từ entity.

## 5. Giới hạn bản demo
Tích hợp trong bản này dùng VNPay Sandbox, không phải cổng thanh toán production. Trước khi thu tiền thật cần cấu hình IPN server-to-server, HTTPS/domain công khai, đối soát merchant và kiểm thử bảo mật độc lập.
