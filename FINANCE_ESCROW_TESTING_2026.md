# SkillShare — Hướng dẫn kiểm thử ví, Escrow, phí sàn và báo cáo

> Môi trường: demo nội bộ trên Spring Boot/H2. Nạp ví và rút tiền hiện chỉ thay đổi dữ liệu nội bộ; không có giao dịch ngân hàng thật. Khi SMTP chưa cấu hình, OTP demo có thể hiển thị trên giao diện.

## 1. Quy tắc tài chính thống nhất

Tỷ lệ phí sàn lấy từ `skillshare.commission.rate` trong `src/main/resources/application.properties` (mặc định `0.05`, tương đương 5%). Phí chỉ được ghi nhận khi quyết toán, áp dụng cùng một `CommissionPolicy` cho:

- Đơn hoàn thành bình thường.
- Khoản chi phí hủy mà Customer đã chấp nhận sau khi Provider bắt đầu làm.

Ví dụ với giá trị quyết toán 1.000.000đ:

| Khoản | Số tiền |
|---|---:|
| Giá trị quyết toán | 1.000.000đ |
| Hoa hồng sàn 5% | 50.000đ |
| Provider thực nhận 95% | 950.000đ |

Ví dụ nếu hai bên đồng ý chi phí phần việc là 300.000đ trong một đơn có 1.000.000đ đang ký quỹ:

| Khoản | Số tiền |
|---|---:|
| Phần chi phí đã thống nhất | 300.000đ |
| Phí sàn 5% | 15.000đ |
| Provider thực nhận | 285.000đ |
| Ký quỹ hoàn lại Customer | 700.000đ |

Đối chiếu Admin: giá trị phí sàn trong biểu đồ/tổng quan phải bằng tổng `platform_fee_amount` của các đơn được quyết toán trong khoảng ngày đã chọn. Đối chiếu Provider: cột “Phí sàn” và “Provider thực nhận” của cùng mã đơn phải khớp đúng quy tắc trên.

## 2. Ví Customer: khả dụng và đang khóa ký quỹ

Mở `/customer/wallet` với `customer@skillrental.vn` / `123456`.

- **Số tiền còn lại sau khi khóa** = số dư khả dụng; đây là phần có thể dùng để đặt dịch vụ khác.
- **Số tiền đã khóa** = khoản tạm giữ trong Escrow, không thể dùng cho đơn khác.

Ca kiểm thử:

1. Ghi lại hai số liệu hiển thị ban đầu: ví khả dụng và tiền đang khóa ký quỹ.
2. Nạp một khoản thử nghiệm từ form “Nạp số dư thử nghiệm”. Đây chỉ là tiền demo, không phải tiền được chuyển từ ngân hàng.
3. Tạo yêu cầu thuê Provider có giá trị nhỏ hơn số dư khả dụng. Sau khi tạo, số khả dụng phải giảm đúng giá trị đơn và số đang khóa phải tăng đúng giá trị đó.
4. Thử đặt đơn lớn hơn số dư khả dụng. Hệ thống phải từ chối đặt và hướng dẫn nạp thêm số dư.
5. Khi Provider từ chối hoặc Customer hủy trước lúc hai bên xác nhận bắt đầu, số tiền khóa phải được mở khóa và trở về số dư khả dụng.

## 3. Kiểm thử toàn bộ vòng đời công việc và quyết toán

