# Cấu hình email OTP trong IntelliJ

Thông báo “Chưa cấu hình SMTP” xuất hiện khi ứng dụng chưa nhận được tài khoản hoặc mật khẩu SMTP. File nguồn không chứa thông tin đăng nhập hộp thư thật. Không thể gửi OTP tới email thật chỉ bằng việc ẩn thông báo lỗi.

## Cách 1 — File cấu hình riêng (dễ dùng khi chạy IntelliJ)

1. Mở đúng thư mục `skill-rental`, nơi chứa `pom.xml`.
2. Sao chép `config/mail.properties` thành `config/mail.properties`.
3. Trong file mới, thay `YOUR_GMAIL_ADDRESS` bằng địa chỉ Gmail gửi thư và `YOUR_GOOGLE_APP_PASSWORD` bằng Mật khẩu ứng dụng của chính hộp thư đó. Nhập liền 16 ký tự, bỏ các khoảng trắng phân nhóm. Không dùng mật khẩu đăng nhập Google.
4. Trong IntelliJ → Run → Edit Configurations → chọn cấu hình Spring Boot → Working directory: chọn thư mục `skill-rental` chứa `pom.xml`. Nếu chạy Maven trong IntelliJ, working directory của cấu hình Maven cũng phải trỏ về thư mục này.
5. Nếu đã khai báo `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` trong Environment variables, xóa giá trị cũ hoặc cập nhật cho đúng: biến môi trường có thể ghi đè file cấu hình.
6. Khởi động lại ứng dụng. Mở Đăng ký, nhập email nhận khác nếu có, nhấn “Gửi mã”, kiểm tra Hộp thư đến và Spam.

`config/mail.properties` bị Git bỏ qua. Không gửi file này cho người khác và không đưa vào ZIP bàn giao. Không có file này thì ứng dụng vẫn khởi động; gửi OTP chỉ hoạt động sau khi cấu hình hợp lệ.

## Cách 2 — Biến môi trường của cấu hình Run

Không cần tạo file ở cách 1. Trong IntelliJ → Run → Edit Configurations → cấu hình đang chạy → Modify options → Environment variables, thêm:

```text
SKILLSHARE_MAIL_USERNAME=địa_chỉ_Gmail_gửi_thư
SKILLSHARE_MAIL_PASSWORD=mật_khẩu_ứng_dụng_Google
```

Có thể thay SMTP bằng `SKILLSHARE_MAIL_HOST` và `SKILLSHARE_MAIL_PORT` nếu dùng nhà cung cấp khác. Mặc định là Gmail, cổng 587, STARTTLS. Khởi động lại đúng cấu hình Run sau khi thay đổi; biến đặt trong một cửa sổ PowerShell không tự truyền vào IntelliJ đã mở trước đó.

## Mật khẩu ứng dụng Google

Bật Xác minh 2 bước trên tài khoản Google rồi tạo Mật khẩu ứng dụng tại https://myaccount.google.com/apppasswords . Một số tài khoản do tổ chức quản lý hoặc cấu hình bảo mật không cho tạo loại mật khẩu này. Hướng dẫn chính thức: https://support.google.com/accounts/answer/185833?hl=vi . File OAuth `client_secret...json` chỉ phục vụ đăng nhập Google, không thay thế mật khẩu SMTP.

## Phân biệt lỗi

- “Chưa cấu hình SMTP”: tài khoản hoặc mật khẩu rỗng; kiểm tra working directory, tên biến và cấu hình Run đang sử dụng.
- “SMTP không chấp nhận email OTP”: kiểm tra Gmail gửi thư, Mật khẩu ứng dụng, quyền của tài khoản, kết nối mạng và cổng 587. Không dùng mật khẩu tài khoản thường.
- Máy chủ SMTP chấp nhận thư nhưng chưa thấy mail: kiểm tra Spam, địa chỉ nhận và chờ thời gian chuyển phát. SMTP chấp nhận không đồng nghĩa thư đã vào hộp thư đến.

Luồng đăng ký vẫn giữ kiểm tra OTP thật, thời hạn và giới hạn gửi lại; không tự kích hoạt tài khoản và không hiển thị OTP đăng ký trên màn hình/log.
