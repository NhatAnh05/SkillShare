package com.skillrental.config;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;

/** Seeds realistic demo records, preserving the three primary demo logins and adding locked test records. */
@Configuration
@RequiredArgsConstructor
public class DemoDataConfig {
    @Bean
    CommandLineRunner seed(
            UserRepository users,
            CustomerProfileRepository customers,
            ProviderProfileRepository providers,
            SkillRepository skills,
            OfferedServiceRepository offeredServices,
            OAuthIdentityRepository oauthIdentities,
            ServiceRequestRepository requests,
            ApplyRepository applies,
            CartRepository carts,
            CartItemRepository cartItems,
            BookingRepository bookings,
            RentalOrderRepository orders,
            RentalOrderItemRepository orderItems,
            RentalOrderStatusHistoryRepository orderHistory,
            PaymentRepository payments,
            PaymentAttemptRepository paymentAttempts,
            RefundRepository refunds,
            UserWalletRepository walletRepository,
            WalletTransactionRepository walletTransactions,
            ReviewRepository reviews,
            ComplaintRepository complaints,
            ConversationRepository conversations,
            MessageRepository messages,
            PasswordEncoder encoder,
            com.skillrental.service.CommissionPolicy commissionPolicy,
            DataSource dataSource) {
        return args -> {
            if (users.count() > 0) return; // Seed only an empty database.
            LocalDateTime now = LocalDateTime.now();

            // Preserve the three demo logins expected by the project.
            User customer = users.save(User.builder()
                    .fullName("Lê Tuấn Anh").email("customer@skillrental.vn")
                    .password(encoder.encode("123456")).phone("0901234567")
                    .emailVerified(true).accountStatus(AccountStatus.ACTIVE).role(Role.CUSTOMER)
                    .avatarUrl("https://i.pravatar.cc/150?img=33")
                    .bio("Khách hàng demo của SkillShare.").build());
            User provider = users.save(User.builder()
                    .fullName("Nguyễn Văn Minh").email("provider1@skillrental.vn")
                    .password(encoder.encode("123456")).phone("0902345678")
                    .emailVerified(true).accountStatus(AccountStatus.ACTIVE).role(Role.PROVIDER)
                    .avatarUrl("https://i.pravatar.cc/150?img=11")
                    .bio("Lập trình viên backend Java và Spring Boot.").build());
            User admin = users.save(User.builder()
                    .fullName("Nguyễn Văn Admin").email("admin@skillrental.vn")
                    .password(encoder.encode("123456")).phone("0903456789")
                    .emailVerified(true).accountStatus(AccountStatus.ACTIVE).role(Role.ADMIN)
                    .avatarUrl("https://i.pravatar.cc/150?img=68")
                    .bio("Quản trị viên hệ thống SkillShare.").build());

            // Số dư ví demo để kiểm thử đặt dịch vụ/escrow; đây không phải tiền thật.
            walletRepository.save(UserWallet.builder().user(customer)
                    .balance(new BigDecimal("10000000")).lockedAmount(BigDecimal.ZERO).build());
            walletRepository.save(UserWallet.builder().user(provider)
                    .balance(BigDecimal.ZERO).lockedAmount(BigDecimal.ZERO).build());

            customers.save(CustomerProfile.builder().user(customer)
                    .location("TP. Hồ Chí Minh")
                    .preferredSkills("Java, Spring Boot, UI/UX").build());

            // Skills: Digital Marketing and Project Management intentionally remain available to add.
            Skill java = skills.save(Skill.builder().name("Java")
                    .description("Lập trình Java core và hướng đối tượng.").build());
            Skill spring = skills.save(Skill.builder().name("Spring Boot")
                    .description("Phát triển REST API và backend bằng Spring Boot.").build());
            Skill sql = skills.save(Skill.builder().name("SQL & Database")
                    .description("Thiết kế ERD, chuẩn hóa dữ liệu và truy vấn SQL.").build());
            Skill figma = skills.save(Skill.builder().name("Figma")
                    .description("Thiết kế giao diện và prototype.").build());
            Skill uiux = skills.save(Skill.builder().name("UI/UX Design")
                    .description("Thiết kế trải nghiệm và giao diện người dùng.").build());
            skills.save(Skill.builder().name("Digital Marketing")
                    .description("SEO, quảng cáo và nội dung số.").build());
            skills.save(Skill.builder().name("Project Management")
                    .description("Lập kế hoạch và theo dõi tiến độ dự án.").build());

            providers.save(ProviderProfile.builder().user(provider)
                    .approved(true).profileStatus("ACTIVE")
                    .headline("Senior Java / Spring Boot Developer")
                    .hourlyRate(new BigDecimal("250000")).rating(4.5).reviewCount(2)
                    .premium(true)
                    .introduction("Thiết kế database, xây dựng REST API và tối ưu backend Java.")
                    .availability("Thứ 2–Thứ 7, 18:00–22:00")
                    .skills(new LinkedHashSet<>(List.of(java, spring, sql, figma, uiux))).build());

            // Demo placeholder only: it is not a real Google authorization and must not be used as proof of OAuth.
            oauthIdentities.save(OAuthIdentity.builder().user(customer)
                    .identityProvider("GOOGLE")
                    .providerSubject("DEMO-GOOGLE-SUBJECT-CUSTOMER-001")
                    .emailAtProvider("customer@skillrental.vn").build());

            // Service catalogue with service_skills records.
            ServiceCatalog apiService = offeredServices.save(ServiceCatalog.builder()
                    .provider(provider).name("Phát triển REST API bằng Spring Boot")
                    .category("Lập trình Web")
                    .description("Thiết kế REST API, phân quyền, validation, kết nối database và tài liệu endpoint.")
                    .pricingType(ServicePricingType.FIXED).price(new BigDecimal("3200000"))
                    .durationMinutes(420)
                    .coverImageUrl("https://images.unsplash.com/photo-1516321318423-f06f85e504b3")
                    .status(ServiceStatus.PUBLISHED).rating(4.0).reviewCount(1)
                    .publishedAt(now.minusDays(30))
                    .skills(new LinkedHashSet<>(List.of(java, spring, sql))).build());
            ServiceCatalog mentoring = offeredServices.save(ServiceCatalog.builder()
                    .provider(provider).name("Mentoring Java và review code")
                    .category("Lập trình")
                    .description("Hướng dẫn Java, phân tích lỗi và review chất lượng code.")
                    .pricingType(ServicePricingType.HOURLY).price(new BigDecimal("250000"))
                    .durationMinutes(60)
                    .coverImageUrl("https://images.unsplash.com/photo-1519389950473-47ba0277781c")
                    .status(ServiceStatus.PUBLISHED).rating(0.0).reviewCount(0)
                    .publishedAt(now.minusDays(15))
                    .skills(new LinkedHashSet<>(List.of(java, spring))).build());
            ServiceCatalog design = offeredServices.save(ServiceCatalog.builder()
                    .provider(provider).name("Thiết kế UI/UX trên Figma")
                    .category("Thiết kế")
                    .description("Thiết kế giao diện responsive, component và prototype cho website.")
                    .pricingType(ServicePricingType.FIXED).price(new BigDecimal("1500000"))
                    .durationMinutes(240)
                    .coverImageUrl("https://images.unsplash.com/photo-1586717791821-3f44a563fa4c")
                    .status(ServiceStatus.PUBLISHED).rating(5.0).reviewCount(1)
                    .publishedAt(now.minusDays(10))
                    .skills(new LinkedHashSet<>(List.of(figma, uiux))).build());

            ServiceRequest request = requests.save(ServiceRequest.builder()
                    .customer(customer).title("Hoàn thiện website đặt lịch dịch vụ")
                    .requiredSkills("Java, Spring Boot, SQL & Database")
                    .description("Hoàn thiện API đặt lịch, validation và lưu lịch sử đơn hàng.")
                    .budget(new BigDecimal("5000000")).deadline(LocalDate.now().plusDays(14))
                    .status(RequestStatus.OPEN)
                    .skills(new LinkedHashSet<>(List.of(java, spring, sql))).build());
            applies.save(Apply.builder().request(request).provider(provider)
                    .proposal("Hoàn thiện API và thiết kế database trong 7 ngày; bàn giao source cùng tài liệu.")
                    .proposedPrice(new BigDecimal("4500000")).status(ApplyStatus.PENDING)
                    .appliedAt(now.minusHours(3)).build());

            // Cart containing two services.
            Cart cart = carts.save(Cart.builder().customer(customer).status(CartStatus.ACTIVE)
                    .createdAt(now.minusHours(2)).updatedAt(now.minusMinutes(10)).build());
            cartItems.save(CartItem.builder().cart(cart).service(mentoring).quantity(1)
                    .unitPriceSnapshot(new BigDecimal("250000")).addedAt(now.minusMinutes(30)).build());
            cartItems.save(CartItem.builder().cart(cart).service(design).quantity(1)
                    .unitPriceSnapshot(new BigDecimal("1500000")).addedAt(now.minusMinutes(20)).build());

            // Five bookings/orders demonstrate in-progress, completed, pending-payment and cancelled states.
            Booking b1 = bookings.save(booking(customer, provider, apiService, LocalDate.now().minusDays(1),
                    "Hoàn thiện REST API và rà soát quyền truy cập.", new BigDecimal("3200000"), BookingStatus.IN_PROGRESS,
                    now.minusDays(3), null, null));
            RentalOrder o1 = orders.save(order(b1, customer, provider, "3200000", "320000", "2880000",
                    OrderStatus.PROCESSING, now.minusDays(3), null, null, null, null));

            Booking b2 = bookings.save(booking(customer, provider, design, LocalDate.now().minusDays(8),
                    "Thiết kế màn hình trang chủ và trang dịch vụ.", new BigDecimal("1500000"), BookingStatus.COMPLETED,
                    now.minusDays(10), now.minusDays(7), null));
            RentalOrder o2 = orders.save(order(b2, customer, provider, "1500000", "0", "1500000",
                    OrderStatus.COMPLETED, now.minusDays(10), now.minusDays(7), null, null, null));

            Booking b3 = bookings.save(booking(customer, provider, mentoring, LocalDate.now().plusDays(2),
                    "Buổi mentoring Java 60 phút.", new BigDecimal("250000"), BookingStatus.PENDING,
                    now.minusHours(1), null, null));
            RentalOrder o3 = orders.save(order(b3, customer, provider, "250000", "0", "250000",
                    OrderStatus.PENDING_PAYMENT, now.minusHours(1), null, null, null, null));

            Booking b4 = bookings.save(booking(customer, provider, mentoring, LocalDate.now().minusDays(12),
                    "Đơn demo đã hủy và hoàn tiền.", new BigDecimal("250000"), BookingStatus.CANCELLED,
                    now.minusDays(14), null, "Khách thay đổi lịch, hai bên đồng ý hủy."));
            b4.setCancelledBy(customer);
            b4.setCancelledAt(now.minusDays(12));
            b4 = bookings.save(b4);
            RentalOrder o4 = orders.save(order(b4, customer, provider, "250000", "0", "250000",
                    OrderStatus.CANCELLED, now.minusDays(14), null, now.minusDays(12),
                    "Khách thay đổi lịch, hai bên đồng ý hủy.", customer));

            Booking b5 = bookings.save(booking(customer, provider, apiService, LocalDate.now().minusDays(20),
                    "Rà soát và tối ưu API cho phiên bản đầu.", new BigDecimal("2800000"), BookingStatus.COMPLETED,
                    now.minusDays(21), now.minusDays(18), null));
            RentalOrder o5 = orders.save(order(b5, customer, provider, "3200000", "400000", "2800000",
                    OrderStatus.COMPLETED, now.minusDays(21), now.minusDays(18), null, null, null));
            for (RentalOrder settledOrder : List.of(o2, o5)) {
                BigDecimal gross = settledOrder.getStatus() == OrderStatus.CANCELLED
                        ? (settledOrder.getCancellationCompensation() == null ? BigDecimal.ZERO : settledOrder.getCancellationCompensation())
                        : settledOrder.getTotal();
                settledOrder.setPlatformFeeAmount(commissionPolicy.fee(gross));
                settledOrder.setProviderNetAmount(commissionPolicy.providerNet(gross));
                orders.save(settledOrder);
            }

            orderItems.save(orderItem(o1, apiService, provider, b1, apiService.getName(), "3200000"));
            orderItems.save(orderItem(o2, design, provider, b2, design.getName(), "1500000"));
            orderItems.save(orderItem(o3, mentoring, provider, b3, mentoring.getName(), "250000"));
            orderItems.save(orderItem(o4, mentoring, provider, b4, mentoring.getName(), "250000"));
            orderItems.save(orderItem(o5, apiService, provider, b5, apiService.getName(), "3200000"));

            // Khởi tạo sổ ví khớp với các đơn demo cũ: DH1 và DH3 đang ký quỹ;
            // DH2 và DH5 là các dự án lịch sử đã quyết toán trong dữ liệu demo.
            UserWallet customerWallet = walletRepository.findByUser(customer).orElseThrow();
            UserWallet providerWallet = walletRepository.findByUser(provider).orElseThrow();
            customerWallet.setBalance(new BigDecimal("6950000"));
            customerWallet.setLockedAmount(new BigDecimal("3050000"));
            walletRepository.save(customerWallet);
            providerWallet.setBalance(new BigDecimal("4085000"));
            walletRepository.save(providerWallet);
            walletTransactions.save(WalletTransaction.builder().wallet(customerWallet).type("DEMO_TOPUP")
                    .amount(new BigDecimal("14300000")).balanceAfter(new BigDecimal("14300000"))
                    .lockedAfter(BigDecimal.ZERO).description("Số dư khởi tạo phục vụ kiểm thử bản demo; không phải tiền thật.")
                    .createdAt(now.minusDays(20)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(customerWallet).order(o5).type("SETTLEMENT_OUT")
                    .amount(new BigDecimal("-2800000")).balanceAfter(new BigDecimal("11500000"))
                    .lockedAfter(BigDecimal.ZERO).description("Lịch sử demo: quyết toán dự án DH" + o5.getId())
                    .createdAt(now.minusDays(18)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(providerWallet).order(o5).type("SETTLEMENT_IN")
                    .amount(new BigDecimal("2660000")).balanceAfter(new BigDecimal("2660000"))
                    .lockedAfter(BigDecimal.ZERO).description("Tiền nhận từ dự án DH" + o5.getId() + " - " + b5.getServiceName())
                    .createdAt(now.minusDays(18)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(customerWallet).order(o2).type("SETTLEMENT_OUT")
                    .amount(new BigDecimal("-1500000")).balanceAfter(new BigDecimal("10000000"))
                    .lockedAfter(BigDecimal.ZERO).description("Lịch sử demo: quyết toán dự án DH" + o2.getId())
                    .createdAt(now.minusDays(7)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(providerWallet).order(o2).type("SETTLEMENT_IN")
                    .amount(new BigDecimal("1425000")).balanceAfter(new BigDecimal("4085000"))
                    .lockedAfter(BigDecimal.ZERO).description("Tiền nhận từ dự án DH" + o2.getId() + " - " + b2.getServiceName())
                    .createdAt(now.minusDays(7)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(customerWallet).order(o1).type("HOLD")
                    .amount(new BigDecimal("2800000")).balanceAfter(new BigDecimal("7200000"))
                    .lockedAfter(new BigDecimal("2800000")).description("Khóa tiền cho dự án DH" + o1.getId())
                    .createdAt(now.minusDays(3)).build());
            walletTransactions.save(WalletTransaction.builder().wallet(customerWallet).order(o3).type("HOLD")
                    .amount(new BigDecimal("250000")).balanceAfter(new BigDecimal("6950000"))
                    .lockedAfter(new BigDecimal("3050000")).description("Khóa tiền cho yêu cầu DH" + o3.getId() + "; chờ Provider nhận việc.")
                    .createdAt(now.minusHours(1)).build());

            orderHistory.save(history(o1, "PAID", "PROCESSING", provider, "Provider bắt đầu thực hiện dịch vụ.", now.minusDays(2)));
            orderHistory.save(history(o2, "PENDING_PAYMENT", "PAID", customer, "Thanh toán thành công.", now.minusDays(10)));
            orderHistory.save(history(o2, "PAID", "PROCESSING", provider, "Bắt đầu thiết kế giao diện.", now.minusDays(9)));
            orderHistory.save(history(o2, "PROCESSING", "COMPLETED", provider, "Đã bàn giao file thiết kế.", now.minusDays(7)));
            orderHistory.save(history(o3, null, "PENDING_PAYMENT", customer, "Đơn hàng chờ thanh toán.", now.minusHours(1)));
            orderHistory.save(history(o4, "PAID", "CANCELLED", customer, "Đã xác nhận hủy đơn.", now.minusDays(12)));
            orderHistory.save(history(o5, "PAID", "PROCESSING", provider, "Bắt đầu rà soát API.", now.minusDays(20)));
            orderHistory.save(history(o5, "PROCESSING", "COMPLETED", provider, "Đã bàn giao và nghiệm thu.", now.minusDays(18)));

            Payment pay1 = payments.save(payment(o1, "MOMO", "MOMO", "DEMO-MOMO-TXN-001", "DEMO-PAYMENT-KEY-001",
                    "WELCOME10", "2880000", PaymentStatus.PAID, now.minusDays(3)));
            Payment pay2 = payments.save(payment(o2, "VNPAY", "VNPAY", "DEMO-VNPAY-TXN-002", "DEMO-PAYMENT-KEY-002",
                    null, "1500000", PaymentStatus.PAID, now.minusDays(10)));
            Payment pay3 = payments.save(payment(o3, "VNPAY", "VNPAY", null, "DEMO-PAYMENT-KEY-003",
                    null, "250000", PaymentStatus.PENDING, null));
            Payment pay4 = payments.save(payment(o4, "MOMO", "MOMO", "DEMO-MOMO-TXN-004", "DEMO-PAYMENT-KEY-004",
                    null, "250000", PaymentStatus.REFUNDED, now.minusDays(14)));
            Payment pay5 = payments.save(payment(o5, "BANK_TRANSFER", "BANK_TRANSFER", "DEMO-BANK-TXN-005", "DEMO-PAYMENT-KEY-005",
                    null, "2800000", PaymentStatus.PAID, now.minusDays(21)));

            paymentAttempts.save(paymentAttempt(pay1, "MOMO", "DEMO-MERCHANT-001", "DEMO-MOMO-TXN-001", "2880000", PaymentAttemptStatus.SUCCEEDED, now.minusDays(3), now.minusDays(3)));
            paymentAttempts.save(paymentAttempt(pay2, "VNPAY", "DEMO-MERCHANT-002", "DEMO-VNPAY-TXN-002", "1500000", PaymentAttemptStatus.SUCCEEDED, now.minusDays(10), now.minusDays(10)));
            paymentAttempts.save(paymentAttempt(pay3, "VNPAY", "DEMO-MERCHANT-003", null, "250000", PaymentAttemptStatus.CREATED, now.minusMinutes(30), null));
            paymentAttempts.save(paymentAttempt(pay4, "MOMO", "DEMO-MERCHANT-004", "DEMO-MOMO-TXN-004", "250000", PaymentAttemptStatus.SUCCEEDED, now.minusDays(14), now.minusDays(14)));
            paymentAttempts.save(paymentAttempt(pay5, "BANK_TRANSFER", "DEMO-MERCHANT-005", "DEMO-BANK-TXN-005", "2800000", PaymentAttemptStatus.SUCCEEDED, now.minusDays(21), now.minusDays(21)));

            refunds.save(Refund.builder().payment(pay1).requestedBy(customer)
                    .amount(new BigDecimal("500000"))
                    .reason("Khách đề nghị hoàn một phần do phạm vi bàn giao thay đổi.")
                    .status(RefundStatus.REQUESTED).requestedAt(now.minusHours(5)).build());
            refunds.save(Refund.builder().payment(pay4).requestedBy(customer).processedByAdmin(admin)
                    .amount(new BigDecimal("250000"))
                    .reason("Hoàn tiền cho đơn đã hủy theo thỏa thuận hai bên.")
                    .status(RefundStatus.SUCCEEDED).gatewayRefundId("DEMO-REFUND-004")
                    .adminNote("Bản ghi hoàn tiền demo, không phát sinh giao dịch thật.")
                    .requestedAt(now.minusDays(12)).processedAt(now.minusDays(12)).build());

            reviews.save(Review.builder().order(o2).service(design).customer(customer).provider(provider)
                    .rating(5).comment("Thiết kế chỉn chu, đúng yêu cầu và bàn giao đúng hạn.")
                    .providerResponse("Cảm ơn bạn đã tin tưởng và phản hồi tích cực!")
                    .createdAt(now.minusDays(7)).updatedAt(now.minusDays(7)).build());
            reviews.save(Review.builder().order(o5).service(apiService).customer(customer).provider(provider)
                    .rating(4).comment("API hoạt động tốt, tài liệu rõ ràng; cần thêm ghi chú triển khai.")
                    .providerResponse("Cảm ơn góp ý, mình đã bổ sung ghi chú triển khai.")
                    .createdAt(now.minusDays(18)).updatedAt(now.minusDays(18)).build());

            complaints.save(Complaint.builder().customer(customer).reporter(customer).reportedUser(provider)
                    .order(o1).complaintType(ComplaintType.ORDER)
                    .subject("Yêu cầu cập nhật tiến độ đơn hàng")
                    .description("Khách muốn Admin hỗ trợ xác nhận các hạng mục còn lại và thời gian bàn giao.")
                    .status(ComplaintStatus.OPEN).createdAt(now.minusHours(6)).build());
            complaints.save(Complaint.builder().customer(customer).reporter(customer).reportedUser(provider).handledBy(admin)
                    .order(o4).complaintType(ComplaintType.PAYMENT)
                    .subject("Xác nhận hoàn tiền đơn đã hủy")
                    .description("Khách gửi yêu cầu kiểm tra trạng thái hoàn tiền của đơn demo đã hủy.")
                    .status(ComplaintStatus.RESOLVED)
                    .resolutionNotes("Đã ghi nhận bản ghi hoàn tiền trong bảng refunds.")
                    .createdAt(now.minusDays(12)).resolvedAt(now.minusDays(12)).build());

            Conversation conversation = conversations.save(Conversation.builder()
                    .customer(customer).provider(provider).createdAt(now.minusDays(2)).build());
            messages.save(Message.builder().conversation(conversation).sender(provider)
                    .content("Chào bạn, mình đã xem yêu cầu và đang rà soát các endpoint.")
                    .sentAt(now.minusMinutes(20)).build());
            messages.save(Message.builder().conversation(conversation).sender(customer)
                    .content("Bạn gửi giúp mình danh sách endpoint và thời gian bàn giao dự kiến nhé.")
                    .sentAt(now.minusMinutes(18)).build());
            messages.save(Message.builder().conversation(conversation).sender(provider)
                    .content("Mình sẽ gửi tài liệu API và bản cập nhật tiến độ trong hôm nay.")
                    .sentAt(now.minusMinutes(15)).build());

            // Expand the demo database so every table has at least 50 records.
            // The three primary login credentials remain unchanged; synthetic users are LOCKED.
            DatabasePopulatorUtils.execute(
                    new ResourceDatabasePopulator(new ClassPathResource("db/seed-expand-demo-data.sql")),
                    dataSource);
            restartH2Identities(dataSource);
        };
    }

    /** Explicit seed IDs are used by the expansion SQL; advance H2 identity generators afterward. */
    private void restartH2Identities(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toUpperCase().contains("H2")) {
                return; // MySQL advances AUTO_INCREMENT automatically for explicit IDs.
            }
            String[] identityTables = {
                    "users", "oauth_identities", "customer_profiles", "provider_profiles", "skills", "services",
                    "service_requests", "applies", "carts", "cart_items", "bookings", "rental_orders",
                    "rental_order_items", "rental_order_status_history", "payments", "payment_attempts", "refunds",
                    "reviews", "complaints", "conversations", "messages", "user_wallets", "wallet_transactions", "provider_withdrawals"
            };
            try (Statement statement = connection.createStatement()) {
                for (String table : identityTables) {
                    long nextId;
                    try (var rs = statement.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table)) {
                        rs.next();
                        nextId = rs.getLong(1);
                    }
                    statement.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + nextId);
                }
            }
        }
    }

    private Booking booking(User customer, User provider, ServiceCatalog service, LocalDate date,
                            String note, BigDecimal amount, BookingStatus status,
                            LocalDateTime createdAt, LocalDateTime completedAt, String cancellationReason) {
        Booking.BookingBuilder builder = Booking.builder().customer(customer).provider(provider).service(service)
                .serviceName(service.getName()).bookingDate(date).timeSlot(LocalTime.of(19, 0))
                .note(note).premium(false).directBooking(true).amount(amount).status(status)
                .createdAt(createdAt).updatedAt(completedAt == null ? createdAt : completedAt);
        if (cancellationReason != null) builder.cancellationReason(cancellationReason);
        return builder.build();
    }

    private RentalOrder order(Booking booking, User customer, User provider, String subtotal,
                              String discount, String total, OrderStatus status, LocalDateTime createdAt,
                              LocalDateTime completedAt, LocalDateTime cancelledAt,
                              String cancellationReason, User cancelledBy) {
        BigDecimal totalAmount = new BigDecimal(total);
        OrderStatus effectiveStatus = status == OrderStatus.PENDING_PAYMENT
                ? OrderStatus.PENDING_PROVIDER_ACCEPTANCE : status;
        RentalOrder.RentalOrderBuilder builder = RentalOrder.builder().booking(booking).customer(customer).provider(provider)
                .subtotal(new BigDecimal(subtotal)).discount(new BigDecimal(discount)).total(totalAmount)
                .status(effectiveStatus).createdAt(createdAt).completedAt(completedAt).cancelledAt(cancelledAt)
                .cancellationReason(cancellationReason).cancelledBy(cancelledBy);
        if (effectiveStatus == OrderStatus.PROCESSING) {
            builder.lockedAmount(totalAmount).providerAccepted(true).customerStarted(true).providerStarted(true)
                    .startDepositAmount(totalAmount.divide(new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP));
        } else if (effectiveStatus == OrderStatus.PENDING_PROVIDER_ACCEPTANCE) {
            builder.lockedAmount(totalAmount);
        } else if (effectiveStatus == OrderStatus.COMPLETED) {
            builder.settledAt(completedAt);
        }
        return builder.build();
    }

    private RentalOrderItem orderItem(RentalOrder order, ServiceCatalog service, User provider,
                                      Booking booking, String name, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return RentalOrderItem.builder().order(order).service(service).provider(provider).booking(booking)
                .serviceNameSnapshot(name).quantity(1).unitPrice(price).lineTotal(price).build();
    }

    private RentalOrderStatusHistory history(RentalOrder order, String previous, String next,
                                             User changedBy, String note, LocalDateTime at) {
        return RentalOrderStatusHistory.builder().order(order).previousStatus(previous).newStatus(next)
                .changedBy(changedBy).note(note).createdAt(at).build();
    }

    private Payment payment(RentalOrder order, String method, String gateway, String transactionId,
                            String idempotencyKey, String coupon, String amount, PaymentStatus status,
                            LocalDateTime paidAt) {
        return Payment.builder().order(order).method(method).gatewayName(gateway)
                .gatewayTransactionId(transactionId).idempotencyKey(idempotencyKey).couponCode(coupon)
                .amount(new BigDecimal(amount)).status(status).paidAt(paidAt).build();
    }

    private PaymentAttempt paymentAttempt(Payment payment, String gateway, String merchantReference,
                                          String transactionId, String amount, PaymentAttemptStatus status,
                                          LocalDateTime initiatedAt, LocalDateTime completedAt) {
        return PaymentAttempt.builder().payment(payment).attemptNumber(1).gatewayName(gateway)
                .merchantReference(merchantReference).gatewayTransactionId(transactionId)
                .idempotencyKey("DEMO-ATTEMPT-KEY-" + merchantReference)
                .amount(new BigDecimal(amount)).currency("VND").status(status)
                .responseCode(status == PaymentAttemptStatus.SUCCEEDED ? "00" : null)
                .initiatedAt(initiatedAt).completedAt(completedAt).build();
    }
}