1. Đăng nhập Customer, tạo đơn. Trạng thái ban đầu phải là chờ Provider chấp nhận, chưa phải hoàn thành.
2. Đăng nhập Provider (`provider1@skillrental.vn` / `123456`), mở `/provider/bookings`, bấm **Nhận yêu cầu**. Thử từ chối một đơn đang chờ: toàn bộ ký quỹ phải được mở khóa.
3. Cả Customer và Provider lần lượt xác nhận bắt đầu. Chỉ sau khi cả hai xác nhận, đơn mới chuyển sang đang thực hiện.
4. Provider bấm **Báo hoàn thành**. Đơn phải chuyển sang chờ Customer nghiệm thu; chưa chuyển tiền cho Provider.
5. Customer mở tiến độ đơn, bấm **Nghiệm thu và nhận OTP**. Với SMTP chưa cấu hình và `skillshare.escrow.show-demo-otp=true`, OTP thử nghiệm sẽ hiện ở trang tiến độ. Nhập mã đó trong ô OTP.
6. Sau OTP hợp lệ, đơn phải hoàn thành; tiền khả dụng Customer giảm theo giá trị quyết toán; khoản ký quỹ được giải tỏa; ví Provider tăng đúng 95% khoản quyết toán theo cấu hình 5%; Admin ghi nhận 5% phí sàn.
7. Thử nhập sai OTP. Không được quyết toán; số lần nhập sai phải được giữ lại. Dùng nút **Gửi lại mã OTP** để nhận mã mới trong chế độ demo.

## 4. Kiểm thử hủy và hoàn tiền

- **Hủy trước khi bắt đầu:** Customer hủy hoặc Provider từ chối/hủy trước khi hai bên bắt đầu; ký quỹ được hoàn 100%.
- **Customer hủy sau khi bắt đầu:** đơn chuyển sang chờ Provider đề xuất chi phí. Provider nhập khoản chi phí và giải thích phần việc đã làm. Customer chấp nhận thì áp dụng phí sàn lên khoản chi phí đó, trả phần ký quỹ dư về ví Customer. Nếu Customer từ chối, không quyết toán và đơn trở về đang thực hiện để xử lý tiếp.
- **Provider hủy:** mặc định hoàn 100% khoản ký quỹ cho Customer.

## 5. Admin: Escrow và doanh thu hoa hồng

Mở `/admin/dashboard` hoặc `/admin/finance` với `admin@skillrental.vn` / `123456`.

1. Chọn ngày bắt đầu và kết thúc rồi nhấn **Lọc dòng tiền** hoặc **Xem giai đoạn**.
2. Biểu đồ dòng tiền hiển thị nạp ví demo và số tiền Provider thực nhận trong giai đoạn đó.
3. Biểu đồ doanh thu hoa hồng sử dụng cùng khoảng thời gian, chỉ tính các khoản đã quyết toán.
4. Đối chiếu một mã đơn với trang ví Provider. Ví dụ giá trị quyết toán 1.000.000đ thì phí Admin là 50.000đ và Provider thực nhận 950.000đ theo mặc định 5%.
5. Mở `/admin/orders`: bảng đối soát hiển thị giá trị hợp đồng, tiền đang khóa, giá trị quyết toán, phí sàn và Provider thực nhận theo cùng mã đơn. Nút **Xuất đối soát đơn hàng CSV** tải danh sách đơn này; không dùng nhầm CSV báo cáo vi phạm.
6. `/admin/statistics` cũng có bộ lọc khoảng ngày và hai biểu đồ thống kê hệ thống.

Phí sàn được lưu trong `rental_orders.platform_fee_amount` và doanh thu Provider được lưu trong `rental_orders.provider_net_amount`; cả hai được ghi cùng lúc tại quyết toán. Bảng ví Provider hiển thị theo từng đơn/dự án và lấy mức phí từ cùng chính sách.

## 6. Provider: doanh thu theo dự án và yêu cầu rút

Mở `/provider/wallet`.

