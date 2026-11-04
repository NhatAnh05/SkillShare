# SkillShare — chạy và kiểm tra bản hoàn thiện luồng chức năng

## 1. Mở trên IntelliJ

Giải nén ZIP, mở thư mục `skill-rental` có `pom.xml`. Đây là một dự án duy nhất; không chép đè hai thư mục dự án cũ lên nhau.

Chọn JDK 17, Reload Maven. Chạy `SkillRentalApplication` với Active profiles = `local`.

Hoặc dùng Maven:

```bash
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Profile local lưu database tại `data/skillshare`, tệp tại `private-deliveries`. Giữ cả hai thư mục khi chuyển máy/sao lưu. Không chạy hai tiến trình dùng chung file H2. Profile mặc định dùng H2 in-memory/create-drop: phù hợp kiểm thử mới mỗi lần chạy, dữ liệu mất khi dừng.

Mở `http://localhost:8080/login`.

| Vai trò demo | Email | Mật khẩu |
| --- | --- | --- |
| Customer | customer@skillrental.vn | 123456 |
| Provider | provider1@skillrental.vn | 123456 |
| Admin | admin@skillrental.vn | 123456 |

Các tài khoản này được seed khi database trống; không phải thông tin đăng nhập production. Tin nhắn/thông báo/tài liệu mới chỉ được tạo khi thao tác thật, không có badge hoặc thông báo mô phỏng.

## 2. Kiểm tra chat và thông báo bằng ba phiên độc lập

Dùng ba trình duyệt/profile riêng, hoặc cửa sổ với cookie tách biệt. Đăng nhập lần lượt Customer, Provider, Admin.

1. Customer/Provider vào **Tin nhắn** (`/messages`); nút nổi là chat nhanh, dùng cùng dữ liệu và cơ chế.
2. Customer gửi Provider, rồi Provider trả lời. Cả hai phải thấy ngay, không F5.
3. Customer/Provider bấm Hỗ trợ; Admin vào **Hộp thư hỗ trợ**, nhận ca và mở hội thoại.
4. Admin vào **Liên hệ người dùng**, tìm email/tên/ID, mở và gửi tin cho Provider không cần đơn hàng.
5. Gửi 30–50 tin, cuộn lên đọc tin cũ, gửi thêm từ bên kia. Không tự giật xuống; có nút Tin nhắn mới. Bấm Tải tin nhắn cũ hơn để xem quá 50 mục.
6. Mở thêm tab cùng tài khoản; tin nhắn và trạng thái đọc đồng bộ, không tạo cuộc hội thoại mới.
7. Mở chuông, đánh dấu một hoặc tất cả thông báo đã đọc. Badge giảm; thao tác này không tự đọc các cuộc chat.

## 3. Tài liệu trao đổi và bàn giao

Trong hội thoại mở **Tài liệu và liên kết**, hoặc trong đơn mở **Trao đổi và tài liệu giữa hai bên**.

- Cả Customer và Provider nhập mô tả, chọn tệp hoặc gửi mỗi URL trên một dòng.
- Tài liệu được lưu riêng, có người gửi, thời điểm, dung lượng và đường tải có kiểm tra quyền.
- Khi lỗi, nội dung và file đang chọn được giữ để thử lại. File mới đã ghi sẽ được dọn nếu transaction rollback.
- Tối đa 5 tệp, 10 MB/tệp và 10 liên kết/lần. Hỗ trợ TXT/MD/CSV/JSON/PDF/PNG/JPEG/WebP/DOCX/XLSX/PPTX/ZIP; kiểm tra định dạng/chữ ký/nội dung archive tại backend.
- Đây là tài liệu trao đổi: không tự hoàn tất đơn, không quyết toán hoặc thay đổi tiền.

**Bản bàn giao chính thức** nằm ở mục Sản phẩm bàn giao, chỉ Provider của đơn đang ở trạng thái hợp lệ mới gửi được. Customer kiểm tra, tải tệp và yêu cầu chỉnh sửa. Provider nộp phiên bản mới; các phiên bản cũ còn nguyên. Nghiệm thu và OTP quyết toán vẫn theo luồng cũ.

Không gửi tệp riêng tư qua URL static hoặc đường dẫn ổ đĩa. Chỉ dùng nút tải trong ứng dụng.

## 4. Hỗ trợ và ghi chú Admin

Vào `/admin/support`:

