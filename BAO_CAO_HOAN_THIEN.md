# Báo cáo hoàn thiện theo văn bản chat, thông báo, Admin, tài liệu và điều khoản

Ngày: 01/11/2026. Bản này tiếp tục từ mã nguồn Admin Notifications/Policies đã gửi trước, đối chiếu toàn bộ văn bản “SKILLSHARE — SỬA VÀ HOÀN THIỆN CHAT, THÔNG BÁO, ADMIN, BÀN GIAO VÀ ĐIỀU KHOẢN”.

## 1. Kết quả và phạm vi

Đã bổ sung các phần còn thiếu thay vì chỉ giữ các chức năng có sẵn: chat nhận hội thoại mới không phụ thuộc đăng ký cũ, tải lịch sử/giữ vị trí cuộn, tài liệu trao đổi hai chiều, bộ lọc và ghi chú hỗ trợ, chuyển ca có lịch sử, quyền xem chat theo khiếu nại có phê duyệt độc lập, chấp nhận điều khoản theo phiên bản và bản lưu nội dung.

Kết quả kiểm thử: **Maven 96/96 đạt, không bỏ qua; BUILD SUCCESS**. Có kiểm thử giao diện trên Chromium headless với ba phiên đăng nhập có cookie độc lập, thêm tab thứ hai và một phiên Admin phê duyệt độc lập cho luồng xem bằng chứng. Đây là kiểm thử tự động thao tác giao diện thật; không gọi là nghiệm thu thủ công bởi người dùng trên Chrome/Edge/Firefox.

## 2. Đối chiếu văn bản

