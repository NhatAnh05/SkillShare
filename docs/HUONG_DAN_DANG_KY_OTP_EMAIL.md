# SkillShare — Đăng ký và xác minh email bằng OTP

Đăng ký email/mật khẩu, gửi OTP và hoàn tất xác minh đều diễn ra trên một trang `/register`. Google OAuth2/OIDC không thay đổi.

## Cấu hình Gmail SMTP trong IntelliJ

Trong **Run → Edit Configurations → SkillRentalApplication → Environment variables**, đặt:

- `SKILLSHARE_MAIL_USERNAME`: địa chỉ Gmail gửi thư.
- `SKILLSHARE_MAIL_PASSWORD`: Google App Password (không phải mật khẩu đăng nhập Gmail).

`application.properties` dùng `smtp.gmail.com:587`, SMTP authentication và STARTTLS; các biến trên được ánh xạ lần lượt vào `spring.mail.username` và `spring.mail.password`. Bật xác minh 2 bước và tạo App Password trong tài khoản Google. Không lưu App Password trong source code hoặc chia sẻ log có thông tin xác thực. Khởi động lại ứng dụng sau khi đổi biến môi trường.

Không có credential hợp lệ thì hệ thống không phát hành OTP và không báo gửi thành công. Test dùng mail sender giả lập; cần Gmail thật để xác nhận gửi thư ngoài hệ thống.

## Luồng đăng ký

1. Nhập họ tên, email, mật khẩu, xác nhận mật khẩu; yêu cầu OTP ngay trên form.
2. Backend kiểm tra dữ liệu, không cho gửi đến email đã kích hoạt, tạo hoặc tái sử dụng duy nhất tài khoản `PENDING_VERIFICATION`, rồi gửi OTP tới email đó.
3. OTP được tạo bằng `SecureRandom`, lưu BCrypt hash, hết hạn sau 5 phút và có tối đa 5 lần nhập sai. OTP chỉ được kiểm tra cho email gắn với session sau yêu cầu gửi.
4. Cooldown 60 giây được lưu trong DB và kiểm tra ở backend; tải lại trang tiếp tục hiển thị thời gian còn lại. Gửi thành công mã mới làm mã cũ mất hiệu lực; gửi thất bại giữ mã cũ và không bắt đầu cooldown thành công mới.
5. Khi nhập OTP hợp lệ và nhấn **Đăng ký**, tài khoản chuyển sang `ACTIVE`, `email_verified=true`, thông tin OTP bị xóa và trang chuyển tới `/login`.
6. Nếu đổi email sau khi gửi, mã cũ không xác minh được email mới. Yêu cầu mã cho email mới; tài khoản pending cũ được giữ lại và có thể tiếp tục từ cùng trang đăng ký.

SMTP chấp nhận thư không đảm bảo thư đã đến Inbox; kiểm tra Spam/Junk. Lỗi SMTP hiển thị thông báo có thể thử lại, không đưa credentials hoặc OTP vào log. Đường dẫn cũ `/verify-otp` chuyển hướng an toàn về `/register`.

## MySQL và migration

H2 demo tạo schema từ entity. Với MySQL, từ v6 áp dụng `db/migration-v6-to-v7-registration-otp.sql` để thêm bộ đếm nhập sai và thời điểm gửi OTP. Nếu database đang ở v5, áp dụng migration v5→v6 trước. Sao lưu và xác nhận phiên bản schema; không chạy lại migration đã áp dụng. Migration không xóa tài khoản/dữ liệu hiện tại.

## Kiểm thử

Chạy `mvn test` trong thư mục `skill-rental`. Integration tests mock SMTP và xác nhận hành vi ứng dụng, không chứng minh Gmail thật đã nhận thư.

Để thử gửi thật: cấu hình hai biến môi trường trong IntelliJ Run Configuration, chạy ứng dụng, đăng ký bằng một email có thể truy cập, nhấn **Gửi mã OTP**, kiểm tra Inbox/Spam, nhập mã trong 5 phút và hoàn tất trên chính trang `/register`. Thử gửi lại sau khi bộ đếm 60 giây kết thúc.
