# SkillShare — cập nhật hoàn thiện chức năng (không gồm Google Login và thanh toán)

## Thay đổi trong bản này

1. **Xác nhận email khi đăng ký**
   - Tài khoản đăng ký mới được đặt ở trạng thái `PENDING_VERIFICATION` và không đăng nhập được trước khi xác nhận.
   - Token xác nhận có thời hạn 24 giờ, được dùng một lần và bị xóa sau khi xác nhận.
   - Gửi email xác nhận qua Gmail SMTP khi đã cấu hình `SKILLSHARE_MAIL_USERNAME` và `SKILLSHARE_MAIL_PASSWORD`.
   - Khi chưa cấu hình SMTP, trang đăng nhập hiển thị liên kết xác nhận chỉ phục vụ kiểm thử local.
2. **Khôi phục mật khẩu**
   - Token có thời hạn 30 phút và bị xóa sau khi đổi mật khẩu.
   - Không tiết lộ email có tồn tại hay không qua thông báo phản hồi.
   - Khi SMTP chưa cấu hình, liên kết kiểm thử local được hiển thị để có thể kiểm tra luồng.
3. **Danh sách dịch vụ đã chọn (Cart)**
   - Bổ sung trang danh mục dịch vụ đã xuất bản tại `/services`.
   - Thêm dịch vụ, cập nhật số lượng (1–99), xóa dịch vụ, kiểm tra quyền sở hữu giỏ và tính tổng tạm tính.
   - Giá được chụp lại khi thêm/cập nhật vào giỏ; giỏ gắn với tài khoản hiện tại.
   - Không bật checkout/thanh toán nhiều dịch vụ trong bản này theo phạm vi loại trừ thanh toán.
4. **Dịch vụ phổ biến và gợi ý Provider**
   - Dịch vụ phổ biến lấy từ `rental_order_items` và số đơn không bị hủy, chỉ hiển thị dịch vụ `PUBLISHED`.
   - Gợi ý Provider dựa trên mức độ khớp kỹ năng ưu tiên trong hồ sơ Customer, sau đó xếp hạng theo đánh giá.

## Phạm vi loại trừ theo yêu cầu

- Không triển khai Google OAuth / đăng nhập bằng Google.
- Không hoàn thiện hoặc bật cổng thanh toán, checkout thanh toán, nạp tiền hay chuyển tiền thật. Những màn hình/tích hợp thanh toán hiện có chỉ nên dùng trong môi trường demo và không được xem là thanh toán sản xuất.

## Kiểm tra đã thực hiện

- Đã rà soát cấu trúc controller, entity/repository và cập nhật source.
- Đã kiểm tra cân bằng dấu ngoặc ở các lớp Java được sửa và kiểm tra đường dẫn template mới.
- **Chưa chạy được `mvn test` hoặc build Spring Boot** trong môi trường tạo bản này vì máy không có Maven và không thể truy cập Maven Central để tải dependency. Do đó không tuyên bố đã kiểm thử end-to-end hoặc đạt 100% đã được chứng minh.

## Checklist bắt buộc trước khi chốt 100%

- Chạy `mvn test` trên máy có Maven/JDK 17+; sửa mọi lỗi compile/test nếu phát sinh.
- Cấu hình Gmail App Password và kiểm tra nhận email xác nhận, link hết hạn, link dùng lại, reset token hết hạn và reset token dùng lại.
- Kiểm thử giỏ với 0 dịch vụ, 1 dịch vụ, dịch vụ không còn `PUBLISHED`, cập nhật số lượng, xóa và quyền truy cập chéo tài khoản.
- Kiểm thử hủy đơn/hoàn tiền/ký quỹ, báo cáo Admin, biểu đồ theo khoảng ngày, phản hồi review và quyền Customer/Provider/Admin trên cùng một database test.