| Mục | Nội dung thực hiện và bằng chứng |
| --- | --- |
| B1 | WebSocket đăng ký theo User ngay khi kết nối, không đợi danh sách hội thoại cũ. Sự kiện sau commit tới đúng thành viên, mọi tab; kiểm tra lại phân quyền khi phát. UI và test xác nhận hai chiều không F5, hỗ trợ Admin và hội thoại mới. |
| B2 | Tin nhắn flex:none, vùng lịch sử min-height:0/overflow-y riêng, composer cố định theo khung. Giữ vị trí khi đọc tin cũ; báo tin mới thay vì kéo xuống. Kiểm thử 55 tin, desktop và viewport mobile 390px. |
| B3 | Nút Tin nhắn trong Header/Customer Portal mở /messages; nút nổi dùng cho chat nhanh. Cùng widget, service và database cho mọi vai trò. Không tạo hội thoại trùng khi chuyển giao diện. |
| B4 | Customer/Provider mở hoặc tiếp tục hỗ trợ; một support_key cho mỗi người. Admin nhận/nhả/chuyển ca có nhật ký; requester vẫn thấy lịch sử đầy đủ. |
| C1 | Notification dùng lại hệ thống hiện có. Thông báo phát sinh từ nghiệp vụ được lưu trước và phát sau commit; sửa transaction mới cho listener sau commit. |
| C2–C4 | Chat, đơn mới/thay đổi trạng thái, bàn giao, chỉnh sửa, tài liệu, hỗ trợ, phê duyệt hồ sơ/dịch vụ và phản hồi khiếu nại nối vào sự kiện thực. Không tạo sự kiện từ chối dịch vụ giả khi hệ thống chưa có nghiệp vụ từ chối tương ứng. |
| C5 | Có target type/ID, route hội thoại/đơn/khiếu nại/chi tiết thông báo; backend kiểm tra quyền tại trang đích. Khiếu nại mới dẫn tới hồ sơ thật, phản hồi Admin dẫn tới đúng complaint ID. |
| C6 | Đánh dấu một/tất cả thông báo đã đọc chỉ tác động hộp thư hiện tại và đồng bộ tab. Đọc thông báo không đánh dấu tin nhắn chat. GET lịch sử chat không tự đánh dấu đã đọc; UI gửi thao tác read khi hội thoại đang mở, tab đang hiển thị và ở gần cuối. |
| C7 | Badge lấy số thực từ API/DB/sự kiện; chat sidebar và Header dùng unread thật; không thêm badge không có nguồn dữ liệu. |
| D1 | Hộp thư hỗ trợ tìm theo tên/email, hội thoại/đơn, trạng thái, người phụ trách và ngày. Hiển thị vai trò, tin mới nhất, số chưa đọc. Giới hạn 200 kết quả mỗi lần tìm, có thể thu hẹp bộ lọc. |
| D2 | Admin tìm người dùng và chủ động liên hệ không cần đơn hàng. Hội thoại được tái sử dụng, lưu DB, phát realtime. Giới hạn 10 thao tác mở/liên hệ mỗi phút và 40 tin nhắn Admin/phút; hành động liên hệ/phân công có audit, tin gửi có sender/thời điểm. |
| D3 | Ghi chú nội bộ chỉ Admin có quyền xử lý đọc được. Liên kết hỗ trợ với đơn/khiếu nại đúng người yêu cầu. Chat riêng vẫn không mở bằng role ADMIN; cần case được phân công, lý do, phạm vi ngày, Admin khác có quyền duyệt, hạn 2 giờ. Có nhật ký xem, tải tệp, xuất; export cần quyền riêng và lý do. |
| E1–E3 | WorkDocument độc lập DeliverySubmission. Hai bên gửi mô tả/file/link trong đơn hoặc hội thoại; không tạo bản bàn giao thay Provider và không đổi trạng thái/tiền do gửi tài liệu. |
| E4 | Trang đơn có tài liệu trao đổi, bản bàn giao, phiên bản và yêu cầu chỉnh sửa. Metadata tên/dung lượng/người gửi/thời điểm/tải xuống. Bàn giao và yêu cầu chỉnh sửa cập nhật vùng chi tiết của cả hai bên, không reload toàn bộ trang nhận. |
| E5 | Kho riêng ngoài static, UUID làm khóa, kiểm tra quyền mỗi lần tải, attachment + nosniff/no-store; giới hạn số lượng/dung lượng/loại file, chữ ký ảnh/PDF, archive traversal/bomb/macro/executable. Dọn file nếu transaction rollback. Profile prod bắt buộc ClamAV thành công; không có scanner sẽ từ chối upload. |
| E6 | Giữ state machine Escrow, bắt đầu hai bên, bàn giao đúng Provider/trạng thái và OTP quyết toán. Chỉ bổ sung khóa đơn khi cấp phiên bản để chống nộp đồng thời trùng version. |
| F1 | Tám trang chính sách có nội dung, giữ nội dung dự thảo đã cung cấp và phân biệt thanh toán demo/thực tế. |
| F2 | Đồng ý đăng ký được giữ; bổ sung đồng ý đặt dịch vụ và Provider quản lý/xuất bản lại dịch vụ. Checkbox không tích sẵn; backend kiểm tra. Đồng ý cùng loại/phiên bản không bị hỏi lại; lưu user/time/source/type/version và snapshot. /provider/terms dành cho Provider hiện có; không tự thay đổi vai trò tài khoản hoặc thêm luồng đăng ký Provider chưa có trong ứng dụng nền. |
| F3 | Footer có các liên kết chính sách thực, co dòng trên mobile; xem lại từng nội dung đã chấp nhận bằng snapshot thuộc đúng tài khoản. |
| G–H | Có kiểm thử Maven, integration/security, JavaScript syntax, WebSocket thật và hai bộ browser QA; báo cáo lỗi/nguyên nhân/file/migration/kết quả/giới hạn/hướng dẫn kèm theo. |

Chức năng Admin gửi thông báo theo bốn đối tượng ở bản trước được giữ, kiểm thử lại: một Customer, mọi Customer, mọi Provider, cả hai nhóm một lần và nhiều tài khoản chọn; idempotency, snapshot người nhận, kết quả phân phối, trạng thái đọc và thử lại vẫn dùng cùng hệ thống Notification.

## 3. Lỗi tìm thấy và nguyên nhân

