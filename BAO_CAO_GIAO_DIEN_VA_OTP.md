# Cập nhật Header, sidebar, Footer và cấu hình OTP — 02/11/2026

## Bản nền và phạm vi

Sửa trên bản sao của `SkillShare_Moi_Nhat_Da_Sua_Chat_Admin_ThongBao.zip`, SHA-256 `8f24331e7bbde57cd0d422ebb0cafcb77584b51ce0a440d222d863ad9923cc62`. Giữ đủ file của bản nền. Không có migration hoặc thay đổi nghiệp vụ thanh toán/chat.

## Thay đổi

- Header Customer và các trang dùng chung chia thành hai khu vực: thương hiệu/tìm kiếm/tài khoản và hàng điều hướng. Khoảng cách dùng cùng quy tắc; điều hướng trên mobile cuộn trong hàng riêng, vẫn hiện tên mục.
- Header Workspace Provider/Admin tự xuống hàng khi thiếu chỗ, giữ khoảng cách giữa nút và không đè nội dung. Sửa lưới Workspace trên mobile từng làm nội dung rơi vào cột có chiều rộng bằng 0 và lưới Dashboard Provider bị tràn.
- Customer dùng chung danh sách điều hướng giữa Header và sidebar, có cả Tìm Request và Giỏ hàng. Trang hồ sơ, cài đặt và các trang dùng sidebar chung của Provider/Admin tái sử dụng chính sidebar Workspace theo vai trò. Bổ sung liên kết Hồ sơ tài khoản/Cài đặt/Đổi mật khẩu vào sidebar Workspace.
- Trang hồ sơ đổi tên thành “Hồ sơ tài khoản” cho phù hợp cả ba vai trò. Mục đang mở được đánh dấu; badge tin nhắn vẫn dùng số thực từ hệ thống hiện có, nằm trong đúng mục điều hướng.
- Footer dùng thành phần chung: phần thương hiệu bên trái, nhóm chính sách bên phải. Các liên kết chính sách xếp đúng hai cột, kể cả mobile. Bổ sung Footer vào các trang hoàn chỉnh trước đây chưa dùng, bao gồm Dashboard và trang đăng nhập/đăng ký. Cho phép cuộn trang đăng nhập để tới Footer.
- Thêm nhập cấu hình SMTP riêng qua `spring.config.import=optional:file:./config/mail.properties`. Tạo `config/mail.properties` và hướng dẫn `CAU_HINH_EMAIL_OTP.md`; file chứa mật khẩu thật bị Git bỏ qua. Vẫn hỗ trợ SKILLSHARE_MAIL_USERNAME/PASSWORD và cho phép thay host/port qua biến môi trường.
- Giữ nguyên kiểm tra OTP đăng ký thật; không thay bằng OTP demo hoặc tự kích hoạt tài khoản.

## Kiểm chứng trong lần bàn giao này

- Maven: 18 tests, 0 failures, 0 errors, BUILD SUCCESS: WorkspaceControllerSmokeTest (6), MarketplaceCartSmokeTest (3), RegistrationOtpIntegrationTest (9). Môi trường kiểm thử dùng Byte Buddy agent khai báo từ đầu vì không hỗ trợ Mockito tự attach; không thay đổi cấu hình test trong dự án.
- Chromium, ba phiên độc lập Customer/Provider/Admin: 45 lượt trên Dashboard/Marketplace, /profile và /settings ở 1440, 1024, 768, 390, 320 px. Kiểm tra HTTP 200, thành phần Header không chồng, Header có đúng chiều rộng, toàn trang không tràn ngang, sidebar đúng vai trò, Footer hai cột và không bị cắt. Thêm 3 lượt trang đăng nhập/đăng ký/điều khoản trên mobile: tổng 48 lượt đạt.
- 5 kiểm tra menu mobile đạt: popup thông báo nằm trong màn hình ở cả ba vai trò; menu Workspace Provider và Admin mở được và đóng bằng Escape, đồng bộ aria-expanded.
- Đã xem ảnh chụp nội bộ của bố cục desktop/mobile. Tài nguyên ngoài (ảnh đại diện, font, thư viện biểu đồ CDN) bị chặn trong phiên kiểm tra để chỉ kiểm tra bố cục nội bộ; không đánh giá việc tải các tài nguyên ngoài đó.
- SMTP cục bộ: khởi chạy ứng dụng từ thư mục kiểm thử có config/mail.properties riêng; JavaMailSender thực sự kết nối SMTP kiểm thử, gửi đúng một email OTP tới địa chỉ .test, xác nhận người gửi/người nhận. Gửi lại trong thời gian chờ trả HTTP 429 và không phát thêm email.
- JavaScript đã qua kiểm tra cú pháp. Gói ZIP được kiểm tra toàn vẹn.