- Đối chiếu từng mã đơn, dịch vụ/dự án, cơ sở tính phí, phí sàn, Provider thực nhận, tiền còn khóa và thời gian quyết toán.
- Tổng thu nhập lũy kế chỉ tính phần Provider thực nhận của các đơn đã quyết toán, không cộng đơn mới đang khóa.
- Tạo yêu cầu rút tiền (tối thiểu 2.000.000đ) và điền ngân hàng, số tài khoản, tên chủ tài khoản.
- Khi SMTP hoạt động, OTP được gửi đến email tài khoản Provider. Khi SMTP chưa cấu hình và `skillshare.escrow.show-demo-otp=true`, trang sẽ hiện OTP demo.
- Sai OTP không được duyệt; gửi lại OTP không xóa bộ đếm nhập sai. Bấm **Hủy yêu cầu / hoàn ví** trước khi xác minh để hoàn lại tiền đã giữ.
- Sau OTP thành công, Admin vào `/admin/finance` để **Duyệt demo** hoặc từ chối. Duyệt demo chỉ đổi trạng thái nội bộ, không chuyển khoản ngân hàng thật; từ chối sẽ hoàn tiền vào số dư Provider.
- Nếu SMTP không gửi được và chế độ OTP demo bị tắt, yêu cầu rút bị hủy và tiền tự động hoàn lại; trang không giữ lại khung OTP cho yêu cầu đã hủy.

## 7. Dịch vụ được đặt nhiều

Mở `/marketplace`, tìm khối **Dịch vụ được đặt nhiều**. Danh sách lấy từ đơn/dịch vụ trong dữ liệu, bỏ đơn đã hủy và sắp xếp theo số lượt đặt giảm dần; khi bằng lượt đặt thì ưu tiên xếp hạng Provider. Bộ dữ liệu seed vẫn có các đơn mẫu, nên chúng có thể ảnh hưởng thứ hạng ban đầu. Đặt thử một dịch vụ rồi kiểm tra nó được liên kết với booking/đơn; sau khi làm mới trang, lượt đặt phải phản ánh đơn không bị hủy.

## 8. Đánh giá dịch vụ / Provider

1. Chỉ với đơn đã hoàn thành, Customer mở `/orders` và chọn **Đánh giá**.
2. Lưu điểm và nhận xét; điểm trung bình và số lượng đánh giá Provider được tính lại từ đánh giá đã lưu.
3. Đăng nhập Provider, mở `/provider/reviews` và gửi phản hồi cho đánh giá của mình.
4. Đơn chưa hoàn thành không được đánh giá. Hãy dùng tài khoản Customer tạo một đơn test rồi hoàn tất luồng Escrow trước.

## 9. Báo cáo Customer / Provider và Admin

1. Customer: mở `/orders`, bấm biểu tượng khiếu nại/báo cáo của đơn liên quan.
2. Provider: mở `/provider/bookings`, bấm **Báo cáo Customer** trên đơn liên quan.
3. Gửi chủ đề và mô tả. Báo cáo được gắn với đơn, người báo cáo, người bị báo cáo và trạng thái trong DB.
4. Admin mở `/admin/reports`: xem đối tượng, loại báo cáo, chi tiết, người báo cáo, thời gian; thử **Đã xử lý** hoặc **Bác báo cáo**.
5. Bấm **Xuất CSV** để kiểm tra báo cáo xuất theo dữ liệu đang có trong DB, không phải ba hàng báo cáo tĩnh mẫu.

## 10. Chạy kiểm thử tự động

Mở thư mục project `skill-rental` trong IntelliJ IDEA và chạy test Maven. Có thể sử dụng terminal trong thư mục project:

```bash
mvn test
```

Các smoke test kiểm tra trang Provider/Admin, biểu đồ với khoảng ngày, báo cáo, ví Customer và danh sách dịch vụ phổ biến; `CommissionPolicyTest` kiểm tra chia 5% phí sàn / 95% Provider thực nhận với quyết toán đầy đủ và bồi hoàn hủy một phần.

**Giới hạn xác minh hiện tại:** môi trường đóng gói không có Maven và chưa có SMTP/tài khoản ngân hàng để chạy tích hợp thật, nên chưa thể xác nhận `mvn test` đã pass ở đây. Chỉ có thể xác nhận nội dung mã nguồn, cấu hình và gói project đã được rà soát tĩnh. Ví demo, nạp demo, OTP demo và rút tiền demo không đại diện giao dịch tiền thật.