- Tìm theo tên/email, ID hội thoại hoặc ID đơn, trạng thái, ID người phụ trách và khoảng ngày.
- ID người phụ trách `0` nghĩa là chưa phân công. Tìm kiếm hiển thị tối đa 200 kết quả; thu hẹp bộ lọc nếu cần.
- Tiếp nhận, trả về hàng đợi hoặc chuyển ca cho ID Admin khác, kèm lý do. Chỉ người phụ trách hiện tại được chuyển ca.
- Mở phần Ghi chú nội bộ, phân công và hồ sơ liên quan để ghi chú, xem lịch sử, liên kết đơn/khiếu nại và đóng/mở ca.
- Customer/Provider không nhận được ghi chú nội bộ qua API hoặc hội thoại.

Giới hạn Admin: tối đa 10 thao tác mở/liên hệ mỗi phút, 40 tin nhắn/phút. Không có API gửi chat hàng loạt.

Trong `/admin/complaints`, mở mã khiếu nại, **Tiếp nhận xử lý** để gắn người phụ trách. Khi giải quyết/bác, nhập phản hồi; người gửi được thông báo tới đúng trang `/complaints/{id}`. Các thao tác này không tự hoàn tiền hay quyết toán.

## 5. Xem chat riêng theo khiếu nại

Role ADMIN không tự cho quyền xem hội thoại Customer–Provider.

Để sử dụng trong hệ thống thực:

1. Cần ít nhất hai tài khoản Admin đang hoạt động đã được tạo bằng quy trình quản trị tài khoản của đơn vị vận hành.
2. Đặt biến môi trường `SKILLSHARE_REVIEW_AUTHORIZERS` là danh sách email Admin được quyền phê duyệt, phân tách bằng dấu phẩy. Mặc định trống, không ai có quyền duyệt. Không tự cấp quyền bằng nút phía trình duyệt.
3. Admin xử lý phải tiếp nhận khiếu nại. Hội thoại được yêu cầu phải thuộc hai bên của đơn trong khiếu nại.
4. Vào `/admin/support-reviews`, nhập mã hội thoại, mã khiếu nại, lý do và phạm vi tối đa 31 ngày.
5. Một Admin khác trong allowlist phê duyệt hoặc từ chối. Không tự duyệt, kể cả khi người yêu cầu cũng có quyền duyệt.
6. Sau phê duyệt, chỉ Admin yêu cầu được đọc đúng phạm vi, tối đa 2 giờ; bản ghi được tải tiếp mỗi trang 200 tin/tài liệu.
7. Tải tệp hoặc xuất JSON cần bật quyền xuất riêng trong phê duyệt. Khi xuất cần thêm lý do; mỗi lần xem, tải, xuất có audit.

Cấp quyền này không mở API chat thông thường, không đăng ký WebSocket theo dõi private chat và không có quyền sửa/xóa lịch sử. Quyền không dùng được nếu hết hạn hoặc người xử lý khiếu nại đã thay đổi.

Mặc định chỉ có một Admin demo nên không thể tự duyệt quyền riêng. Có thể kiểm thử quy trình hai Admin bằng `BrowserReviewFixture` trong **src/test**, trên database QA riêng; không thêm tài khoản reviewer vào môi trường thật chỉ để chạy demo.

## 6. Điều khoản và chấp nhận

Các trang công khai: `/policies/terms`, `/policies/privacy`, `/policies/provider`, `/policies/booking`, `/policies/cancellation`, `/policies/messages`, `/policies/payments`, `/policies/cookies`.

- Đăng ký vẫn phải đồng ý điều khoản.
- Khi đặt dịch vụ, checkbox đặt dịch vụ không tích sẵn, có link đọc; backend chặn bỏ qua.
- Provider quản lý dịch vụ/xuất bản lại phải chấp nhận quy định liên quan. Có thể chủ động chấp nhận tại `/provider/terms`.
- Khi cùng loại và phiên bản đã được chấp nhận, không yêu cầu lại. Không trộn với lựa chọn marketing.
- `/terms/accepted` xem lịch sử và nội dung snapshot đúng phiên bản thuộc tài khoản.

Không tự tạo luồng đổi vai trò Customer thành Provider: bản nền chỉ có đăng ký Customer. Trang `/provider/terms` phục vụ Provider hiện có/onboarding trước quản lý dịch vụ.

