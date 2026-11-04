# Đặt lịch, tiến độ và bàn giao trong đơn hàng — 02/11/2026

## Phạm vi và luồng sử dụng

Sửa trực tiếp từ bản đính kèm SkillShare_Dang_Ky_Chon_Customer_Provider(1).zip. Các phần đăng ký chọn vai trò, Header, sidebar, Footer, thông báo và cấu hình SMTP trước đó được giữ lại.

Luồng mới: Customer đặt công việc → Provider nhận → cả hai xác nhận bắt đầu → Provider cập nhật phần việc đã làm và thời điểm dự kiến → Provider nộp tệp/liên kết sản phẩm đồng thời báo hoàn tất → Customer kiểm tra, yêu cầu sửa hoặc nghiệm thu → OTP và quyết toán theo luồng hiện có.

“Provider báo hoàn tất” tương ứng trạng thái chờ Customer nghiệm thu, chưa phải đơn đã hoàn thành và chưa giải ngân. 100% trên tiến độ là báo cáo Provider đã nộp sản phẩm, không thay cho việc nghiệm thu.

## Hai cách đặt lịch

| Hình thức | Dữ liệu lưu | Cách dùng |
|---|---|---|
| Hẹn giờ cố định (APPOINTMENT) | Ngày hẹn, giờ bắt đầu, số giờ; kết thúc dự kiến = bắt đầu + số giờ | Dịch vụ theo giờ bắt buộc dùng; công việc giá cố định cũng có thể chọn nếu cần buổi làm việc |
| Theo hạn giao (DEADLINE) | Ngày và giờ hạn giao sản phẩm, không cần khung giờ làm việc | Công việc giá cố định/theo gói; là lựa chọn đề xuất khi chọn dịch vụ giá cố định |

Giá theo giờ vẫn tính đơn giá × số giờ × số lượng; giá cố định không nhân số giờ. Hạn đã thỏa thuận lưu riêng trên Booking; ETA Provider cập nhật trên đơn không thay đổi hạn này. Quá hạn hiển thị cảnh báo, không tự hủy, không tự nghiệm thu và không tự quyết toán.

Lịch hẹn là đề xuất chờ Provider chấp thuận. Bản này chưa có bộ máy kiểm tra xung đột lịch/đảm bảo lịch trống hoặc tự khởi động công việc theo đồng hồ. Các mốc LocalDateTime dùng giờ máy chủ; khi triển khai nên cấu hình JVM theo múi giờ vận hành, chẳng hạn Asia/Ho_Chi_Minh.

## Chat và bàn giao

- Chat vẫn nhắn tin realtime. Bỏ form gửi file/liên kết mới trong chat và chặn gửi tài liệu qua API chat ở backend.
- Tài liệu chat đã có vẫn đọc/tải lại theo phân quyền, không xóa lịch sử.
- Customer và Provider trao đổi tài liệu yêu cầu/bản nháp trong đơn hàng; tài liệu trao đổi không tự hoàn tất công việc.
- Provider phải nộp ít nhất một tệp không rỗng hoặc URL kết quả http/https hợp lệ cùng mô tả. Chỉ mô tả hoặc link không hợp lệ đều không chuyển đơn sang chờ nghiệm thu.
- “Nộp sản phẩm & báo hoàn tất” lưu bản bàn giao theo phiên bản và đổi trạng thái trong cùng giao dịch. Backend cũng chặn gọi trực tiếp service báo hoàn tất nếu chưa có bản bàn giao hợp lệ.
- Customer yêu cầu sửa bản mới nhất sẽ đưa đơn về đang thực hiện; Provider cần nộp phiên bản kết quả mới. Không được tái dùng bản đang bị yêu cầu chỉnh sửa để báo hoàn tất.
- URL được kiểm tra định dạng, không tự truy cập hoặc đánh giá chất lượng nội dung. Customer vẫn cần kiểm tra kết quả và quyền truy cập liên kết.

## Tiến độ Provider

Provider của đơn đang thực hiện có thể ghi nội dung đã làm, phần trăm từ 0–99 và ETA ở tương lai. Mỗi lần cập nhật có lịch sử, tác giả và thời điểm; Customer nhận thông báo và cập nhật trang chi tiết realtime. Lưu thời điểm bắt đầu khi cả hai xác nhận, thời điểm nộp sản phẩm và thời điểm quyết toán riêng biệt. Các tài khoản ngoài đơn không được xem/cập nhật tiến độ.

## Database và nâng cấp

Migration mới: src/main/resources/db/migration-v14-to-v15-order-workflow.sql.

