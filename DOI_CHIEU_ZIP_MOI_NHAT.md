# Bàn giao trên ZIP đính kèm mới nhất — 02/11/2026

Đầu vào: `SkillShare_Admin_Notifications_Policies_Full_Source.zip` và `Văn bản đã dán (1)(3).txt`.

Đã giải nén ZIP mới vào thư mục làm việc riêng và đối chiếu từng file. SHA-256 của ZIP đầu vào là `abd963ad55f9627a1865dafc6bab97e3ad53a0b26f32eae3ba3c06d2cda7a225`; SHA-256 văn bản là `5e51a2953ef58c72a28bc972a8dd7d5bc33baba3bde3552063d38a364c8e7ee9`. Cả hai trùng chính xác với bản nền và yêu cầu của lần hoàn thiện trước.

Đã áp dụng 62 file sửa và 40 file bổ sung từ phần hoàn thiện đã kiểm thử. Không xóa file nào trong 253 file đầu vào. Danh sách cùng hash trước/sau nằm tại `validation/LATEST_UPLOAD_CHANGES.json`. Mã ứng dụng, migration và kiểm thử sau khi áp dụng trùng từng byte với bản đã kiểm thử trước; các thay đổi mới của lần bàn giao này là tài liệu đối chiếu và bằng chứng kiểm tra gói nguồn.

Các phần được bổ sung gồm chat hai chiều và hội thoại mới realtime; cuộn và lịch sử chat; trang Tin nhắn chung; hỗ trợ, liên hệ chủ động, chuyển ca và ghi chú Admin; truy cập bằng chứng theo phê duyệt; thông báo nghiệp vụ; tài liệu hai chiều và bàn giao có phiên bản; chấp nhận điều khoản theo phiên bản và snapshot. Chức năng chiến dịch thông báo Admin từ ZIP đầu vào tiếp tục dùng NotificationService/Controller và WebSocket hiện có.

## Kết quả kiểm tra và phạm vi bằng chứng

- Lần chạy trước ngày 01/11/2026: Maven 96 tests, 0 failures/errors/skipped; 18 nhóm browser workflow và 6 nhóm review workflow đạt. Kết quả giữ trong `validation/MAVEN_TEST_RESULT.txt`, `BROWSER_WORKFLOW_RESULT.json`, `BROWSER_REVIEW_RESULT.json`.
- Lần bàn giao 02/11/2026: đối chiếu SHA-256 đầu vào; kiểm tra mã nguồn sau áp dụng trùng bản đã kiểm thử; chạy lại syntax check JavaScript; kiểm tra gói ZIP và toàn bộ file đầu vào được giữ lại.
- Không chạy lại Maven hoặc các bộ browser trong lần bàn giao này. Môi trường phiên mới không còn Maven/dependency cache/browser binary của lần chạy trước. Các kết quả PASS nêu trên là kết quả đã lưu của lần chạy trước trên cùng mã nguồn, không phải một lần chạy mới.
- Chưa xác minh thủ công trên trình duyệt máy người dùng. Kiểm thử browser trước là tự động trên Chromium với các phiên Customer, Provider và Admin độc lập.

## Cách mở và nâng cấp

Giải nén vào thư mục mới, mở `pom.xml` bằng IntelliJ, chọn JDK 17 và reload Maven. Xem `HUONG_DAN_CHAY_BAN_MOI.md` để chạy local, đăng nhập ba vai trò và thử chat/thông báo không F5. Dùng `mvn test` để kiểm tra lại trên máy của bạn.

Nếu dùng database v13, sao lưu rồi chạy thủ công `src/main/resources/db/migration-v13-to-v14-workflow-completion.sql`. Nếu database cũ hơn, áp dụng các migration còn thiếu đúng thứ tự; không chạy lại migration đã áp dụng. Không thay database đang dùng bằng database demo.

Giới hạn còn lại: chưa chạy migration trên MySQL thật; chưa kiểm thử với daemon ClamAV production; WebSocket hiện dành cho một instance; điều khoản còn là dự thảo cần thông tin đơn vị vận hành, liên hệ và thời hạn lưu giữ. Chi tiết nguyên nhân lỗi, file sửa, đối chiếu B–H, kiểm thử và giới hạn nằm trong `BAO_CAO_HOAN_THIEN.md`.
