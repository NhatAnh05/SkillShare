# Skill Rental — Checklist chỉnh sửa giao diện A–G

## A. Đăng nhập
- `templates/auth/login.html`: loại bỏ code fence Markdown vô tình nằm trong HTML gốc, logo, h1 tên website, chú thích, Remember Me, form viewport.
- `static/css/app.css`: `.auth-login`, `.login-body`, responsive theo chiều cao.
- `config/SecurityConfig.java`: kích hoạt `rememberMe` cho cookie đăng nhập (không lưu mật khẩu gốc).

## B. Header, sidebar, footer, marketplace
- `templates/fragments.html`: icons SVG 24×24, dropdown avatar, thông báo, footer, chat widget, sidebar dành riêng quản lý.
- `templates/home.html`, `templates/marketplace/search.html`, `templates/marketplace/provider-detail.html`, `templates/booking/booking.html`, `templates/booking/payment.html`: Header + Footer; không Sidebar.
- `templates/customer/profile.html`, `templates/customer/settings.html`, `templates/requests/*.html`, `templates/orders/*.html`, `templates/auth/change-password.html`, `templates/interaction/review.html`, `templates/interaction/complaint.html`: Sidebar + Footer, không Header.
- `controller/MarketplaceController.java`, `service/MarketplaceService.java`: bộ lọc minPrice/maxPrice và lọc theo tên kỹ năng chính xác.
- `controller/SettingsController.java`: Cài đặt tài khoản hoạt động.
- `static/js/app.js`: lọc option kỹ năng và dual range slider, điều khiển dropdown.

## C. Tin nhắn
- `templates/interaction/chat.html`: chỉ là fragment popup cho floating action button, không còn trang chat riêng.
- `controller/ChatWidgetController.java`, `controller/InteractionController.java`: lấy danh sách chat, nội dung chat, tạo cuộc hội thoại, gửi tin; bảo toàn kiểm tra Customer sở hữu hội thoại.
- `static/js/app.js`, `static/css/app.css`: popup và polling 3 giây; DOM textContent chống chèn HTML từ tin nhắn.

## D. Đánh giá và khiếu nại
- `templates/interaction/review.html`: năm sao radio bấm trực tiếp, có labels và validation.
- `templates/interaction/complaint.html`: màn hình khiếu nại riêng.
- `controller/ComplaintController.java`, `controller/InteractionController.java`, `service/ReviewService.java`: tách 2 luồng.

## E. Đơn hàng
- `templates/orders/orders.html`: ba icon tiến độ, đánh giá, khiếu nại; nút đánh giá chỉ hoạt động khi hoàn thành.
- `templates/orders/progress.html`: Thymeleaf fragment `details` cho modal, có trạng thái hiện tại và hành động phù hợp.
- `controller/OrderController.java`, `service/OrderService.java`: chi tiết fragment, cập nhật trạng thái qua POST + CSRF + kiểm tra quyền và chuyển trạng thái hợp lệ.
- `static/js/app.js`: cập nhật modal và bảng đơn hàng không tải lại trang.

## F. Đổi mật khẩu
- `templates/auth/change-password.html`, `static/css/app.css`, `static/js/app.js`: icon khóa và nút hiện/ẩn mật khẩu.

## G. Font
- `static/css/app.css`: tăng font cơ bản; vẫn dùng Inter và palette gốc.

## Cách chạy
`mvn test` rồi `mvn spring-boot:run` tại thư mục chứa `pom.xml`; tài khoản demo xem `README.md`.

- `templates/marketplace/provider-detail.html`: thêm nút bắt đầu trò chuyện trực tiếp với Provider.
