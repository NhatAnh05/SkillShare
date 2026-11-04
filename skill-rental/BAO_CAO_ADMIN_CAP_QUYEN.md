# Admin nâng Customer/Provider lên Admin — 03/11/2026

## Áp dụng vào ZIP mới gửi ngày 03/11/2026

- Bản đầu vào: SkillShare_Dat_Lich_Tien_Do_Ban_Giao.zip; SHA-256: `37b769a127d428062bfb96e2e9e6abcae605fc2e272251540b39d58bc7c2970e`.
- ZIP mới gửi trùng từng byte nội dung với bản nền của phần cấp quyền đã triển khai. Áp dụng đúng 9 file sửa và các file mới; giữ nguyên tất cả file ngoài phạm vi.
- Đối chiếu mã Java, HTML, JS, CSS, cấu hình và SQL sau khi áp dụng: trùng bản đã kiểm thử trình duyệt trước đó.
- Chạy Maven mới trên thư mục giải nén này: AdminUserManagementTest, **11 tests, 0 failures, 0 errors, BUILD SUCCESS**. Bao gồm Customer/Provider lên Admin, quyền backend/CSRF, kiểm tra đầu vào, giữ hồ sơ, chặn đơn/dòng tiền còn xử lý, không trùng nhật ký và đăng nhập lại với quyền mới.
- Không chạy lại các luồng trình duyệt đã thành công trên cùng mã nguồn. Các kết quả 30 tests và 3 kiểm tra browser ở phần sau là bằng chứng của lượt triển khai trước, không phải kết quả chạy mới hôm nay.
- Migration v15→v16 vẫn chưa được thử trên MySQL thật.

## Cách sử dụng

1. Đăng nhập bằng tài khoản Admin → Người dùng.
2. Tìm tài khoản cần nâng quyền. Trong cột Vai trò, mở “Cấp quyền Admin”.
3. Chọn Admin, nhập lý do rồi nhấn “Lưu vai trò Admin” và xác nhận.
4. Người được cấp quyền đăng nhập lại bằng email/mật khẩu để cập nhật quyền; sau đó có thể vào Admin Dashboard.

Tài khoản mới ở form đăng ký vẫn chỉ chọn Customer hoặc Provider; không cho phép tự đăng ký Admin. Chức năng này chỉ nâng Customer/Provider lên Admin, không bổ sung hạ quyền Admin hoặc chuyển Customer ↔ Provider.

## Điều kiện và kiểm soát

- Kiểm tra quyền Admin ở SecurityFilterChain, controller và service. Service đọc vai trò/trạng thái/email đã xác minh của người thực hiện từ database.
- Chỉ nâng tài khoản ACTIVE và đã xác minh email. Không tự mở khóa hoặc bỏ qua OTP cho tài khoản đang chờ xác minh.
- Chặn khi tài khoản còn đơn chưa kết thúc, tránh làm mất quyền bắt đầu, bàn giao hoặc nghiệm thu đơn đang xử lý.
- Provider còn số dư/tiền khóa trong ví hoặc yêu cầu rút tiền PENDING_OTP/PENDING_ADMIN cần xử lý xong trước khi nâng quyền, vì khu vực rút tiền Provider yêu cầu role PROVIDER.
- Tái sử dụng users.role. Không xóa hồ sơ Customer/Provider, dịch vụ hoặc lịch sử giao dịch. Dịch vụ của người đã thành Admin không còn đáp ứng điều kiện Provider ở Marketplace.
- Yêu cầu POST có CSRF, vai trò mới chính xác ADMIN và lý do không rỗng, tối đa 500 ký tự.
- Khóa dòng tài khoản khi nâng quyền. Gửi lại cho tài khoản đã là Admin không ghi thêm nhật ký hoặc phát lại thông báo.
- Lưu người nhận quyền, Admin thực hiện, vai trò cũ/mới, lý do và thời điểm vào user_role_changes. Trang Người dùng hiển thị 20 thay đổi mới nhất theo phân quyền Admin.
- Gửi thông báo trong ứng dụng cho người được cấp quyền. Phiên HTTP còn quyền Customer/Provider sẽ được đăng xuất khi gửi yêu cầu tiếp theo, xóa cookie remember-me của phiên đó; đăng nhập lại lấy quyền Admin từ database. API yêu cầu JSON nhận 401, trình duyệt được chuyển về /login?roleChanged.

