# Chọn Customer hoặc Provider khi đăng ký — 02/11/2026

Áp dụng trên bản sao SkillShare_Cap_Nhat_Header_Sidebar_Footer_OTP.zip. Giữ nguyên các thay đổi Header, sidebar, Footer, SMTP và nghiệp vụ có sẵn.

## Hành vi mới

- Form đăng ký email/OTP bắt buộc chọn Customer (thuê dịch vụ) hoặc Provider (cung cấp dịch vụ); ban đầu chưa chọn vai trò nào.
- Backend dùng kiểu dữ liệu đăng ký chỉ có CUSTOMER/PROVIDER. Thiếu vai trò, ADMIN hoặc giá trị lạ bị từ chối; không thể tự đăng ký tài khoản Admin.
- Gửi mã tạo tài khoản chờ xác minh cùng hồ sơ tương ứng. Tải lại trang trong phiên đang đăng ký vẫn giữ vai trò đã gửi OTP.
- Xác minh OTP kích hoạt đúng vai trò đã chọn. Provider có hồ sơ khởi tạo trống, không có kỹ năng/đánh giá giả và approved=false; cần hoàn thiện hồ sơ, được Admin duyệt theo quy trình hiện có trước khi công khai.
- Nếu đổi vai trò sau khi gửi OTP, cần gửi lại mã theo thời gian chờ 60 giây. Mã mới thay thế mã cũ; hồ sơ chờ xác minh chuyển sang đúng loại và loại bỏ hồ sơ của vai trò cũ.
- Nếu SMTP thất bại trong khi đổi vai trò, giao dịch rollback giữ vai trò, hồ sơ và OTP trước đó.
- Việc đổi vai trò này chỉ áp dụng cho tài khoản chưa xác minh. Email đã hoạt động không thể dùng lại luồng đăng ký để đổi quyền.
- Đăng nhập Google được giữ nguyên hành vi hiện có; thay đổi này áp dụng cho biểu mẫu đăng ký email/OTP.

## Kiểm thử lần này

- Maven: 19 tests, 0 failures, 0 errors, BUILD SUCCESS. Gồm RegistrationOtpIntegrationTest (13) và WorkspaceControllerSmokeTest (6).
- Kiểm thử mới: Provider đăng ký và tải lại trang giữ lựa chọn; hồ sơ chưa được duyệt; đăng nhập Provider truy cập Workspace; từ chối vai trò trống/ADMIN/không hợp lệ; đổi vai trò cần mã mới và chịu cooldown; chuyển hồ sơ đúng loại; rollback khi SMTP lỗi.
- Trình duyệt Chromium: hai phiên độc lập đăng ký CUSTOMER và PROVIDER trên màn hình 390px. Mỗi phiên chọn vai trò, gửi OTP qua SMTP kiểm thử cục bộ, tải lại trang, xác minh, đăng nhập và mở đúng hồ sơ/Workspace. Không ghi nhận lỗi JavaScript trong hai luồng.
- JavaScript inline của form qua kiểm tra cú pháp. ZIP đã kiểm tra toàn vẹn, giữ đủ các file đầu vào.

Không có migration mới: tái sử dụng users.role và hai bảng hồ sơ hiện có. Chưa gửi OTP qua Gmail thật; cấu hình hộp thư thật theo CAU_HINH_EMAIL_OTP.md như bản trước.

## File sửa

- `src/main/java/com/skillrental/controller/AuthController.java`
- `src/main/java/com/skillrental/dto/RegisterRequest.java`
- `src/main/java/com/skillrental/service/AuthService.java`
- `src/main/resources/templates/auth/register.html`
- `src/test/java/com/skillrental/RegistrationOtpIntegrationTest.java`

## File mới

- BAO_CAO_DANG_KY_CHON_VAI_TRO.md
- validation/REGISTRATION_ROLE_MAVEN_RESULT.txt
- validation/REGISTRATION_ROLE_BROWSER_RESULT.json
