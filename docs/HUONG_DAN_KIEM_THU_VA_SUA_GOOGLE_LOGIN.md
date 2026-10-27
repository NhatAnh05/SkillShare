# Sửa lỗi Google Login — SkillShare

## Những thay đổi

- Không còn hiển thị một lỗi chung cho mọi thất bại OAuth2; thông báo được chia theo trạng thái tài khoản. Console chỉ ghi mã `reasonCode` không chứa credential hay token.
- Email Google **chưa từng đăng ký SkillShare**: tạo `User` (Customer), `OAuthIdentity` và `CustomerProfile` trong cùng transaction.
- Email Google **đã đăng ký SkillShare với mật khẩu**: không tự liên kết. Ứng dụng yêu cầu đăng nhập bằng đúng email và **mật khẩu SkillShare hiện có** trong tối đa 5 phút, rồi lưu OAuthIdentity. Lần tiếp theo có thể dùng Google trực tiếp.
- Chỉ tài khoản Customer đã xác minh, đang ACTIVE được liên kết. Provider/Admin tiếp tục dùng đăng nhập hiện có. Không phá vỡ đăng nhập bằng email/mật khẩu.
- Khi Google không trả `userinfo` (một số tình huống tùy cấu hình), principal vẫn có attributes từ ID Token để tránh NullPointerException.

## Lắp bản vá

Dùng file `SkillShare_Google_Login_Ban_Va.zip` và giải nén vào **thư mục cha của `skill-rental`** đang làm việc để ghi đè các file tương ứng. Sao lưu dự án trước khi ghi đè. Đừng ghi đè `.git` hoặc cài đặt IDE; bản vá không chứa những thư mục này. Hoặc dùng bản ZIP đầy đủ chỉ chứa source ở ngoài.

### Cấu hình Google Cloud

- OAuth client type: **Web application**.
- Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google` (đổi cổng nếu máy chạy cổng khác).
- Với chế độ Testing, thêm Gmail muốn thử vào Audience -> Test users.
- Trong IntelliJ, Run > Edit Configurations > Environment variables, cấu hình hai biến:
  - `GOOGLE_CLIENT_ID` = Client ID từ Google Cloud
  - `GOOGLE_CLIENT_SECRET` = Client Secret từ Google Cloud
- Không đưa Client Secret vào source code, commit, ảnh chụp màn hình hoặc chat. Nếu secret từng được hard-code/commit, thu hồi và tạo secret mới trên Google Cloud.
- Đảm bảo URL nhất quán: mở `http://localhost:8080/login` thay vì đổi qua lại `127.0.0.1` và `localhost` trong cùng phiên.

## Kịch bản thử nghiệm thủ công

1. **Gmail mới**: bật ứng dụng, truy cập `/login`, nhấn Đăng nhập với Google. Kết quả: đăng nhập vào trang marketplace và trong database có User + OAuthIdentity + CustomerProfile.
2. **Đăng nhập lại**: logout, dùng cùng Gmail lần nữa. Kết quả: không tạo user/identity trùng.
3. **Gmail đã đăng ký SkillShare bằng mật khẩu**: nhấn Google, ứng dụng báo đã có tài khoản và yêu cầu đăng nhập mật khẩu. Trong cùng phiên trình duyệt, nhập đúng email và mật khẩu SkillShare. Kết quả: liên kết thành công, sau đó logout và thử lại Google.
4. **Sai tài khoản hoặc hết 5 phút**: bắt đầu bước 3 rồi đăng nhập tài khoản khác/đợi quá 5 phút. Kết quả: không liên kết Google.
5. **Tài khoản LOCKED hoặc chưa được xác minh**: bị từ chối; vẫn không vượt qua khóa bằng Google.
6. **Tài khoản Provider/Admin**: không được cấp quyền qua Google theo chính sách hiện tại.
7. **Đăng nhập mật khẩu truyền thống**: hoạt động bình thường, trừ khi đã bị khóa từ trước.

## Nếu còn lỗi

Quan sát URL sau khi trở lại `/login` và console của IntelliJ:

| `oauthError` | Ý nghĩa |
|---|---|
| `linkRequired` | Email đã có tài khoản Customer, cần nhập mật khẩu SkillShare để liên kết |
| `emailUnverified` | Email trong Google ID Token chưa được xác minh |
| `accountDisabled` | Tài khoản SkillShare không active / chưa xác minh |
| `unsupportedAccount` | Tài khoản không thuộc vai trò Customer |
| `failed` | Vấn đề cấu hình, token, callback, mạng hoặc lỗi khác — xem `reasonCode` trong console |

Nếu `failed`, chỉ gửi **mã `reasonCode`**, stack trace đã che token, và URL lỗi sau redirect, tuyệt đối không gửi Client Secret, authorization code hay Google ID Token.

## Test tự động

Trong IntelliJ mở Maven > Lifecycle > `test`, hoặc máy đã cài Maven thì chạy `mvn test`. Các test bổ sung trong `GoogleOidcLoginTest` xác minh: tạo CustomerProfile, an toàn liên kết tài khoản sau khi kiểm tra mật khẩu, từ chối sai tài khoản, hạn 5 phút và ID token không có userinfo.

**Giới hạn:** Tại môi trường đóng gói bản vá này, không có Maven CLI, không có dependency cache Maven cho Spring Boot để chạy test. Các bài test bổ sung cần được chạy bằng Maven trong IntelliJ của bạn. Chưa thể xác nhận đăng nhập thật với Google từ môi trường này.