1. **Hội thoại mới có thể không cập nhật phía người nhận**: chỉ subscribe những ID đã tải trước. Đăng ký socket theo User và phát dựa trên quyền hiện tại giải quyết thiếu subscription; UI tự tải lại danh sách mà không reload trang.
2. **Khung hội thoại có lúc trống khi mở**: fetch danh sách lúc socket mở chạy đua với fetch có lựa chọn hội thoại, làm mất thao tác chọn. Giữ/khôi phục yêu cầu chọn khi có phản hồi mới hơn; chặn phản hồi lịch sử của hội thoại đã chuyển.
3. **Thông báo chat/trạng thái có thể không lưu**: callback sau commit tiếp tục dùng transaction đã hoàn tất. Listener nghiệp vụ mở transaction REQUIRES_NEW để lưu Notification và phát sự kiện riêng sau commit mới.
4. **Tin dài làm hỏng thông báo**: Message cho phép 2500 ký tự nhưng Notification chỉ 1200. Lưu toàn bộ tin; thông báo dùng preview tối đa 1200, không gây rollback tài liệu hay mất thông báo.
5. **Cuộn bị nén/giật và lịch sử lớn**: chặn flex shrink của từng tin, giữ scrollTop/scrollHeight, tải 50 tin/lần bằng beforeId. Chỉ auto-scroll khi đang gần cuối.
6. **Đánh dấu đã đọc quá sớm**: GET lịch sử từng tự cập nhật read_at. Tách read thành thao tác riêng có CSRF/kiểm tra thành viên; không mark khi tab ẩn hoặc đang đọc tin cũ.
7. **Admin cũ giữ socket sau chuyển ca**: kiểm tra lại thành viên khi phát, gửi inbox invalidation để xóa hội thoại đã mất quyền; read event chỉ cập nhật hội thoại tương ứng.
8. **Gửi lại vì mất phản hồi có thể trùng tin**: clientKey riêng cho mỗi tin; unique(sender_id,client_key), so payload, khóa conversation khi lưu. Client giữ nội dung nếu gửi thất bại.
9. **Tài liệu Customer và bàn giao bị lẫn/thiếu**: thêm WorkDocument cho tài liệu trao đổi, giữ DeliverySubmission riêng. Dùng chung kiểm tra kho riêng tư cho cả tài liệu và bàn giao.
10. **Chưa có kiểm soát xem chat riêng theo vụ việc**: thêm grant có phê duyệt độc lập, phạm vi, hết hạn và audit; không sửa isParticipant để cho Admin xem mọi chat.
11. **Phản hồi khiếu nại chưa thông báo tới người gửi**: ghi người xử lý, thời điểm/nội dung phản hồi và Notification dẫn đúng hồ sơ; không can thiệp thanh toán hay hoàn tiền.
12. **Logout có thể đua với request hoặc socket còn mở**: HTTP session invalidation đóng socket của đúng phiên, chặn handshake cũ, auth hết hạn trả 401/403 thay vì lỗi 500. Mỗi cookie session được quản lý riêng.
13. **Seed SQL cũ thiếu cột support_status**: thêm DEFAULT OPEN ở entity/migration để dữ liệu seed và bản ghi cũ tương thích.

## 4. Database/migration

Mới: `src/main/resources/db/migration-v13-to-v14-workflow-completion.sql`.

- conversations: support_status, linked_complaint_id.
- messages: client_key và unique(sender_id,client_key).
- Bảng mới: support_notes, work_documents, conversation_review_grants, conversation_access_audits, policy_snapshots.
- Giữ FK, indexes, kho file riêng và constraint tách mục tiêu tài liệu order/conversation.
- Không đổi bảng số dư/thanh toán, quy tắc Escrow, OTP hoặc trường tài chính.

SQL MySQL được cung cấp để chạy thủ công sau các migration trước đó; **chưa chạy SQL này trên MySQL thật trong môi trường này**. H2 kiểm thử tạo schema từ entity. Profile local giữ dữ liệu qua restart; profile mysql/prod tiếp tục validate schema.

## 5. Kiểm thử tự động

Maven: 96 tests, failures 0, errors 0, skipped 0, BUILD SUCCESS. Log tóm tắt theo lớp: validation/MAVEN_TEST_RESULT.txt.

Các lớp mới: WorkflowCompletionTest, SocketSessionRegistryTest. Có kiểm thử UI fixture launcher BrowserReviewFixture trong src/test; lớp này không thuộc runtime của ứng dụng.

Bao gồm: nhận tin chưa subscribe, nhiều socket, không gửi private chat cho Admin; clientKey trùng; lịch sử phân trang; read tách thông báo/chat; tài liệu hai chiều và tải riêng tư; chặn nội dung/file/link nguy hiểm, dọn rollback; CSRF; quyền ghi chú và chuyển ca; nhận ca đồng thời; rate limit; case/approval độc lập/hết hạn/cấm export khi chưa duyệt/audit; lưu/không hỏi lại consent/snapshot; chặn bỏ qua consent; phản hồi khiếu nại; scanner bắt buộc fail closed; session logout đóng đúng socket và handshake trễ.