## Giới hạn và cách bật OTP thật

Chưa có địa chỉ hộp thư gửi và App Password của người vận hành nên chưa gửi thư qua Gmail thật. Kết quả SMTP cục bộ không chứng minh email sẽ vào hộp thư đến Gmail.

Để dùng thật: sao chép config/mail.properties.example thành config/mail.properties, nhập địa chỉ Gmail gửi cùng App Password Google, đặt Working directory IntelliJ về thư mục skill-rental chứa pom.xml rồi khởi động lại. Chi tiết và cách dùng biến môi trường ở CAU_HINH_EMAIL_OTP.md. Không gửi mật khẩu qua chat hoặc đưa file cấu hình thật vào ZIP.

Các báo cáo kiểm thử cũ có sẵn trong nguồn là lịch sử lần bàn giao trước; chỉ các tệp UI_SHELL_RESULT.json, UI_MENU_RESULT.json, SMTP_CONFIG_RESULT.json và MAVEN_UI_SMTP_TEST_RESULT.txt phản ánh lượt sửa này. Không chạy lại toàn bộ chức năng chat/bàn giao đã kiểm tra ở lần trước.

## File sửa so với ZIP đầu vào

- `.gitignore`
- `src/main/resources/application.properties`
- `src/main/resources/static/css/app.css`
- `src/main/resources/static/js/app.js`
- `src/main/resources/static/js/workspace.js`
- `src/main/resources/templates/auth/change-password.html`
- `src/main/resources/templates/auth/forgot-password.html`
- `src/main/resources/templates/auth/login.html`
- `src/main/resources/templates/auth/register.html`
- `src/main/resources/templates/customer/billing.html`
- `src/main/resources/templates/customer/coming-soon.html`
- `src/main/resources/templates/customer/kyc.html`
- `src/main/resources/templates/customer/language.html`
- `src/main/resources/templates/customer/notification-settings.html`
- `src/main/resources/templates/customer/profile.html`
- `src/main/resources/templates/customer/settings.html`
- `src/main/resources/templates/customer/wallet.html`
- `src/main/resources/templates/fragments.html`
- `src/main/resources/templates/interaction/complaint.html`
- `src/main/resources/templates/interaction/review.html`
- `src/main/resources/templates/orders/orders.html`
- `src/main/resources/templates/policies/provider-consent.html`
- `src/main/resources/templates/requests/applies.html`
- `src/main/resources/templates/requests/create.html`
- `src/main/resources/templates/requests/edit.html`
- `src/main/resources/templates/requests/list.html`
- `src/main/resources/templates/workspace/admin/page.html`
- `src/main/resources/templates/workspace/admin/support-reviews.html`
- `src/main/resources/templates/workspace/fragments.html`
- `src/main/resources/templates/workspace/provider/page.html`

## File mới

- CAU_HINH_EMAIL_OTP.md
- config/mail.properties.example
- BAO_CAO_GIAO_DIEN_VA_OTP.md
- validation/UI_SHELL_RESULT.json
- validation/UI_MENU_RESULT.json
- validation/SMTP_CONFIG_RESULT.json
- validation/MAVEN_UI_SMTP_TEST_RESULT.txt