- bookings: schedule_mode (mặc định APPOINTMENT cho đơn cũ), delivery_due_at.
- rental_orders: work_started_at, delivered_at, estimated_completion_at, progress_percent.
- Bảng order_work_updates: lịch sử cập nhật tiến độ, mô tả, Provider, ETA và thời điểm.
- Schema MySQL cho cài mới đã đồng bộ. Migration cho database v14 chạy đúng một lần sau khi sao lưu. MySQL profile dùng ddl-auto=validate cần chạy migration trước khi khởi động.
- Local H2 profile dùng ddl-auto=update. Đơn cũ chưa có mốc thực tế sẽ hiển thị là chưa lưu mốc, không tự tạo lịch sử giả. Migration MySQL chỉ suy ra hạn lịch hẹn cũ từ ngày/giờ/thời lượng đã lưu.

## Kết quả kiểm thử lần này

- Maven toàn bộ: 105 tests, 0 failures, 0 errors, 0 skipped; tiến trình trả mã 0. Tổng hợp từ 19 báo cáo Surefire cùng một lượt chạy trong validation/ORDER_WORKFLOW_MAVEN_RESULT.json.
- Trước lượt toàn bộ, bộ tập trung đạt 32/32, BUILD SUCCESS: WorkflowCompletionTest, WorkspaceControllerSmokeTest, CustomerProviderAdminTransactionFlowTest.
- 5 test nghiệp vụ bổ sung: chặn tài liệu mới qua chat nhưng vẫn nhắn text; quyền cập nhật tiến độ/thông báo/lịch sử; chặn hoàn tất thiếu sản phẩm và kiểm tra bản sửa mới; đặt hạn giao + chống gửi trùng đúng payload; đặt theo giờ đúng thời lượng/giá và chặn deadline cho dịch vụ theo giờ. Các kiểm thử cũ về file bằng chứng được đổi sang tạo dữ liệu lịch sử để giữ kiểm tra quyền tải tài liệu cũ.
- Chromium, hai phiên Customer/Provider độc lập: 8 luồng thành công — đặt theo hạn không chọn khung giờ; nhận việc và hai bên bắt đầu; tiến độ realtime; chặn hoàn tất thiếu file/link; upload sản phẩm và chuyển chờ nghiệm thu; yêu cầu sửa/nộp bản 2; đặt hẹn 3 giờ; chat không còn form gửi file.
- Lượt browser đầu phát hiện tràn ngang mobile. Sau khi sửa CSS, kiểm tra riêng trang đơn cũ đang thực hiện ở 390px đạt, không chạy lại 8 luồng đã đạt. File xác nhận ghi rõ phạm vi trong validation/ORDER_WORKFLOW_BROWSER_RESULT.json.
- Đã xem ảnh nội bộ của trang tiến độ mobile. Tài nguyên font/avatar/biểu đồ ngoài được chặn trong kiểm tra trình duyệt; không đánh giá các tài nguyên ngoài đó.
- JavaScript kiểm tra cú pháp đạt; ZIP kiểm tra toàn vẹn và giữ đủ file đầu vào.

Giới hạn: chưa chạy migration trên MySQL thật; chưa kiểm tra lại gửi OTP qua Gmail thật. Cấu hình SMTP giữ theo hướng dẫn trước. Các báo cáo cũ còn trong nguồn là lịch sử; hai báo cáo ORDER_WORKFLOW_* mới phản ánh lần sửa này.

## File đã sửa

- `src/main/java/com/skillrental/controller/BookingController.java`
- `src/main/java/com/skillrental/controller/OrderController.java`
- `src/main/java/com/skillrental/dto/BookingForm.java`
- `src/main/java/com/skillrental/entity/Booking.java`
- `src/main/java/com/skillrental/entity/RentalOrder.java`
- `src/main/java/com/skillrental/service/BookingService.java`
- `src/main/java/com/skillrental/service/DeliveryService.java`
- `src/main/java/com/skillrental/service/EscrowService.java`
- `src/main/java/com/skillrental/service/WorkDocumentService.java`
- `src/main/resources/db/schema-mysql.sql`
- `src/main/resources/static/css/app.css`
- `src/main/resources/static/js/figma-ui.js`
- `src/main/resources/static/js/workflow.js`
- `src/main/resources/templates/booking/booking.html`
- `src/main/resources/templates/interaction/chat.html`
- `src/main/resources/templates/orders/progress.html`
- `src/main/resources/templates/workspace/provider/page.html`
- `src/test/java/com/skillrental/CustomerProviderAdminTransactionFlowTest.java`
- `src/test/java/com/skillrental/WorkflowCompletionTest.java`

## File mới

- src/main/java/com/skillrental/entity/ScheduleMode.java
- src/main/java/com/skillrental/entity/OrderWorkUpdate.java
- src/main/java/com/skillrental/repository/OrderWorkUpdateRepository.java
- src/main/java/com/skillrental/service/OrderWorkService.java
- src/main/resources/db/migration-v14-to-v15-order-workflow.sql
- BAO_CAO_DAT_LICH_TIEN_DO_BAN_GIAO.md
- validation/ORDER_WORKFLOW_MAVEN_RESULT.json
- validation/ORDER_WORKFLOW_BROWSER_RESULT.json