Nội dung vẫn là dự thảo; đơn vị vận hành bổ sung thông tin pháp lý, liên hệ, lưu trữ và điều kiện thực tế trước công bố. Bản chấp nhận lịch sử cũ không có snapshot sẽ báo chưa có bản lưu; không lấy nội dung mới giả làm bản cũ.

## 7. Admin gửi thông báo

Vào `/admin/notifications`, nhập nội dung và chọn một trong bốn đối tượng: tất cả Customer, tất cả Provider, cả hai nhóm một lần hoặc danh sách tài khoản.

Xem trước số người nhận → Gửi → xác nhận. Người nhận phải ACTIVE và đã xác minh email. Danh sách chốt khi gửi; lưu chiến dịch, từng recipient và kết quả phân phối.

“Đã phát tới kết nối” chỉ là máy chủ đã phát WebSocket, không có nghĩa đã đọc. Người offline có hộp thư DB. Thử lại chỉ tác động mục lỗi/chờ; không tự gửi lại chiến dịch khi startup. Chống gửi lặp bằng requestKey và khóa giao dịch.

## 8. MySQL migration và kho file

Trước nâng cấp: sao lưu database và kho file riêng, xác định phiên bản schema. Dự án dùng SQL thủ công, không tự chạy Flyway.

- Nếu DB đang ở v13: chạy **migration-v13-to-v14-workflow-completion.sql**.
- Nếu DB đang ở v12: chạy v12→v13 rồi v13→v14.
- Nếu cũ hơn: áp dụng các migration còn thiếu trong `src/main/resources/db`, đúng thứ tự. Không chạy lại migration đã áp dụng.
- Không chạy SQL MySQL trên H2. Local dùng JPA update; mysql/prod dùng validate.

Migration mới thêm trạng thái/liên kết hỗ trợ, clientKey tin nhắn và các bảng notes, documents, grants, audits, policy snapshots. Không thay đổi tiền hoặc trạng thái tài chính.

Profile prod đặt `skillshare.files.clamav-required=true`. Cần cài/cấu hình daemon ClamAV và lệnh `clamdscan`, hoặc đặt `SKILLSHARE_FILE_SCANNER` trỏ tới executable tương thích. Nếu scan lỗi, quá thời gian hoặc tìm thấy mã độc, upload bị từ chối và không tạo tài liệu. Local có kiểm tra cơ bản nhưng không thay thế antivirus.

Các cấu hình Google/SMTP/VNPay hiện có vẫn đọc biến môi trường; không nhúng file client secret vào source. Muốn thử gửi email/OTP thật phải cấu hình SMTP như trước.

## 9. Chạy lại browser QA

**Chỉ dùng database QA riêng có dữ liệu demo mới.** Script tạo tin, tài liệu, bàn giao, dịch vụ và thông báo thật trong database đó. Không chạy vào MySQL production hoặc database đang có giao dịch người dùng.

Cài công cụ QA trong một thư mục làm việc riêng nếu chưa có:

```bash
npm install --no-save @playwright/test
npx playwright install chromium
```

Chạy ứng dụng với profile mặc định H2 memory, cổng 8080 (khởi động mới mỗi lần). Đặt `SKILLSHARE_QA_BASE_URL=http://localhost:8080` rồi chạy:

```bash
node validation/browser-qa.cjs
```

PowerShell đặt biến môi trường bằng `$env:SKILLSHARE_QA_BASE_URL="http://localhost:8080"`.

Kiểm thử quyền xem chat riêng: chạy main của `BrowserReviewFixture` từ src/test bằng IntelliJ với tham số:

```text
--server.port=18081 --skillshare.support.review-authorizers=reviewer@skillrental.test --skillshare.delivery.storage-dir=./target/review-qa-files
```

Đặt `SKILLSHARE_QA_REVIEW_FIXTURE` thành một đường file JSON tạm hợp lệ trên máy (ví dụ `C:/Temp/skillshare-review-fixture.json`) cho cả tiến trình Java và Node. Nếu dùng Windows, tạo thư mục C:/Temp trước. Lớp fixture tạo dữ liệu QA và ghi metadata vào file này; chỉ tiếp tục sau thông báo BROWSER_REVIEW_FIXTURE_READY. Đặt base URL cổng 18081 rồi chạy `node validation/browser-review-qa.cjs`.

Kết quả đã chạy trong môi trường bàn giao nằm trong validation/*.json và MAVEN_TEST_RESULT.txt. Xem BAO_CAO_HOAN_THIEN.md để đối chiếu từng yêu cầu và các giới hạn triển khai.
