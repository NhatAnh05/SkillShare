# Skill Rental — Bản nâng cấp giao diện theo 7 ảnh Figma

## Cấu hình dự án
Mở trực tiếp thư mục `skill-rental/` chứa `pom.xml` bằng IntelliJ IDEA.
Project SDK: **JDK 17**; Language level: 17. Reload Maven rồi chạy
`SkillRentalApplication.java` hoặc `mvn spring-boot:run` (khi Maven đã cài).
H2 demo tạo lại schema mỗi lần khởi chạy. MySQL profile dùng `ddl-auto=update`.
Trước khi chạy migration với database cũ, **sao lưu dữ liệu** và kiểm tra xem
cột `two_factor_preference` đã tồn tại chưa. Chỉ chạy `db/figma-upgrade-mysql.sql`
nếu schema chưa tự cập nhật.

## Các mục hoàn thành
1. Login chia đôi màn hình; social OAuth bị vô hiệu hóa vì chưa tích hợp.
2. Marketplace: hero, search, chip kỹ năng lấy từ DB, lưới Provider 4/2/1 cột;
   giữ lọc khoảng giá hai slider và bộ lọc kỹ năng của bản trước.
3. Provider Detail: gallery minh họa, thống kê sao theo Review thực có trong DB,
   danh sách Review thực, booking sticky, nút nhắn tin từ controller cũ.
4. Request: 4 chỉ số đếm trực tiếp từ Request của tài khoản hiện tại;
   avatar ứng tuyển từ dữ liệu Apply; thêm chức năng chỉnh sửa Request đang mở.
5. Booking: lịch tháng + 4 slot hai giờ; giá giờ x 2 và +25% premium tính
   cả ở JS lẫn Backend. Payment và trang kết thúc ghi rõ giao dịch mô phỏng.
6. Cài đặt: % hồ sơ là số trường có dữ liệu/5, 6 ô chức năng.
   Ví/KYC/Thông báo/Ngôn ngữ hiển thị trang đang phát triển, không thực hiện hành động giả.
7. Đổi mật khẩu: trình đo độ mạnh trên client, xác nhận khớp, lưu lựa chọn 2FA
   vào `User.twoFactorPreference`; CHƯA triển khai OTP/2FA thật. Quản lý phiên khác
   là nút disabled, không hiển thị thiết bị/vị trí suy đoán.

## Dữ liệu minh họa và giới hạn demo
- `10,000+` ở ảnh Login chỉ là số trang trí được gắn nhãn minh họa.
- Các hình portfolio và tên dự án là placeholder, không phải thành tích Provider thật.
- Badge "Đã xác minh (demo)" không thay thế nghiệp vụ xác minh danh tính thật.
- Rating tổng trên hồ sơ trong DemoDataConfig là số liệu mẫu;
  biểu đồ chi tiết chỉ thống kê các review thực sự trong bảng `reviews`.
- Chưa có thanh toán thật, escrow, OAuth, khóa học, OTP hoặc kiểm tra lịch trống.
- Tác vụ chat nổi, review bằng 5 sao, order modal và các controller cũ vẫn được giữ.

## Code đầy đủ
Toàn bộ nội dung từng file được sửa hoặc thêm mới nằm trong file
`FIGMA_FULL_CHANGED_CODE.md` (đường dẫn + nội dung file, không viết tắt).
`FIGMA_CHANGED_FILES.txt` liệt kê chính xác tên file để đối chiếu.
