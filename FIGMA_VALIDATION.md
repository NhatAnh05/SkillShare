# Kiểm tra tĩnh / Validation
- HTML: 23 file, HTML root/head/body OK
- JavaScript: {'app.js': 'PASS', 'figma-ui.js': 'PASS'}
- CSS top-level parser: OK
- Chưa thể chạy Maven build và Spring Boot runtime trong môi trường này vì lệnh `mvn` không sẵn có. Cần kiểm tra build và các luồng nghiệp vụ trong IntelliJ/JDK 17 trên máy chạy.
- Không tích hợp OAuth, 2FA OTP, KYC, ví, escrow hay thông tin lịch trống theo thời gian thực. Các khu vực này là UI minh họa và được gắn nhãn minh bạch.
