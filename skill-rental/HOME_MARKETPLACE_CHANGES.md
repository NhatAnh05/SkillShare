# Home Marketplace — cập nhật Provider và Request

## Thay đổi

1. Khu vực **Provider nổi bật** giữ nguyên CSS grid và các breakpoint hiện có. Script `static/js/home-marketplace.js` đọc số cột đang hiển thị, ban đầu chỉ cho xem tối đa 3 hàng (số thẻ = số cột hiện tại × 3). Nút **Xem thêm Provider** mở toàn bộ danh sách; bấm lần nữa để thu gọn.
2. Khu vực **Request đang tìm Provider** lấy dữ liệu thật từ `service_requests`, chỉ hiển thị Request có trạng thái `OPEN`; bỏ ba thẻ Request hard-code trước đó.
3. Home hiển thị tối đa 6 Request mới nhất, kèm kỹ năng, hạn hoàn thành, ngân sách và số lượng Provider đã ứng tuyển.
4. Bổ sung trang `/requests/browse` để xem toàn bộ Request đang mở. Tài khoản Provider có nút xem chi tiết/ứng tuyển qua luồng hiện có (`/provider/requests/{id}` và `/provider/requests/{id}/apply`). Khách hàng có thể quản lý Request của mình hoặc tạo Request mới.
5. Thêm mục **Tìm Request** vào thanh điều hướng, đồng thời giữ nguyên mục **Request của tôi**.

## Các file chính

- `src/main/java/com/skillrental/controller/HomeController.java`
- `src/main/java/com/skillrental/controller/RequestController.java`
- `src/main/java/com/skillrental/service/RequestService.java`
- `src/main/java/com/skillrental/repository/ServiceRequestRepository.java`
- `src/main/resources/templates/home.html`
- `src/main/resources/templates/requests/browse.html`
- `src/main/resources/templates/fragments.html`
- `src/main/resources/static/js/home-marketplace.js`
- `src/main/resources/static/css/app.css`

## Kiểm thử

Smoke test `/requests/browse` đã được thêm vào `WorkspaceControllerSmokeTest`. Cần chạy `mvn test` trong môi trường dự án để xác nhận Spring Boot/Thymeleaf thực thi thực tế; môi trường chỉnh sửa hiện tại không có Maven cài đặt.
