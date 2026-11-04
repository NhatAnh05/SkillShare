# SkillShare — hoàn thiện chat, hỗ trợ, tài liệu, thông báo và điều khoản

Spring Boot 3.3.5 · Java 17 · Maven · Thymeleaf · WebSocket · H2/MySQL.

**Đã kiểm tra:** Maven 96/96 đạt; 18 nhóm browser workflow và 6 nhóm browser review đạt.

Mở thư mục chứa pom.xml trên IntelliJ, chọn JDK 17 và Reload Maven. Đọc [Hướng dẫn chạy và kiểm tra](HUONG_DAN_CHAY_BAN_MOI.md), [Báo cáo đối chiếu yêu cầu](BAO_CAO_HOAN_THIEN.md) và các kết quả trong validation.

```bash
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- `/messages`: hộp thư dùng chung Customer, Provider và Admin; widget nổi dành cho chat nhanh.
- `/admin/support`: tìm/lọc hỗ trợ, ghi chú nội bộ, nhận/trả/chuyển ca, liên kết hồ sơ.
- `/admin/support-reviews`: quyền xem chat khiếu nại có phê duyệt độc lập, thời hạn và audit; cần cấu hình reviewer riêng.
- `/orders/{id}/progress`: tài liệu trao đổi hai chiều và bàn giao chính thức theo phiên bản, giữ luồng nghiệm thu/OTP hiện có.
- `/admin/notifications`: gửi cho Customer, Provider, cả hai nhóm hoặc danh sách chọn, có lịch sử và thử lại.
- `/terms/accepted`: phiên bản và nội dung đã chấp nhận; `/provider/terms` cho quy định Provider.

Profile local giữ dữ liệu tại data/skillshare và private-deliveries. Profile mặc định H2 memory dùng cho demo/test và mất dữ liệu khi dừng. MySQL cần chạy migration đúng thứ tự; mới nhất v13→v14. Bản SQL MySQL chưa được thực thi trên MySQL server thật trong môi trường bàn giao.

Tài khoản demo khi database trống: customer@skillrental.vn, provider1@skillrental.vn, admin@skillrental.vn; mật khẩu 123456. Chỉ dùng trong môi trường demo. Google, SMTP và VNPay vẫn dùng biến môi trường như trước.

Kiểm thử giao diện là tự động trên Chromium headless với các phiên đăng nhập độc lập, dùng backend/DB/WebSocket thật; không thay cho nghiệm thu thủ công của người dùng trên các trình duyệt/máy khác. Không có endpoint debug hoặc tài khoản reviewer tự thêm vào runtime production.