Các test booking/service trước đây được cập nhật để cung cấp sự đồng ý mới trong những fixture gửi hợp lệ. Test Admin bị khóa được điều chỉnh: actor khóa bị từ chối 403; actor Admin còn hoạt động vẫn không thể khóa/mở khóa tài khoản ADMIN. Không bỏ test để đạt build.

Môi trường thực thi cần nạp Byte Buddy agent sẵn khi chạy Mockito (`-DargLine=-javaagent:<byte-buddy-agent-1.14.19.jar>`); đây là tham số môi trường kiểm thử, không sửa pom để bỏ qua hoặc đổi logic ứng dụng. Sáu JavaScript ứng dụng và hai script browser qua node --check.

## 6. Kiểm thử giao diện thật

Chromium headless 131, Playwright. Ba context đăng nhập độc lập Admin/Customer/Provider, thêm tab Customer thứ hai. Không mock frontend notification/badge và không chặn API nội bộ. Dữ liệu là database H2 của ứng dụng QA; mọi thao tác đi qua form/API, lưu DB và WebSocket thật.

- Three independent browser login contexts
- Customer ↔ Provider realtime without page reload
- Customer support → Admin inbox → realtime reply
- Admin proactively contacts Provider, no order dependency
- 55-message scroll: composer visible, old-message position retained
- Older history loads beyond 50 messages
- Mobile 390px: inbox fits viewport and composer remains usable
- Customer file → Provider realtime document list and authorized download
- Provider link → Customer realtime document list
- Order file exchange, official handoff, revision and second version update live
- Same account receives one chat message in each of two tabs
- One Admin campaign reaches both roles; badges/read-all synchronize across tabs
- Eight policy pages and footer destinations render
- Provider policy consent is explicit and not requested again for the same version
- Booking policy checkbox is linked, required and unchecked
- Admin support search and internal note entry operate through the UI
- Offline notification is visible after login, and read state survives the next login
- No page JavaScript exceptions

Bộ browser-review QA dùng fixture backend trong src/test tạo thêm một Admin reviewer trong database QA riêng, cấu hình allowlist duyệt. Không thêm tài khoản hay API debug vào runtime thật.

- Request is scoped to an assigned complaint; second Admin sees approval request without reload
- Independent allowlisted Admin approves; requester receives live decision
- Approved UI reads scoped private history and attachment; file download is authorized
- Grant does not open ordinary private-chat APIs; Customer cannot access Admin review APIs
- Explicitly approved export produces a downloadable evidence file
- Audit UI records decision, message view, attachment download and export

Kết quả máy đọc: validation/BROWSER_WORKFLOW_RESULT.json và validation/BROWSER_REVIEW_RESULT.json. Script để chạy lại: validation/browser-qa.cjs, validation/browser-review-qa.cjs.

Đã kiểm tra DOM, hành vi nhập/gửi/nhận, kích thước/overflow/composer và trạng thái UI. Chưa có nghiệm thu thủ công bởi người dùng trên Chrome/Edge/Firefox của máy Windows. Không đồng nhất Maven pass với UI pass: hai kết quả được ghi riêng.

## 7. Giới hạn triển khai và dữ liệu

- Chưa thực thi migration trên MySQL thật; cần backup, xác định schema rồi áp dụng theo thứ tự.
- Antivirus prod cần daemon ClamAV khả dụng. Bộ kiểm thử chứng minh fail closed khi scanner lỗi; chưa thử với một daemon ClamAV production thật. Kiểm tra cơ bản ở local không thay cho antivirus.
- Registry WebSocket/executor thuộc một instance; chưa có broker phân tán và chưa thử tải nhiều nghìn kết nối/người nhận. Không tự gửi lại chiến dịch khi startup; bản QUEUED dùng thử lại có audit.
- Review chat riêng bị tắt quyền duyệt mặc định. Cần ít nhất hai Admin thực, cấu hình allowlist reviewer và tiếp nhận khiếu nại trước khi yêu cầu. Đây là kiểm soát phân quyền, không dùng Admin role để tự duyệt.
- Quyền review tối đa 2 giờ/31 ngày nội dung; mỗi lần đọc/xuất tối đa 200 tin, có tải tiếp. Metadata tài liệu cũng phân trang 200 mục/lần; đã kiểm thử tải tiếp với 201 bản ghi.
- Không có endpoint sửa/xóa nội dung lịch sử/audit trong các chức năng mới. Chưa bổ sung một chương trình retention/purge tự động; đơn vị vận hành phải quyết định thời hạn lưu giữ và kiểm soát bản xuất.
- Snapshot bắt đầu từ nội dung đã có ở bản này; không thể tái tạo nội dung lịch sử cũ chưa từng lưu. Nếu một acceptance cũ không có snapshot, hệ thống báo rõ thay vì gán nội dung hiện tại vào phiên bản cũ.
- Điều khoản vẫn là dự thảo; cần bổ sung đơn vị vận hành, liên hệ và thời hạn lưu trữ trước công bố.

