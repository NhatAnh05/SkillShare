# Lưu database trên ổ đĩa và mở khóa 49 Provider mẫu

Bản này mặc định dùng profile `local`: H2 file `./data/skillshare`, Hibernate `update`.
Mở đúng thư mục có `pom.xml`, giữ Working directory tại đó. Nếu IntelliJ đang chỉ định profile khác, đổi Active profiles thành `local`.

## Chạy với dữ liệu hiện có

1. Stop ứng dụng cũ. Sao lưu thư mục `data` khi ứng dụng đã dừng.
2. Đặt source bản mới vào dự án đang dùng, giữ nguyên `data` và Working directory. Không chép thư mục database sang máy khác khi app đang chạy.
3. Khôi phục cấu hình email riêng bằng biến môi trường hoặc `config/mail.properties`. ZIP không chứa mật khẩu SMTP thật; có file example.
4. Run `SkillRentalApplication` với profile `local`.
5. Sau seed (nếu database mới), tác vụ một lần mở các tài khoản `seed.provider01@skillshare.invalid` đến `seed.provider49@skillshare.invalid` từ LOCKED sang ACTIVE.
6. Đăng nhập Admin, kiểm tra 49 tài khoản; vào Marketplace bằng Customer để xem tối đa 4 Provider gợi ý. Dữ liệu được duyệt/xác minh của seed được giữ nguyên.
7. Stop/Run lại, kiểm tra trạng thái vẫn ACTIVE. Sau đó chủ động khóa một Provider qua Admin và Run lại: tài khoản đó phải tiếp tục LOCKED.

Tác vụ chỉ chạy ở `local`, không chạy khi có `prod` hoặc `mysql`. Đánh dấu hoàn tất bằng bảng `skillshare_demo_tasks`, khóa `unlock-49-seed-providers-v1`. Không xóa bảng/dòng này để tránh chạy lại thao tác mở khóa sau các quyết định của Admin.

Nếu database không có đủ đúng 49 tài khoản mẫu còn mang vai trò PROVIDER, tác vụ thông báo deferred và không cập nhật tài khoản nào. Không mở khóa hàng loạt tài khoản thật hay Customer. Các Provider chưa xác minh hoặc không có trạng thái LOCKED không bị cưỡng ép đổi trạng thái. Không đổi mật khẩu của tài khoản mẫu.

Nếu trước đây chạy H2 trong RAM, dữ liệu của tiến trình cũ không tự chuyển thành database file; lần chạy local đầu tiên có thể tạo bộ dữ liệu mới. Nếu đã dùng local thì giữ nguyên file data cũ sẽ giữ được thông tin tài khoản.

## Phạm vi và kiểm thử

- Đổi profile mặc định sang local; cấu hình test dùng default test để các test Spring Boot không ghi vào database local.
- Seed chạy thứ tự 0; mở khóa chạy thứ tự 1. Seed chỉ tạo dữ liệu khi database rỗng.
- Thêm 3 test H2 file: mở đúng 49 và giữ user khác; giữ khóa của Admin sau startup tiếp theo; không cập nhật một phần nếu dữ liệu seed chưa đủ.
- Chưa chạy Maven/JUnit hoặc ứng dụng Spring Boot trong môi trường bàn giao vì thiếu Maven/dependencies và mạng tải bị chặn. Cần chạy `mvn -Dtest=LocalDemoProviderUnlockConfigTest test` và nghiệm thu các bước trên tại máy phát triển.
- Thay đổi này chuẩn bị dữ liệu kiểm thử đề xuất; không bổ sung mô hình ML hoặc chatbot AI.