Ứng dụng hiện có một vai trò chính trên tài khoản, không triển khai nhiều vai trò đồng thời. Đây là lý do cần xử lý đơn và dòng tiền Provider trước khi chuyển sang Admin. Luồng đăng nhập Google vẫn giữ phạm vi Customer như trước; tài khoản Google chưa có mật khẩu sử dụng chức năng quên mật khẩu qua email trước khi đăng nhập bằng email/mật khẩu với quyền Admin.

## Database

Migration mới: src/main/resources/db/migration-v15-to-v16-admin-role-changes.sql. Tạo bảng user_role_changes và các khóa ngoại đến users; không tự thay đổi vai trò tài khoản nào khi khởi động.

MySQL đã ở v15: sao lưu rồi chạy migration đúng một lần trước khi khởi động profile mysql (ddl-auto=validate). Cài MySQL mới: dùng schema-mysql.sql đã cập nhật. Local H2 với profile local tiếp tục cập nhật schema theo cấu hình sẵn có.

## Kiểm thử đã lưu của phần triển khai trước (02/11/2026)

- Maven: 30 tests, 0 failures, 0 errors, BUILD SUCCESS. Gồm AdminUserManagementTest (11), RegistrationOtpIntegrationTest (13), WorkspaceControllerSmokeTest (6). Đây là bộ kiểm thử tập trung cho lần sửa này, không phải kết quả chạy lại toàn bộ hệ thống.
- Trường hợp mới: nâng Customer/Provider; giữ hồ sơ; gửi lặp không trùng audit; chặn Customer và authority giả; kiểm tra quyền ở service; CSRF; validate vai trò/lý do/trạng thái; chặn đơn đang làm và số dư Provider; chấm dứt phiên cũ rồi đăng nhập được vào Admin Dashboard.
- Chromium với các phiên độc lập: tạo và xác minh hai tài khoản thử qua SMTP cục bộ, đăng nhập Customer/Provider; Admin thực hiện thao tác ở cột Vai trò; giao diện lưu ADMIN và hiển thị lịch sử; phiên cũ bị yêu cầu đăng nhập; đăng nhập lại vào được Admin Dashboard. Bộ lọc Admin hiển thị cả hai tài khoản mới được nâng. Tổng 3 kiểm tra giao diện đạt.
- Đã xem ảnh nội bộ trang danh sách và lịch sử. Không đóng gói tài khoản/database thử hoặc ảnh chụp. Tài nguyên font/avatar ngoài được chặn trong trình duyệt kiểm tra.
- ZIP được kiểm tra toàn vẹn và giữ đủ file của ZIP đầu vào.

Chưa chạy migration trên MySQL thật và chưa gửi OTP qua Gmail thật. Các báo cáo cũ trong nguồn là lịch sử lần trước; validation/ADMIN_ROLE_* là bằng chứng lần sửa này.

## File sửa

- `src/main/java/com/skillrental/config/SecurityConfig.java`
- `src/main/java/com/skillrental/repository/RentalOrderRepository.java`
- `src/main/java/com/skillrental/repository/UserRepository.java`
- `src/main/java/com/skillrental/service/UserManagementService.java`
- `src/main/java/com/skillrental/workspace/WorkspaceController.java`
- `src/main/resources/db/schema-mysql.sql`
- `src/main/resources/templates/auth/login.html`
- `src/main/resources/templates/workspace/admin/page.html`
- `src/test/java/com/skillrental/AdminUserManagementTest.java`

## File mới

- src/main/java/com/skillrental/config/PromotedAdminSessionFilter.java
- src/main/java/com/skillrental/entity/UserRoleChange.java
- src/main/java/com/skillrental/repository/UserRoleChangeRepository.java
- src/main/resources/db/migration-v15-to-v16-admin-role-changes.sql
- BAO_CAO_ADMIN_CAP_QUYEN.md
- validation/ADMIN_ROLE_BROWSER_RESULT.json
- validation/ADMIN_ROLE_MAVEN_RESULT.json