## 8. File thay đổi so với bản mã nguồn gửi trước

| Loại | File |
| --- | --- |
| Sửa | `src/main/java/com/skillrental/config/ChatWebSocketConfig.java` |
| Mới | `src/main/java/com/skillrental/config/PolicySnapshotConfig.java` |
| Mới | `src/main/java/com/skillrental/config/SocketSessionLifecycleConfig.java` |
| Sửa | `src/main/java/com/skillrental/controller/ChatWidgetController.java` |
| Sửa | `src/main/java/com/skillrental/controller/ComplaintController.java` |
| Mới | `src/main/java/com/skillrental/controller/ComplaintDetailController.java` |
| Sửa | `src/main/java/com/skillrental/controller/InteractionController.java` |
| Sửa | `src/main/java/com/skillrental/controller/OrderController.java` |
| Mới | `src/main/java/com/skillrental/controller/PolicyConsentController.java` |
| Mới | `src/main/java/com/skillrental/controller/PolicyConsentModel.java` |
| Sửa | `src/main/java/com/skillrental/controller/PolicyController.java` |
| Mới | `src/main/java/com/skillrental/controller/SupportManagementController.java` |
| Mới | `src/main/java/com/skillrental/controller/WorkDocumentController.java` |
| Mới | `src/main/java/com/skillrental/controller/WorkflowErrorAdvice.java` |
| Sửa | `src/main/java/com/skillrental/dto/BookingForm.java` |
| Sửa | `src/main/java/com/skillrental/dto/MessageForm.java` |
| Sửa | `src/main/java/com/skillrental/entity/Conversation.java` |
| Mới | `src/main/java/com/skillrental/entity/ConversationAccessAudit.java` |
| Mới | `src/main/java/com/skillrental/entity/ConversationReviewGrant.java` |
| Sửa | `src/main/java/com/skillrental/entity/Message.java` |
| Mới | `src/main/java/com/skillrental/entity/PolicySnapshot.java` |
| Mới | `src/main/java/com/skillrental/entity/SupportNote.java` |
| Mới | `src/main/java/com/skillrental/entity/WorkDocument.java` |
| Sửa | `src/main/java/com/skillrental/notification/NotificationWebSocketConfig.java` |
| Sửa | `src/main/java/com/skillrental/notification/NotificationWebSocketHandler.java` |
| Sửa | `src/main/java/com/skillrental/repository/ComplaintRepository.java` |
| Mới | `src/main/java/com/skillrental/repository/ConversationAccessAuditRepository.java` |
| Sửa | `src/main/java/com/skillrental/repository/ConversationRepository.java` |
| Mới | `src/main/java/com/skillrental/repository/ConversationReviewGrantRepository.java` |
| Sửa | `src/main/java/com/skillrental/repository/MessageRepository.java` |
| Mới | `src/main/java/com/skillrental/repository/PolicySnapshotRepository.java` |
| Sửa | `src/main/java/com/skillrental/repository/RentalOrderRepository.java` |
| Sửa | `src/main/java/com/skillrental/repository/SupportAssignmentHistoryRepository.java` |
| Mới | `src/main/java/com/skillrental/repository/SupportNoteRepository.java` |
| Sửa | `src/main/java/com/skillrental/repository/UserRepository.java` |
| Mới | `src/main/java/com/skillrental/repository/WorkDocumentRepository.java` |
| Sửa | `src/main/java/com/skillrental/service/AuthService.java` |
| Sửa | `src/main/java/com/skillrental/service/BookingService.java` |
| Sửa | `src/main/java/com/skillrental/service/ChatService.java` |
| Mới | `src/main/java/com/skillrental/service/ComplaintHandlingService.java` |
| Mới | `src/main/java/com/skillrental/service/ConversationReviewService.java` |
| Sửa | `src/main/java/com/skillrental/service/CurrentUserService.java` |
| Sửa | `src/main/java/com/skillrental/service/DeliveryService.java` |
| Sửa | `src/main/java/com/skillrental/service/NotificationAutomationService.java` |
| Sửa | `src/main/java/com/skillrental/service/NotificationService.java` |
| Mới | `src/main/java/com/skillrental/service/PolicyConsentService.java` |
| Mới | `src/main/java/com/skillrental/service/PolicyContent.java` |
| Mới | `src/main/java/com/skillrental/service/PrivateFileStorage.java` |
| Sửa | `src/main/java/com/skillrental/service/ReviewService.java` |
| Mới | `src/main/java/com/skillrental/service/SupportManagementService.java` |
| Mới | `src/main/java/com/skillrental/service/WorkDocumentService.java` |
| Mới | `src/main/java/com/skillrental/websocket/ChatMembershipChangedEvent.java` |
| Sửa | `src/main/java/com/skillrental/websocket/ChatWebSocketHandler.java` |
| Mới | `src/main/java/com/skillrental/websocket/SocketSessionRegistry.java` |
| Sửa | `src/main/java/com/skillrental/workspace/WorkspaceController.java` |
| Sửa | `src/main/resources/application-prod.properties` |
| Sửa | `src/main/resources/application.properties` |
| Mới | `src/main/resources/db/migration-v13-to-v14-workflow-completion.sql` |
| Sửa | `src/main/resources/static/css/app.css` |
| Sửa | `src/main/resources/static/js/app.js` |
| Mới | `src/main/resources/static/js/workflow.js` |
| Sửa | `src/main/resources/templates/booking/booking.html` |
| Sửa | `src/main/resources/templates/booking/payment.html` |
| Sửa | `src/main/resources/templates/customer/profile.html` |
| Sửa | `src/main/resources/templates/fragments.html` |
| Sửa | `src/main/resources/templates/interaction/chat.html` |
| Mới | `src/main/resources/templates/interaction/complaint-detail.html` |
| Sửa | `src/main/resources/templates/interaction/complaint.html` |
| Sửa | `src/main/resources/templates/interaction/inbox.html` |
| Sửa | `src/main/resources/templates/interaction/review.html` |
| Sửa | `src/main/resources/templates/marketplace/search.html` |
| Sửa | `src/main/resources/templates/notifications/all.html` |
| Sửa | `src/main/resources/templates/notifications/detail.html` |
| Sửa | `src/main/resources/templates/orders/orders.html` |
| Sửa | `src/main/resources/templates/orders/progress.html` |
| Sửa | `src/main/resources/templates/policies/accepted.html` |
| Sửa | `src/main/resources/templates/policies/page.html` |
| Mới | `src/main/resources/templates/policies/provider-consent.html` |
| Sửa | `src/main/resources/templates/requests/applies.html` |
| Sửa | `src/main/resources/templates/requests/create.html` |
| Sửa | `src/main/resources/templates/workspace/admin/page.html` |
| Mới | `src/main/resources/templates/workspace/admin/support-reviews.html` |
| Sửa | `src/main/resources/templates/workspace/fragments.html` |
| Sửa | `src/main/resources/templates/workspace/provider/page.html` |
| Sửa | `src/test/java/com/skillrental/AdminCatalogManagementTest.java` |
| Sửa | `src/test/java/com/skillrental/AdminUserManagementTest.java` |
| Mới | `src/test/java/com/skillrental/BrowserReviewFixture.java` |
| Sửa | `src/test/java/com/skillrental/ChatRealtimeIntegrationTest.java` |
| Sửa | `src/test/java/com/skillrental/CustomerProviderAdminTransactionFlowTest.java` |
| Sửa | `src/test/java/com/skillrental/ProviderServiceManagementTest.java` |
| Mới | `src/test/java/com/skillrental/SocketSessionRegistryTest.java` |
| Mới | `src/test/java/com/skillrental/WorkflowCompletionTest.java` |
| Sửa | `src/test/java/com/skillrental/WorkspaceControllerSmokeTest.java` |

Ngoài ra cập nhật README, hướng dẫn, báo cáo và validation. Không đóng gói database, tệp riêng tư, credential, browser binary hoặc target.
