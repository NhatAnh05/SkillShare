package com.skillrental.workspace;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.CommissionPolicy;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.EscrowService;
import com.skillrental.service.AdminCatalogManagementService;
import com.skillrental.service.AdminCatalogManagementService.CatalogManagementException;
import com.skillrental.service.AdminOrderManagementService;
import com.skillrental.service.AdminOrderManagementService.OrderReviewException;
import com.skillrental.service.ProviderServiceManagementService;
import com.skillrental.service.ProviderServiceManagementService.ServiceDetails;
import com.skillrental.service.ProviderServiceManagementService.ServiceManagementException;
import com.skillrental.service.UserManagementService;
import com.skillrental.service.UserManagementService.AccountManagementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequiredArgsConstructor
public class WorkspaceController {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("2000000");
    private static final Set<String> PROVIDER_MODES = Set.of("AVAILABLE", "LIMITED", "OFF");
    private final CurrentUserService current;
    private final EscrowService escrowService;
    private final UserManagementService userManagement;
    private final AdminCatalogManagementService catalogManagement;
    private final AdminOrderManagementService adminOrderManagement;
    private final ProviderServiceManagementService providerServiceManagement;
    private final UserRepository users;
    private final ProviderProfileRepository providers;
    private final OfferedServiceRepository offeredServices;
    private final SkillRepository skills;
    private final ServiceRequestRepository requests;
    private final ApplyRepository applies;
    private final BookingRepository bookings;
    private final RentalOrderRepository orders;
    private final RentalOrderStatusHistoryRepository orderHistory;
    private final PaymentRepository payments;
    private final WalletTransactionRepository walletTransactions;
    private final ProviderWithdrawalRepository providerWithdrawalRepository;
    private final CommissionPolicy commissionPolicy;
    private final ReviewRepository reviews;
    private final ComplaintRepository complaints;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final WorkspaceState state;

    @GetMapping("/workspace")
    public String workspace(Authentication authentication) {
        User u = current.require(authentication);
        return switch (u.getRole()) {
            case ADMIN -> "redirect:/admin/dashboard";
            case PROVIDER -> "redirect:/provider/dashboard";
            default -> "redirect:/marketplace";
        };
    }

    @GetMapping("/provider")
    public String providerHome() { return "redirect:/provider/dashboard"; }

    @GetMapping("/provider/{page}")
    public String providerPage(@PathVariable String page, @RequestParam(required = false) String q, Authentication authentication, Model model) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        prepareProviderBaseModel(model, p, page);

        switch (page) {
            case "dashboard" -> fillProviderDashboard(model, u, p);
            case "profile" -> { }
            case "skills" -> {
                model.addAttribute("allSkills", skills.findAll());
                model.addAttribute("certifications", providerCertifications());
            }
            case "services", "packages" -> {
                model.addAttribute("providerServices", offeredServices.findByProviderOrderByCreatedAtDesc(u));
                model.addAttribute("pricingTypes", ServicePricingType.values());
            }
            case "availability" -> model.addAttribute("schedule", scheduleRows());
            case "offers" -> {
                model.addAttribute("requestQuery", q);
                model.addAttribute("requests", openRequests(q));
            }
            case "applications" -> model.addAttribute("applications", providerApplications(u));
            case "bookings" -> model.addAttribute("providerOrders", providerOrders(u));
            case "wallet" -> fillProviderWallet(model, u);
            case "reviews" -> model.addAttribute("providerReviews", reviews.findByProviderOrderByIdDesc(u));
            case "complaints" -> model.addAttribute("providerComplaints", providerComplaints(u));
            case "chat" -> fillProviderChat(model, u);
            case "fees" -> model.addAttribute("feeInvoices", feeInvoices());
            case "posts" -> model.addAttribute("posts", state.posts());
            default -> {
                model.addAttribute("page", "dashboard");
                model.addAttribute("workspaceTitle", providerPageTitle("dashboard"));
                fillProviderDashboard(model, u, p);
            }
        }
        return "workspace/provider/page";
    }

    @GetMapping("/provider/requests/{id}")
    public String providerRequest(@PathVariable Long id, Authentication authentication, Model model) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        prepareProviderBaseModel(model, p, "request-detail");
        model.addAttribute("request", requests.findById(id).orElseThrow());
        return "workspace/provider/page";
    }

    @GetMapping("/provider/requests/{id}/apply")
    public String providerApply(@PathVariable Long id, Authentication authentication, Model model) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        prepareProviderBaseModel(model, p, "apply");
        model.addAttribute("request", requests.findById(id).orElseThrow());
        return "workspace/provider/page";
    }

    @PostMapping("/provider/requests/{id}/apply")
    public String providerApplySubmit(@PathVariable Long id,
                                      @RequestParam BigDecimal proposedPrice,
                                      @RequestParam int durationDays,
                                      @RequestParam String proposal,
                                      Authentication authentication,
                                      RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        ServiceRequest r = requests.findById(id).orElseThrow();

        if (r.getStatus() != RequestStatus.OPEN) {
            ra.addFlashAttribute("error", "Request này không còn mở để nhận ứng tuyển.");
            return "redirect:/provider/offers";
        }
        if (proposedPrice == null || proposedPrice.signum() <= 0) {
            ra.addFlashAttribute("error", "Báo giá phải lớn hơn 0.");
            return "redirect:/provider/requests/" + id + "/apply";
        }
        if (durationDays <= 0) {
            ra.addFlashAttribute("error", "Thời gian hoàn thành phải lớn hơn 0 ngày.");
            return "redirect:/provider/requests/" + id + "/apply";
        }
        if (proposal == null || proposal.isBlank()) {
            ra.addFlashAttribute("error", "Vui lòng nhập nội dung đề xuất.");
            return "redirect:/provider/requests/" + id + "/apply";
        }

        boolean exists = applies.findAll().stream()
                .filter(Objects::nonNull)
                .anyMatch(a -> a.getRequest() != null
                        && a.getProvider() != null
                        && Objects.equals(a.getRequest().getId(), id)
                        && Objects.equals(a.getProvider().getId(), u.getId()));

        if (exists) {
            ra.addFlashAttribute("error", "Bạn đã gửi báo giá cho Request này.");
        } else {
            applies.save(Apply.builder()
                    .request(r)
                    .provider(u)
                    .proposal(proposal.trim() + "\nThời gian dự kiến: " + durationDays + " ngày")
                    .proposedPrice(proposedPrice)
                    .status(ApplyStatus.PENDING)
                    .appliedAt(LocalDateTime.now())
                    .build());
            ra.addFlashAttribute("success", "Đã gửi hồ sơ ứng tuyển và báo giá.");
        }
        return "redirect:/provider/applications";
    }

    @PostMapping("/provider/profile/save")
    public String providerProfileSave(@RequestParam String headline,
                                      @RequestParam BigDecimal hourlyRate,
                                      @RequestParam(required = false, defaultValue = "") String introduction,
                                      @RequestParam(required = false, defaultValue = "") String availability,
                                      Authentication authentication,
                                      RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        if (headline == null || headline.isBlank()) {
            ra.addFlashAttribute("error", "Chức danh Provider không được để trống.");
            return "redirect:/provider/profile";
        }
        if (hourlyRate == null || hourlyRate.signum() < 0) {
            ra.addFlashAttribute("error", "Đơn giá theo giờ không hợp lệ.");
            return "redirect:/provider/profile";
        }
        p.setHeadline(headline.trim());
        p.setHourlyRate(hourlyRate);
        p.setIntroduction(introduction.trim());
        p.setAvailability(availability.trim());
        providers.save(p);
        ra.addFlashAttribute("success", "Đã lưu hồ sơ Provider.");
        return "redirect:/provider/profile";
    }

    @PostMapping("/provider/mode")
    public String providerMode(@RequestParam String mode, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_MODES.contains(normalized)) {
            ra.addFlashAttribute("error", "Trạng thái nhận việc không hợp lệ.");
            return "redirect:/provider/availability";
        }
        state.setProviderMode(p.getId(), normalized);
        ra.addFlashAttribute("success", "Đã cập nhật trạng thái nhận việc.");
        return "redirect:/provider/availability";
    }

    @PostMapping("/provider/availability/save")
    public String availabilitySave(
            @RequestParam(defaultValue = "24") int leadTimeHours,
            @RequestParam(defaultValue = "3") int maxConcurrent,
            @RequestParam(required = false, defaultValue = "") String note,
            Authentication authentication,
            RedirectAttributes ra
    ) {
        require(authentication, Role.PROVIDER);

        if (leadTimeHours < 1 || leadTimeHours > 168) {
            ra.addFlashAttribute("error", "Thời gian báo trước phải nằm trong khoảng 1-168 giờ.");
            return "redirect:/provider/availability";
        }
        if (maxConcurrent < 1 || maxConcurrent > 20) {
            ra.addFlashAttribute("error", "Số dự án đồng thời phải nằm trong khoảng 1-20.");
            return "redirect:/provider/availability";
        }

        String suffix = note == null || note.isBlank() ? "" : " Ghi chú: " + note.trim();
        ra.addFlashAttribute(
                "success",
                "Đã lưu cấu hình lịch: báo trước " + leadTimeHours
                        + " giờ, tối đa " + maxConcurrent + " dự án đồng thời." + suffix
        );
        return "redirect:/provider/availability";
    }

    @PostMapping("/provider/availability/vacation")
    public String vacationSave(@RequestParam(required = false, defaultValue = "") String from,
                               @RequestParam(required = false, defaultValue = "") String to,
                               @RequestParam(required = false, defaultValue = "") String message,
                               Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        String period = from.isBlank() ? "chưa chọn ngày bắt đầu" : from;
        if (!to.isBlank()) period += " → " + to;
        ra.addFlashAttribute("success", "Đã lưu lịch nghỉ phép: " + period + ".");
        return "redirect:/provider/availability";
    }

    @PostMapping("/provider/skills/{id}/add")
    public String addSkill(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        skills.findById(id).ifPresent(s -> p.getSkills().add(s));
        providers.save(p);
        ra.addFlashAttribute("success", "Đã thêm kỹ năng vào hồ sơ.");
        return "redirect:/provider/skills";
    }

    @PostMapping("/provider/skills/{id}/remove")
    public String removeSkill(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        ProviderProfile p = requireProviderProfile(u);
        p.getSkills().removeIf(s -> Objects.equals(s.getId(), id));
        providers.save(p);
        ra.addFlashAttribute("success", "Đã xóa kỹ năng khỏi hồ sơ.");
        return "redirect:/provider/skills";
    }

    @PostMapping("/provider/services/new")
    public String createProviderService(@RequestParam(required = false) String name,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String description,
                                        @RequestParam(required = false) String pricingType,
                                        @RequestParam(required = false) BigDecimal price,
                                        @RequestParam(required = false) Integer durationMinutes,
                                        @RequestParam(required = false) String coverImageUrl,
                                        Authentication authentication, RedirectAttributes ra) {
        User provider = require(authentication, Role.PROVIDER);
        try {
            providerServiceManagement.create(provider,
                    new ServiceDetails(name, category, description, pricingType, price, durationMinutes, coverImageUrl));
            ra.addFlashAttribute("success", "Đã lưu dịch vụ ở trạng thái nháp. Dịch vụ cần được Admin duyệt trước khi mở bán.");
        } catch (ServiceManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể tạo dịch vụ providerId={}", provider.getId(), e);
            ra.addFlashAttribute("error", "Không thể lưu dịch vụ. Vui lòng thử lại.");
        }
        return "redirect:/provider/services";
    }

    @PostMapping("/provider/services/{id}/edit")
    public String updateProviderService(@PathVariable Long id,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String description,
                                        @RequestParam(required = false) String pricingType,
                                        @RequestParam(required = false) BigDecimal price,
                                        @RequestParam(required = false) Integer durationMinutes,
                                        @RequestParam(required = false) String coverImageUrl,
                                        Authentication authentication, RedirectAttributes ra) {
        User provider = require(authentication, Role.PROVIDER);
        try {
            providerServiceManagement.update(id, provider,
                    new ServiceDetails(name, category, description, pricingType, price, durationMinutes, coverImageUrl));
            ra.addFlashAttribute("success", "Đã cập nhật dịch vụ.");
        } catch (ServiceManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể cập nhật dịch vụ serviceId={} providerId={}", id, provider.getId(), e);
            ra.addFlashAttribute("error", "Không thể lưu thay đổi dịch vụ. Vui lòng thử lại.");
        }
        return "redirect:/provider/services";
    }

    @PostMapping("/provider/services/{id}/pause")
    public String pauseProviderService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        return providerServiceStatusAction(id, authentication, ra,
                user -> providerServiceManagement.pause(id, user), "Đã tạm ngừng niêm yết dịch vụ.");
    }

    @PostMapping("/provider/services/{id}/resume")
    public String resumeProviderService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        return providerServiceStatusAction(id, authentication, ra,
                user -> providerServiceManagement.resume(id, user), "Đã xuất bản lại dịch vụ.");
    }

    @PostMapping("/provider/services/{id}/archive")
    public String archiveProviderService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        return providerServiceStatusAction(id, authentication, ra,
                user -> providerServiceManagement.archive(id, user), "Đã lưu trữ dịch vụ.");
    }

    @PostMapping("/provider/orders/{id}/accept")
    public String providerAcceptOrder(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.providerAccept(id, u);
            ra.addFlashAttribute("success", "Đã nhận yêu cầu. Chờ Customer và Provider xác nhận bắt đầu trước khi thực hiện công việc.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/decline")
    public String providerDeclineOrder(@PathVariable Long id, @RequestParam(required = false) String reason,
                                       Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.providerDecline(id, u, reason);
            ra.addFlashAttribute("success", "Đã từ chối yêu cầu. Toàn bộ khoản ký quỹ được mở khóa và hoàn lại cho Customer.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/start-confirmation")
    public String providerStartConfirmation(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.confirmStart(id, u, Role.PROVIDER);
            ra.addFlashAttribute("success", "Đã ghi nhận xác nhận bắt đầu của Provider. Đơn chỉ chuyển sang đang thực hiện khi Customer cũng xác nhận.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/complete")
    public String providerComplete(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.providerComplete(id, u);
            ra.addFlashAttribute("success", "Đã báo hoàn thành. Tiền vẫn được giữ trong Escrow cho đến khi Customer nghiệm thu và xác nhận OTP.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/cancel")
    public String providerCancel(@PathVariable Long id, @RequestParam(required = false) String reason,
                                 Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.cancelByProvider(id, u, reason);
            ra.addFlashAttribute("success", "Đã hủy đơn. Toàn bộ khoản ký quỹ chưa quyết toán được mở khóa và hoàn lại cho Customer.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/cancellation-compensation")
    public String providerCompensation(@PathVariable Long id, @RequestParam BigDecimal amount,
                                       @RequestParam(required = false) String note,
                                       Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.proposeCancellationCompensation(id, u, amount, note);
            ra.addFlashAttribute("success", "Đã gửi đề xuất chi phí phần việc đã thực hiện. Chờ Customer xác nhận trước khi giải ngân.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/reviews/{id}/respond")
    public String respondReview(@PathVariable Long id, @RequestParam String response,
                                Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        Review r = reviews.findById(id).orElseThrow();
        if (r.getProvider() == null || !Objects.equals(r.getProvider().getId(), u.getId())) {
            throw new IllegalArgumentException("Không có quyền");
        }
        if (response == null || response.isBlank()) {
            ra.addFlashAttribute("error", "Nội dung phản hồi không được để trống.");
            return "redirect:/provider/reviews";
        }
        r.setProviderResponse(response.trim());
        reviews.save(r);
        ra.addFlashAttribute("success", "Đã gửi phản hồi khách hàng.");
        return "redirect:/provider/reviews";
    }

    @PostMapping("/provider/complaints/{id}/evidence")
    public String complaintEvidence(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        Complaint c = complaints.findById(id).orElseThrow();
        if (c.getOrder() == null || c.getOrder().getProvider() == null || !Objects.equals(c.getOrder().getProvider().getId(), u.getId())) throw new IllegalArgumentException("Không có quyền");
        c.setStatus(ComplaintStatus.PROCESSING);
        complaints.save(c);
        ra.addFlashAttribute("success", "Đã gửi bằng chứng giải trình cho Admin.");
        return "redirect:/provider/complaints";
    }

    @PostMapping("/provider/complaints/{id}/refund")
    public String complaintRefund(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        Complaint c = complaints.findById(id).orElseThrow();
        if (c.getOrder() == null || c.getOrder().getProvider() == null || !Objects.equals(c.getOrder().getProvider().getId(), u.getId())) throw new IllegalArgumentException("Không có quyền");
        c.setStatus(ComplaintStatus.RESOLVED);
        complaints.save(c);
        ra.addFlashAttribute("success", "Đã đồng ý phương án hoàn tiền cho khách trong demo.");
        return "redirect:/provider/complaints";
    }

    @PostMapping("/provider/wallet/withdraw")
    public String withdraw(@RequestParam BigDecimal amount, @RequestParam String bankName,
                           @RequestParam String accountNumber, @RequestParam String accountHolder,
                           Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            EscrowService.WithdrawalOtpDispatchResult result = escrowService.createWithdrawal(
                    u, amount, bankName, accountNumber, accountHolder);
            if (result.emailSent()) {
                ra.addFlashAttribute("withdrawalOtpRequestId", result.withdrawalId());
                ra.addFlashAttribute("success", "Đã giữ số tiền yêu cầu rút và gửi OTP tới email " + u.getEmail() + ". Nhập OTP để chuyển yêu cầu cho Admin duyệt.");
            } else if (result.demoCode() != null) {
                ra.addFlashAttribute("withdrawalOtpRequestId", result.withdrawalId());
                ra.addFlashAttribute("info", "SMTP chưa cấu hình. Đây là OTP demo chỉ dùng kiểm thử; nhập mã bên dưới để xác minh yêu cầu rút tiền.");
                ra.addFlashAttribute("withdrawalDemoOtp", result.demoCode());
            } else {
                ra.addFlashAttribute("error", "Không gửi được OTP. Yêu cầu đã bị hủy và tiền đã được hoàn lại vào ví; hãy kiểm tra SMTP hoặc bật OTP demo cho môi trường local.");
            }
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/provider/wallet";
    }

    @PostMapping("/provider/wallet/withdrawals/{id}/verify-otp")
    public String verifyWithdrawalOtp(@PathVariable Long id, @RequestParam String otp,
                                      Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.verifyWithdrawalOtp(id, u, otp);
            ra.addFlashAttribute("success", "OTP hợp lệ. Yêu cầu rút tiền đã chuyển sang Admin kiểm tra. Bản demo chưa chuyển tiền qua ngân hàng thật.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("withdrawalOtpRequestId", id);
        }
        return "redirect:/provider/wallet";
    }

    @PostMapping("/provider/wallet/withdrawals/{id}/resend-otp")
    public String resendWithdrawalOtp(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            EscrowService.WithdrawalOtpDispatchResult result = escrowService.resendWithdrawalOtp(id, u);
            if (result.emailSent()) {
                ra.addFlashAttribute("withdrawalOtpRequestId", result.withdrawalId());
                ra.addFlashAttribute("success", "Đã gửi lại OTP tới " + u.getEmail() + ".");
            } else if (result.demoCode() != null) {
                ra.addFlashAttribute("withdrawalOtpRequestId", result.withdrawalId());
                ra.addFlashAttribute("info", "OTP mới cho môi trường demo được hiển thị bên dưới.");
                ra.addFlashAttribute("withdrawalDemoOtp", result.demoCode());
            } else ra.addFlashAttribute("error", "Không gửi được OTP. Yêu cầu đã bị hủy và tiền đã được hoàn lại vào ví; hãy kiểm tra SMTP.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("withdrawalOtpRequestId", id);
        }
        return "redirect:/provider/wallet";
    }

    @PostMapping("/provider/wallet/withdrawals/{id}/cancel")
    public String cancelWithdrawalOtp(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        try {
            escrowService.cancelUnverifiedWithdrawal(id, u);
            ra.addFlashAttribute("success", "Đã hủy yêu cầu rút chưa xác minh và hoàn lại tiền về số dư khả dụng.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("withdrawalOtpRequestId", id);
        }
        return "redirect:/provider/wallet";
    }

    @PostMapping("/provider/posts")
    public String post(@RequestParam String title,
                       @RequestParam String category,
                       @RequestParam(required = false, defaultValue = "") String content,
                       Authentication authentication,
                       RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        if (title == null || title.isBlank() || category == null || category.isBlank()) {
            ra.addFlashAttribute("error", "Tiêu đề và chuyên mục bài viết không được để trống.");
            return "redirect:/provider/posts";
        }
        state.addPost(title.trim(), category.trim(), content == null ? "" : content.trim());
        ra.addFlashAttribute("success", "Đã lưu bài viết thành bản nháp.");
        return "redirect:/provider/posts";
    }

    @PostMapping("/provider/posts/{id}/delete")
    public String deletePost(@PathVariable String id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        state.deletePost(id);
        ra.addFlashAttribute("success", "Đã xóa bài viết demo.");
        return "redirect:/provider/posts";
    }

    @PostMapping("/provider/fees/pay")
    public String providerFeePay(@RequestParam String method, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        ra.addFlashAttribute("success", "Đã tạo phiên thanh toán gói duy trì qua " + method + " trong demo.");
        return "redirect:/provider/fees";
    }

    @GetMapping("/admin/providers/{id}")
    public String adminProviderDetail(@PathVariable Long id, Authentication authentication, Model model) {
        require(authentication, Role.ADMIN);
        ProviderProfile p = providers.findById(id).orElseThrow();
        model.addAttribute("page", "provider-detail");
        model.addAttribute("workspaceTitle", adminPageTitle("provider-detail"));
        model.addAttribute("admin", current.require(authentication));
        model.addAttribute("providerDetail", p);
        model.addAttribute("providerApproved", Boolean.TRUE.equals(p.getApproved()));
        model.addAttribute("state", state);
        return "workspace/admin/page";
    }

    @GetMapping("/admin")
    public String adminHome() { return "redirect:/admin/dashboard"; }

    @GetMapping("/admin/{page}")
    public String adminPage(@PathVariable String page, @RequestParam(required = false) String q,
                            @RequestParam(required = false) String roleFilter,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Authentication authentication, Model model) {
        User admin = require(authentication, Role.ADMIN);
        List<User> allUsers = users.findAll();
        state.primeDemo(allUsers);
        model.addAttribute("page", page);
        model.addAttribute("workspaceTitle", adminPageTitle(page));
        model.addAttribute("admin", admin);
        model.addAttribute("users", allUsers);
        model.addAttribute("providers", providers.findAll());
        model.addAttribute("skills", skills.findAll());
        List<RentalOrder> adminOrderSource = orders.findAll().stream()
                .sorted(Comparator.comparing(RentalOrder::getId).reversed()).toList();
        model.addAttribute("orders", adminOrderSource);
        model.addAttribute("adminOrderRows", adminOrderSource.stream().map(o -> new AdminOrderFinanceRow(
                o, commissionPolicy.grossSettledAmount(o), commissionPolicy.feeFor(o), commissionPolicy.providerNetFor(o),
                orderHistory.findByOrderIdOrderByCreatedAtAsc(o.getId()))).toList());
        model.addAttribute("reviews", reviews.findAll().stream().sorted(Comparator.comparing(Review::getId).reversed()).toList());
        model.addAttribute("complaints", complaints.findAll().stream().sorted(Comparator.comparing(Complaint::getId).reversed()).toList());
        model.addAttribute("state", state);
        model.addAttribute("adminQuery", q);
        model.addAttribute("roleFilter", roleFilter);

        switch (page) {
            case "dashboard" -> { fillAdminDashboard(model); fillAdminFinance(model, from, to); }
            case "users" -> model.addAttribute("userRows", adminUsers(allUsers.stream().filter(u -> (q == null || q.isBlank()) || u.getFullName().toLowerCase().contains(q.toLowerCase()) || u.getEmail().toLowerCase().contains(q.toLowerCase()) || (u.getPhone() != null && u.getPhone().contains(q))).filter(u -> roleFilter == null || roleFilter.isBlank() || u.getRole().name().equalsIgnoreCase(roleFilter)).toList()));
            case "locked" -> model.addAttribute("lockedRows", allUsers.stream()
                    .filter(u -> u.getAccountStatus() == AccountStatus.LOCKED).toList());
            case "unlock" -> model.addAttribute("unlockRows", allUsers.stream()
                    .filter(u -> u.getAccountStatus() == AccountStatus.LOCKED).toList());
            case "providers" -> model.addAttribute("providerRows", providers.findAll());
            case "pending-providers" -> model.addAttribute("pendingProviders", providers.findAll().stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getApproved())).toList());
            case "skills" -> model.addAttribute("pendingSkills", skills.findAll().stream().filter(s -> !state.isSkillApproved(s.getId())).limit(5).toList());
            case "services" -> model.addAttribute("serviceRows", offeredServices.findAll());
            case "pending-services" -> model.addAttribute("pendingServices", offeredServices.findAll().stream()
                    .filter(service -> service.getStatus() == ServiceStatus.DRAFT).toList());
            case "price-requests" -> model.addAttribute("priceRequests", priceRequests());
            case "blocked-services" -> model.addAttribute("blockedServices", offeredServices.findAll().stream()
                    .filter(service -> service.getStatus() == ServiceStatus.PAUSED).toList());
            case "reviews" -> model.addAttribute("adminReviews", adminReviewRows());
            case "complaints" -> model.addAttribute("complaintRows", adminComplaintRows());
            case "reports" -> model.addAttribute("reportRows", reportRows());
            case "statistics" -> fillAdminStatistics(model, from, to);
            case "finance" -> fillAdminFinance(model, from, to);
            case "packages" -> {
                model.addAttribute("packages", adminPackages());
                model.addAttribute("packageOrders", packageOrders());
            }
        }
        return "workspace/admin/page";
    }

    @PostMapping("/admin/orders/{id}/intervene")
    public String interveneOrder(@PathVariable Long id, @RequestParam(required = false) String note,
                                 Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        try {
            adminOrderManagement.recordReview(id, admin, note);
            ra.addFlashAttribute("success", "Đã lưu ghi chú rà soát đơn hàng vào lịch sử.");
        } catch (OrderReviewException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể ghi nhận rà soát đơn orderId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu ghi chú rà soát. Vui lòng thử lại.");
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/reset-demo")
    public String resetDemo(Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.reset();
        ra.addFlashAttribute("success", "Đã đặt lại trạng thái thao tác demo, không thay đổi dữ liệu database.");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/escrow/check")
    public String escrowCheck(Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        BigDecimal active = orders.findAll().stream().map(RentalOrder::getLockedAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        ra.addFlashAttribute("success", "Đã đối soát demo Escrow. Tổng tiền đang bảo lãnh: " + money(active) + ".");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/users/{id}/lock")
    public String lockUser(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            userManagement.lock(id);
            ra.addFlashAttribute("success", "Đã khóa tài khoản.");
        } catch (AccountManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể khóa tài khoản userId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái tài khoản. Vui lòng thử lại.");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            userManagement.unlock(id);
            ra.addFlashAttribute("success", "Đã mở khóa tài khoản.");
        } catch (AccountManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể mở khóa tài khoản userId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái tài khoản. Vui lòng thử lại.");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/providers/{id}/approve")
    public String approveProvider(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            catalogManagement.approveProvider(id);
            ra.addFlashAttribute("success", "Đã duyệt hồ sơ Provider.");
        } catch (CatalogManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể duyệt Provider profileId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái Provider. Vui lòng thử lại.");
        }
        return "redirect:/admin/pending-providers";
    }

    @PostMapping("/admin/skills/{id}/approve")
    public String approveSkill(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.approveSkill(id);
        ra.addFlashAttribute("success", "Đã duyệt kỹ năng và gắn trạng thái Verified Skill trong demo.");
        return "redirect:/admin/skills";
    }

    @PostMapping("/admin/services/{id}/approve")
    public String approveService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            catalogManagement.approveService(id);
            ra.addFlashAttribute("success", "Đã duyệt và xuất bản dịch vụ.");
        } catch (CatalogManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể duyệt dịch vụ serviceId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái dịch vụ. Vui lòng thử lại.");
        }
        return "redirect:/admin/pending-services";
    }

    @PostMapping("/admin/providers/{id}/suspend")
    public String suspendProvider(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            catalogManagement.suspendProvider(id);
            ra.addFlashAttribute("success", "Đã tạm ngưng Provider.");
        } catch (CatalogManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể tạm ngưng Provider profileId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái Provider. Vui lòng thử lại.");
        }
        return "redirect:/admin/providers";
    }

    @PostMapping("/admin/providers/{id}/reactivate")
    public String reactivateProvider(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        try {
            catalogManagement.reactivateProvider(id);
            ra.addFlashAttribute("success", "Đã kích hoạt lại Provider.");
        } catch (CatalogManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể kích hoạt lại Provider profileId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu trạng thái Provider. Vui lòng thử lại.");
        }
        return "redirect:/admin/providers";
    }

    @PostMapping("/admin/services/{id}/price/approve")
    public String approvePrice(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.approveService(id);
        ra.addFlashAttribute("success", "Đã duyệt điều chỉnh giá trong demo.");
        return "redirect:/admin/price-requests";
    }

    @PostMapping("/admin/services/{id}/price/reject")
    public String rejectPrice(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        ra.addFlashAttribute("success", "Đã từ chối yêu cầu điều chỉnh giá trong demo.");
        return "redirect:/admin/price-requests";
    }

    @PostMapping("/admin/services/{id}/block")
    public String blockService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        ra.addFlashAttribute("error", "Thao tác khóa dịch vụ đang tạm hoãn.");
        return "redirect:/admin/services";
    }

    @PostMapping("/admin/services/{id}/restore")
    public String restoreService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        ra.addFlashAttribute("error", "Thao tác mở khóa dịch vụ đang tạm hoãn.");
        return "redirect:/admin/blocked-services";
    }

    @PostMapping("/admin/reviews/{id}/hide")
    public String hideReview(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.hideReview(id);
        ra.addFlashAttribute("success", "Đã ẩn đánh giá trong workspace demo.");
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/restore")
    public String restoreReview(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.restoreReview(id);
        ra.addFlashAttribute("success", "Đã khôi phục đánh giá.");
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/complaints/{id}/resolve")
    public String resolveComplaint(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        Complaint c = complaints.findById(id).orElseThrow();
        c.setStatus(ComplaintStatus.RESOLVED);
        complaints.save(c);
        state.resolveComplaint(id);
        ra.addFlashAttribute("success", "Đã đóng khiếu nại và ghi nhận phán quyết.");
        return "redirect:/admin/complaints";
    }

    @PostMapping("/admin/complaints/{id}/reject")
    public String rejectComplaint(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        Complaint c = complaints.findById(id).orElseThrow();
        c.setStatus(ComplaintStatus.REJECTED);
        complaints.save(c);
        ra.addFlashAttribute("success", "Đã bác khiếu nại.");
        return "redirect:/admin/complaints";
    }

    @PostMapping("/admin/withdrawals/{id}/approve")
    public String approveWithdrawal(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        try {
            escrowService.approveWithdrawal(id, admin);
            ra.addFlashAttribute("success", "Đã duyệt yêu cầu rút WD" + id + " trong ví nội bộ. Đây không phải lệnh chuyển ngân hàng thật.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/finance";
    }

    @PostMapping("/admin/withdrawals/{id}/reject")
    public String rejectWithdrawal(@PathVariable Long id, @RequestParam(required = false) String reason,
                                   Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        try {
            escrowService.rejectWithdrawal(id, admin, reason);
            ra.addFlashAttribute("success", "Đã từ chối yêu cầu rút WD" + id + " và hoàn lại số tiền về ví Provider.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/finance";
    }

    @PostMapping("/admin/reports/{id}/resolve")
    public String resolveReport(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        Complaint report = complaints.findById(id).orElseThrow();
        report.setStatus(ComplaintStatus.RESOLVED);
        report.setHandledBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNotes("Admin đã xem xét báo cáo trong hệ thống.");
        complaints.save(report);
        ra.addFlashAttribute("success", "Đã ghi nhận xử lý báo cáo #" + id + ".");
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/reports/{id}/reject")
    public String rejectReport(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        Complaint report = complaints.findById(id).orElseThrow();
        report.setStatus(ComplaintStatus.REJECTED);
        report.setHandledBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNotes("Admin bác báo cáo sau khi xem xét.");
        complaints.save(report);
        ra.addFlashAttribute("success", "Đã bác báo cáo #" + id + ".");
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/packages/{code}/save")
    public String savePackage(@PathVariable String code, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.togglePackage("ADMIN-" + code);
        ra.addFlashAttribute("success", "Đã lưu cấu hình gói " + code + " trong demo.");
        return "redirect:/admin/packages";
    }

    @GetMapping("/admin/orders/export")
    public ResponseEntity<byte[]> exportOrders(Authentication authentication) {
        require(authentication, Role.ADMIN);
        StringBuilder csv = new StringBuilder("ID,Customer,Provider,DichVu,GiaTriHopDong,TienDangKhoa,GiaTriQuyetToan,PhiSan,ProviderThucNhan,TrangThai,NgayTao,NgayQuyetToan\n");
        for (RentalOrder order : orders.findAll().stream().sorted(Comparator.comparing(RentalOrder::getId)).toList()) {
            String serviceName = order.getBooking() == null ? "Dịch vụ trong giỏ hàng" : order.getBooking().getServiceName();
            BigDecimal settlementBasis = commissionPolicy.grossSettledAmount(order);
            BigDecimal fee = commissionPolicy.feeFor(order);
            BigDecimal net = commissionPolicy.providerNetFor(order);
            csv.append(order.getId()).append(',')
                    .append(csv(order.getCustomer() == null ? "" : order.getCustomer().getFullName())).append(',')
                    .append(csv(order.getProvider() == null ? "" : order.getProvider().getFullName())).append(',')
                    .append(csv(serviceName)).append(',')
                    .append(csv(amount(order.getTotal()))).append(',')
                    .append(csv(amount(order.getLockedAmount()))).append(',')
                    .append(csv(order.getSettledAt() == null ? "" : amount(settlementBasis))).append(',')
                    .append(csv(order.getSettledAt() == null ? "" : amount(fee))).append(',')
                    .append(csv(order.getSettledAt() == null ? "" : amount(net))).append(',')
                    .append(csv(order.getStatus() == null ? "" : order.getStatus().name())).append(',')
                    .append(csv(order.getCreatedAt() == null ? "" : order.getCreatedAt().toString())).append(',')
                    .append(csv(order.getSettledAt() == null ? "" : order.getSettledAt().toString())).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillshare-orders-finance.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(utf8Bom(csv.toString()));
    }

    private String amount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    @GetMapping("/admin/reports/export")
    public ResponseEntity<byte[]> exportReport(Authentication authentication) {
        require(authentication, Role.ADMIN);
        StringBuilder csv = new StringBuilder("ID,Loai,DoiTuongBiBaoCao,NguoiBaoCao,ChiTiet,TrangThai,ThoiGian\n");
        for (ReportRow r : reportRows()) {
            csv.append(r.id()).append(',').append(csv(r.type())).append(',').append(csv(r.target())).append(',')
                    .append(csv(r.reporter())).append(',').append(csv(r.detail())).append(',').append(csv(r.status().name())).append(',')
                    .append(csv(r.date())).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillshare-reports.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(utf8Bom(csv.toString()));
    }

    @GetMapping("/admin/users/export")
    public ResponseEntity<byte[]> exportUsers(Authentication authentication) {
        require(authentication, Role.ADMIN);
        StringBuilder csv = new StringBuilder("ID,HoTen,Email,VaiTro,TrangThai\n");
        for (User u : users.findAll()) csv.append(u.getId()).append(',').append(csv(u.getFullName())).append(',')
                .append(csv(u.getEmail())).append(',').append(u.getRole()).append(',')
                .append(u.getAccountStatus()).append('\n');
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillshare-users.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(utf8Bom(csv.toString()));
    }

    private void prepareProviderBaseModel(Model model, ProviderProfile provider, String page) {
        model.addAttribute("provider", provider);
        model.addAttribute("page", page);
        model.addAttribute("workspaceTitle", providerPageTitle(page));
        model.addAttribute("providerMode", state.providerMode(provider.getId()));
        model.addAttribute("avgRating", provider.getRating() == null ? 0.0 : provider.getRating());
        model.addAttribute("reviewCount", provider.getReviewCount() == null ? 0 : provider.getReviewCount());
        model.addAttribute("commissionRatePercent", commissionPolicy.rate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());
        model.addAttribute("commissionRate", commissionPolicy.rate());

        // Default collections keep the template contract stable even when a page has no data.
        model.addAttribute("allSkills", List.of());
        model.addAttribute("certifications", List.of());
        model.addAttribute("packages", List.of());
        model.addAttribute("addons", List.of());
        model.addAttribute("schedule", List.of());
        model.addAttribute("requests", List.of());
        model.addAttribute("applications", List.of());
        model.addAttribute("providerOrders", List.of());
        model.addAttribute("providerWalletOrders", List.of());
        model.addAttribute("providerReviews", List.of());
        model.addAttribute("providerComplaints", List.of());
        model.addAttribute("providerConversations", List.of());
        model.addAttribute("selectedConversation", null);
        model.addAttribute("selectedMessages", List.of());
        model.addAttribute("feeInvoices", List.of());
        model.addAttribute("withdrawals", List.of());
        model.addAttribute("posts", List.of());
        model.addAttribute("requestQuery", "");
    }

    private ProviderProfile requireProviderProfile(User user) {
        return providers.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("Chưa có hồ sơ Provider cho tài khoản hiện tại"));
    }

    private String providerServiceStatusAction(Long serviceId, Authentication authentication,
                                               RedirectAttributes ra, Consumer<User> operation,
                                               String successMessage) {
        User provider = require(authentication, Role.PROVIDER);
        try {
            operation.accept(provider);
            ra.addFlashAttribute("success", successMessage);
        } catch (ServiceManagementException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Không thể cập nhật trạng thái dịch vụ serviceId={} providerId={}", serviceId, provider.getId(), e);
            ra.addFlashAttribute("error", "Không thể cập nhật trạng thái dịch vụ. Vui lòng thử lại.");
        }
        return "redirect:/provider/services";
    }

    private void fillProviderDashboard(Model model, User user, ProviderProfile provider) {
        List<RentalOrder> ownOrders = providerOrders(user);
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        BigDecimal completedIncome = ownOrders.stream()
                .filter(order -> order.getSettledAt() != null)
                .filter(order -> !order.getSettledAt().toLocalDate().isBefore(firstDayOfMonth))
                .map(commissionPolicy::providerNetFor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("monthIncome", completedIncome);
        model.addAttribute("activeCount", ownOrders.stream().map(RentalOrder::getLockedAmount)
                .filter(Objects::nonNull).filter(amount -> amount.signum() > 0).count());
        model.addAttribute("avgRating", provider.getRating() == null ? 0.0 : provider.getRating());
        model.addAttribute("reviewCount", provider.getReviewCount() == null ? 0 : provider.getReviewCount());
        model.addAttribute(
                "unansweredComplaints",
                providerComplaints(user).stream()
                        .filter(complaint -> complaint.getStatus() == ComplaintStatus.OPEN)
                        .count()
        );
    }

    private void fillProviderWallet(Model model, User user) {
        UserWallet wallet = escrowService.wallet(user);
        List<RentalOrder> ownOrders = providerOrders(user);
        BigDecimal lifetime = ownOrders.stream().map(commissionPolicy::providerNetFor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal held = ownOrders.stream().map(RentalOrder::getLockedAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("walletBalance", wallet.getBalance());
        model.addAttribute("escrow", held);
        model.addAttribute("lifetime", lifetime);
        model.addAttribute("commissionRatePercent", commissionPolicy.rate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());
        model.addAttribute("providerWalletOrders", ownOrders);
        model.addAttribute("providerWalletOrderRows", ownOrders.stream().map(o -> new ProviderWalletOrderRow(o,
                commissionPolicy.grossSettledAmount(o), commissionPolicy.feeFor(o), commissionPolicy.providerNetFor(o))).toList());
        model.addAttribute("withdrawals", providerWithdrawalRepository.findByProviderOrderByCreatedAtDesc(user));
    }

    private BigDecimal providerWalletBalance(User user) {
        return escrowService.wallet(user).getBalance();
    }

    private void fillProviderChat(Model model, User user) {
        List<Conversation> providerConversations = conversations.findAll().stream()
                .filter(Objects::nonNull)
                .filter(conversation -> conversation.getProvider() != null)
                .filter(conversation -> Objects.equals(conversation.getProvider().getId(), user.getId()))
                .sorted(Comparator.comparing(Conversation::getId))
                .toList();

        Conversation selected = providerConversations.isEmpty() ? null : providerConversations.get(0);
        List<Message> selectedMessages = selected == null
                ? List.of()
                : messages.findByConversationOrderBySentAtAsc(selected);

        model.addAttribute("providerConversations", providerConversations);
        model.addAttribute("selectedConversation", selected);
        model.addAttribute("selectedMessages", selectedMessages == null ? List.of() : selectedMessages);
    }

    @PostMapping("/provider/chat/{id}/send")
    public String providerChatSend(@PathVariable Long id, @RequestParam String content,
                                   Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        Conversation c = conversations.findById(id).orElseThrow();
        if (c.getProvider() == null || !Objects.equals(c.getProvider().getId(), u.getId())) {
            throw new IllegalArgumentException("Không có quyền");
        }
        if (content == null || content.isBlank()) {
            ra.addFlashAttribute("error", "Tin nhắn không được để trống.");
            return "redirect:/provider/chat";
        }
        messages.save(Message.builder()
                .conversation(c)
                .sender(u)
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build());
        return "redirect:/provider/chat";
    }

    private void fillAdminDashboard(Model model) {
        long userCount = users.count();
        long providerCount = providers.count();
        BigDecimal inflow = walletTransactions.findAll().stream().filter(t -> "DEMO_TOPUP".equals(t.getType()))
                .map(WalletTransaction::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal escrow = orders.findAll().stream().map(RentalOrder::getLockedAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingProviders = providers.findAll().stream().filter(p -> !Boolean.TRUE.equals(p.getApproved())).count();
        model.addAttribute("userCount", userCount);
        model.addAttribute("providerCount", providerCount);
        model.addAttribute("pendingProviders", pendingProviders);
        model.addAttribute("commission", orders.findAll().stream().filter(o -> o.getSettledAt() != null).map(commissionPolicy::feeFor).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("escrow", escrow);
        model.addAttribute("activeOrders", orders.findAll().stream().filter(o -> o.getLockedAmount() != null && o.getLockedAmount().signum() > 0).count());
    }

    private void fillAdminStatistics(Model model, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        final LocalDate rangeFrom = from;
        final LocalDate rangeTo = to;
        List<RentalOrder> allOrders = orders.findAll();
        List<RentalOrder> periodOrders = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null)
                .filter(o -> !o.getCreatedAt().toLocalDate().isBefore(rangeFrom)
                        && !o.getCreatedAt().toLocalDate().isAfter(rangeTo))
                .sorted(Comparator.comparing(RentalOrder::getCreatedAt))
                .toList();
        long completed = periodOrders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        double completion = periodOrders.isEmpty() ? 0 : completed * 100.0 / periodOrders.size();
        double avgRating = reviews.findAll().stream().mapToInt(Review::getRating).average().orElse(0.0);
        BigDecimal settledRevenue = allOrders.stream()
                .filter(o -> o.getSettledAt() != null)
                .filter(o -> !o.getSettledAt().toLocalDate().isBefore(rangeFrom)
                        && !o.getSettledAt().toLocalDate().isAfter(rangeTo))
                .map(this::settledValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long span = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        int bucketDays = span <= 31 ? 1 : span <= 180 ? 7 : Math.max(30, (int) Math.ceil(span / 60.0));
        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to) && starts.size() < 60; d = d.plusDays(bucketDays)) starts.add(d);
        List<TimePointRaw> raw = new ArrayList<>();
        long maxOrders = 0;
        BigDecimal maxRevenue = BigDecimal.ZERO;
        for (LocalDate startDay : starts) {
            LocalDate endDay = startDay.plusDays(bucketDays - 1L);
            if (endDay.isAfter(to)) endDay = to;
            final LocalDate bucketStart = startDay;
            final LocalDate bucketEnd = endDay;
            long count = periodOrders.stream().filter(o -> !o.getCreatedAt().toLocalDate().isBefore(bucketStart)
                    && !o.getCreatedAt().toLocalDate().isAfter(bucketEnd)).count();
            BigDecimal revenue = allOrders.stream()
                    .filter(o -> o.getSettledAt() != null)
                    .filter(o -> !o.getSettledAt().toLocalDate().isBefore(bucketStart)
                            && !o.getSettledAt().toLocalDate().isAfter(bucketEnd))
                    .map(this::settledValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String label = bucketDays == 1 ? startDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                    : startDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + " - " + endDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
            raw.add(new TimePointRaw(label, count, revenue));
            maxOrders = Math.max(maxOrders, count);
            if (revenue.compareTo(maxRevenue) > 0) maxRevenue = revenue;
        }
        final long maxCountScale = maxOrders;
        final BigDecimal maxRevenueScale = maxRevenue;
        List<TimePoint> timePoints = raw.stream().map(p -> new TimePoint(p.label(), p.orders(), p.revenue(),
                p.orders() == 0 ? 0 : Math.max(8, (int) Math.round(p.orders() * 100.0 / Math.max(1, maxCountScale))),
                p.revenue().signum() == 0 ? 0 : Math.max(8, p.revenue().multiply(new BigDecimal("100"))
                        .divide(maxRevenueScale, 2, java.math.RoundingMode.HALF_UP).intValue()))).toList();

        List<OrderStatus> statusList = List.of(OrderStatus.PENDING_PROVIDER_ACCEPTANCE,
                OrderStatus.PENDING_START_CONFIRMATION, OrderStatus.PROCESSING, OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,
                OrderStatus.PENDING_PAYMENT_OTP, OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER,
                OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER, OrderStatus.COMPLETED, OrderStatus.CANCELLED,
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        List<String> colors = List.of("#64748b", "#0ea5e9", "#2563eb", "#a855f7", "#d97706", "#f97316", "#e11d48", "#16a34a", "#dc2626", "#94a3b8", "#64748b");
        long total = periodOrders.size();
        List<StatusSlice> slices = new ArrayList<>();
        StringBuilder gradient = new StringBuilder();
        double cursor = 0;
        int colorIndex = 0;
        for (OrderStatus status : statusList) {
            long count = periodOrders.stream().filter(o -> o.getStatus() == status).count();
            if (count == 0) { colorIndex++; continue; }
            double percent = total == 0 ? 0 : count * 100.0 / total;
            double next = cursor + percent * 3.6;
            String color = colors.get(colorIndex++);
            slices.add(new StatusSlice(statusLabel(status), count, percent, color));
            if (gradient.length() > 0) gradient.append(", ");
            gradient.append(color).append(' ').append(String.format(Locale.ROOT, "%.2f", cursor)).append("deg ")
                    .append(String.format(Locale.ROOT, "%.2f", next)).append("deg");
            cursor = next;
        }
        if (gradient.length() == 0) gradient.append("#e2e8f0 0deg 360deg");

        model.addAttribute("completionRate", completion);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("retentionRate", 0.0);
        model.addAttribute("matchTime", 0.0);
        model.addAttribute("topProviders", providers.findAll().stream()
                .sorted(Comparator.comparing(ProviderProfile::getRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3).toList());
        model.addAttribute("fromDate", rangeFrom);
        model.addAttribute("toDate", rangeTo);
        model.addAttribute("periodOrders", periodOrders);
        model.addAttribute("totalOrdersInPeriod", total);
        model.addAttribute("completedOrdersInPeriod", completed);
        model.addAttribute("settledRevenueInPeriod", settledRevenue);
        model.addAttribute("timePoints", timePoints);
        model.addAttribute("statusSlices", slices);
        model.addAttribute("statusPieGradient", gradient.toString());
    }

    private BigDecimal settledValue(RentalOrder order) {
        if (order == null || order.getSettledAt() == null) return BigDecimal.ZERO;
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order.getCancellationCompensation() == null ? BigDecimal.ZERO : order.getCancellationCompensation();
        }
        return order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING_PROVIDER_ACCEPTANCE -> "Chờ Provider nhận";
            case PENDING_START_CONFIRMATION -> "Chờ xác nhận bắt đầu";
            case PROCESSING -> "Đang thực hiện";
            case PENDING_CUSTOMER_ACCEPTANCE -> "Chờ nghiệm thu";
            case PENDING_PAYMENT_OTP -> "Chờ OTP quyết toán";
            case CANCELLATION_REQUESTED_BY_CUSTOMER -> "Đề nghị hủy";
            case CANCELLATION_COMPENSATION_PENDING_CUSTOMER -> "Chờ duyệt phí hủy";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
            case PENDING_PAYMENT -> "Đơn cũ: chờ thanh toán";
            case PAID -> "Đơn cũ: đã thanh toán";
        };
    }

    private void fillAdminFinance(Model model, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) { LocalDate swap = from; from = to; to = swap; }
        final LocalDate rangeFrom = from;
        final LocalDate rangeTo = to;
        List<WalletTransaction> allTransactions = walletTransactions.findAll();
        List<WalletTransaction> periodTransactions = allTransactions.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> !t.getCreatedAt().toLocalDate().isBefore(rangeFrom) && !t.getCreatedAt().toLocalDate().isAfter(rangeTo))
                .toList();
        BigDecimal inflow = periodTransactions.stream().filter(t -> "DEMO_TOPUP".equals(t.getType()))
                .map(WalletTransaction::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = periodTransactions.stream().filter(t -> "SETTLEMENT_IN".equals(t.getType()))
                .map(WalletTransaction::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RentalOrder> allOrders = orders.findAll();
        List<RentalOrder> settledInPeriod = allOrders.stream().filter(o -> o.getSettledAt() != null)
                .filter(o -> !o.getSettledAt().toLocalDate().isBefore(rangeFrom) && !o.getSettledAt().toLocalDate().isAfter(rangeTo)).toList();
        BigDecimal commission = settledInPeriod.stream().map(commissionPolicy::feeFor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal escrow = allOrders.stream().map(RentalOrder::getLockedAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        long span = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
        int bucketDays = span <= 31 ? 1 : span <= 180 ? 7 : Math.max(30, (int) Math.ceil(span / 60.0));
        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to) && starts.size() < 60; d = d.plusDays(bucketDays)) starts.add(d);
        List<FinancePointRaw> rawFlow = new ArrayList<>();
        List<CommissionPointRaw> rawCommission = new ArrayList<>();
        BigDecimal maxInflow = BigDecimal.ZERO, maxOutflow = BigDecimal.ZERO, maxCommission = BigDecimal.ZERO;
        for (LocalDate startDay : starts) {
            LocalDate endDay = startDay.plusDays(bucketDays - 1L);
            if (endDay.isAfter(to)) endDay = to;
            final LocalDate bucketStart = startDay, bucketEnd = endDay;
            BigDecimal bucketIn = periodTransactions.stream().filter(t -> "DEMO_TOPUP".equals(t.getType()))
                    .filter(t -> inDateRange(t.getCreatedAt(), bucketStart, bucketEnd)).map(WalletTransaction::getAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bucketOut = periodTransactions.stream().filter(t -> "SETTLEMENT_IN".equals(t.getType()))
                    .filter(t -> inDateRange(t.getCreatedAt(), bucketStart, bucketEnd)).map(WalletTransaction::getAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bucketFee = settledInPeriod.stream().filter(o -> inDateRange(o.getSettledAt(), bucketStart, bucketEnd))
                    .map(commissionPolicy::feeFor).reduce(BigDecimal.ZERO, BigDecimal::add);
            String label = bucketDays == 1 ? startDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                    : startDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + " - " + endDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
            rawFlow.add(new FinancePointRaw(label, bucketIn, bucketOut));
            rawCommission.add(new CommissionPointRaw(label, bucketFee));
            if (bucketIn.compareTo(maxInflow) > 0) maxInflow = bucketIn;
            if (bucketOut.compareTo(maxOutflow) > 0) maxOutflow = bucketOut;
            if (bucketFee.compareTo(maxCommission) > 0) maxCommission = bucketFee;
        }
        final BigDecimal maxInScale = maxInflow, maxOutScale = maxOutflow, maxFeeScale = maxCommission;
        List<FinancePoint> financePoints = rawFlow.stream().map(p -> new FinancePoint(p.label(), p.inflow(), p.outflow(),
                barHeight(p.inflow(), maxInScale), barHeight(p.outflow(), maxOutScale))).toList();
        List<CommissionPoint> commissionPoints = rawCommission.stream().map(p -> new CommissionPoint(p.label(), p.commission(),
                barHeight(p.commission(), maxFeeScale))).toList();
        model.addAttribute("fromDate", rangeFrom);
        model.addAttribute("toDate", rangeTo);
        model.addAttribute("inflow", inflow);
        model.addAttribute("outflow", outflow);
        model.addAttribute("commission", commission);
        model.addAttribute("commissionRatePercent", commissionPolicy.rate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());
        model.addAttribute("escrow", escrow);
        model.addAttribute("financePoints", financePoints);
        model.addAttribute("commissionPoints", commissionPoints);
        model.addAttribute("withdrawals", adminWithdrawals());
    }

    private boolean inDateRange(LocalDateTime timestamp, LocalDate from, LocalDate to) {
        return timestamp != null && !timestamp.toLocalDate().isBefore(from) && !timestamp.toLocalDate().isAfter(to);
    }

    private int barHeight(BigDecimal value, BigDecimal max) {
        if (value == null || value.signum() <= 0 || max == null || max.signum() <= 0) return 0;
        return Math.max(8, value.multiply(new BigDecimal("100")).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue());
    }

    private List<AdminUserRow> adminUsers(List<User> source) {
        return source.stream().map(u -> {
            boolean locked = u.getAccountStatus() == AccountStatus.LOCKED;
            String status = switch (u.getAccountStatus()) {
                case ACTIVE -> "Hoạt động";
                case PENDING_VERIFICATION -> "Chờ xác minh email";
                case SUSPENDED -> "Tạm ngưng";
                case LOCKED -> "Đã khóa";
                case DELETED -> "Đã xóa";
            };
            return new AdminUserRow(u, locked, status);
        }).toList();
    }

    private List<PriceRequest> priceRequests() {
        return providers.findAll().stream().limit(2).map(p -> new PriceRequest(p.getId(), p.getHeadline(), p.getUser().getFullName(), p.getHourlyRate(), p.getHourlyRate().multiply(new BigDecimal("1.25")), "Bổ sung phạm vi bàn giao và dịch vụ hậu mãi")).toList();
    }

    private List<AdminReviewRow> adminReviewRows() {
        return reviews.findAll().stream().map(r -> new AdminReviewRow(r.getId(), r.getCustomer().getFullName(), r.getProvider().getFullName(), r.getRating(), r.getComment(), state.isReviewHidden(r.getId()), r.getCreatedAt() == null ? "-" : r.getCreatedAt().toLocalDate().toString())).toList();
    }

    private List<AdminComplaintRow> adminComplaintRows() {
        return complaints.findAll().stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> {
                    User reporter = c.getReporter() != null ? c.getReporter() : c.getCustomer();
                    User target = reportTarget(c);
                    return new AdminComplaintRow(c.getId(), c.getOrder() == null ? "-" : "DH" + c.getOrder().getId(),
                            reporter == null ? "Không xác định" : reporter.getFullName(),
                            target == null ? "Không xác định" : target.getFullName(), c.getSubject(), c.getStatus());
                }).toList();
    }

    private List<ReportRow> reportRows() {
        return complaints.findAll().stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> {
                    User reporter = c.getReporter() != null ? c.getReporter() : c.getCustomer();
                    User target = reportTarget(c);
                    return new ReportRow(c.getId(), target == null ? "Không xác định" : target.getFullName(),
                            c.getComplaintType() == null ? "OTHER" : c.getComplaintType().name(),
                            c.getSubject() + ": " + c.getDescription(), reporter == null ? "Không xác định" : reporter.getFullName(),
                            c.getCreatedAt() == null ? "-" : c.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                            c.getStatus());
                }).toList();
    }

    private User reportTarget(Complaint complaint) {
        if (complaint.getReportedUser() != null) return complaint.getReportedUser();
        RentalOrder order = complaint.getOrder();
        if (order == null) return null;
        User reporter = complaint.getReporter() != null ? complaint.getReporter() : complaint.getCustomer();
        if (reporter != null && order.getCustomer() != null && Objects.equals(reporter.getId(), order.getCustomer().getId())) return order.getProvider();
        return order.getCustomer();
    }

    private List<WithdrawalRow> adminWithdrawals() {
        return providerWithdrawalRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(w -> new WithdrawalRow(String.valueOf(w.getId()), w.getProvider().getFullName(), w.getBankName(),
                        w.getAccountNumber(), w.getAmount(), w.getCreatedAt() == null ? "-" : w.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), w.getStatus()))
                .toList();
    }


    private List<FeeInvoice> feeInvoices() {
        return List.of(
                new FeeInvoice("INV-2026-08", "Gói VIP Partner (Tháng 8)", new BigDecimal("299000"), "VNPay QR", "15/08/2026"),
                new FeeInvoice("INV-2026-07", "Gói VIP Partner (Tháng 7)", new BigDecimal("299000"), "Ví MoMo", "15/07/2026")
        );
    }

    private List<Certification> providerCertifications() {
        return List.of(new Certification("Google UX Design Professional Certificate", "Coursera & Google Career Certificates", "10/2023", "GOOG-UX-88421"), new Certification("Meta Front-End Developer Professional Certificate", "Meta & Coursera Authorized", "04/2024", "META-FE-99302"));
    }

    private List<ScheduleRow> scheduleRows() {
        return List.of(
                new ScheduleRow("Ca Sáng", "08:00 - 12:00", new boolean[]{true, true, true, true, true, false, false}),
                new ScheduleRow("Ca Chiều", "13:30 - 17:30", new boolean[]{true, true, true, true, true, false, false}),
                new ScheduleRow("Ca Tối", "19:00 - 22:00", new boolean[]{true, false, true, false, false, false, false})
        );
    }

    private List<Apply> providerApplications(User user) {
        return applies.findAll().stream()
                .filter(Objects::nonNull)
                .filter(application -> application.getProvider() != null)
                .filter(application -> Objects.equals(application.getProvider().getId(), user.getId()))
                .sorted(Comparator.comparing(Apply::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<RentalOrder> providerOrders(User user) {
        return orders.findAll().stream()
                .filter(Objects::nonNull)
                .filter(order -> order.getProvider() != null)
                .filter(order -> Objects.equals(order.getProvider().getId(), user.getId()))
                .sorted(Comparator.comparing(RentalOrder::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<Complaint> providerComplaints(User user) {
        return complaints.findAll().stream()
                .filter(Objects::nonNull)
                .filter(complaint -> {
                    if (complaint.getReportedUser() != null) return Objects.equals(complaint.getReportedUser().getId(), user.getId());
                    return complaint.getOrder() != null && complaint.getOrder().getProvider() != null
                            && Objects.equals(complaint.getOrder().getProvider().getId(), user.getId());
                })
                .sorted(Comparator.comparing(Complaint::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<ServiceRequest> openRequests(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return requests.findAll().stream()
                .filter(Objects::nonNull)
                .filter(request -> request.getStatus() == RequestStatus.OPEN)
                .filter(request -> {
                    if (needle.isBlank()) {
                        return true;
                    }
                    String title = request.getTitle() == null ? "" : request.getTitle().toLowerCase(Locale.ROOT);
                    String requiredSkills = request.getRequiredSkills() == null
                            ? ""
                            : request.getRequiredSkills().toLowerCase(Locale.ROOT);
                    return title.contains(needle) || requiredSkills.contains(needle);
                })
                .sorted(Comparator.comparing(ServiceRequest::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private RentalOrder ownedProviderOrder(Long id, User user) {
        RentalOrder order = orders.findById(id).orElseThrow();

        if (order.getProvider() == null || !Objects.equals(order.getProvider().getId(), user.getId())) {
            throw new IllegalArgumentException("Không có quyền xử lý đơn này");
        }

        return order;
    }

    private BigDecimal paidSum() {
        return payments.findAll().stream()
                .filter(Objects::nonNull)
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String categoryFor(ProviderProfile p) {
        String headline = p.getHeadline() == null ? "" : p.getHeadline().toLowerCase();
        if (headline.contains("design") || headline.contains("ui/ux")) return "Design";
        if (headline.contains("marketing")) return "Marketing";
        return "IT";
    }

    private User require(Authentication a, Role role) {
        User u = current.require(a);
        if (u.getRole() != role) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Không có quyền truy cập");
        return u;
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return java.text.NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(safe) + "đ";
    }

    private String csv(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private byte[] utf8Bom(String text) { return ("\uFEFF" + text).getBytes(StandardCharsets.UTF_8); }

    private List<PackageRow> adminPackages() {
        return List.of(
                new PackageRow("BASIC", "Gói Cơ Bản (Mặc định)", BigDecimal.ZERO, "30 ngày", "Mặc định", List.of("3 dịch vụ", "Nhận đơn & Escrow", "Hỗ trợ tiêu chuẩn")),
                new PackageRow("PRO", "Gói Chuyên Nghiệp (Pro Provider)", new BigDecimal("199000"), "30 ngày", "Phổ biến nhất", List.of("15 dịch vụ", "3 lượt đẩy Top", "Huy hiệu PRO", "Giảm 2% phí")),
                new PackageRow("ELITE", "Gói Doanh Nghiệp (VIP Elite)", new BigDecimal("499000"), "30 ngày", "VIP", List.of("Không giới hạn bài đăng", "10 lượt đẩy Hot", "Huy hiệu VIP ELITE", "Giảm 4% phí"))
        );
    }

    private List<PackageOrder> packageOrders() {
        return List.of(new PackageOrder("PKG-ORD-01", "Nguyễn Văn Minh", "Gói Chuyên Nghiệp", new BigDecimal("199000"), "01/04/2025", "01/05/2025"), new PackageOrder("PKG-ORD-02", "Trần Ngọc Mai", "Gói Doanh Nghiệp", new BigDecimal("499000"), "15/04/2025", "15/05/2025"), new PackageOrder("PKG-ORD-03", "Phạm Gia Huy", "Gói Chuyên Nghiệp", new BigDecimal("199000"), "10/04/2025", "10/05/2025"));
    }

    private String providerPageTitle(String page) {
        return switch (page) {
            case "dashboard" -> "Dashboard";
            case "profile" -> "Hồ Sơ & Dịch Vụ";
            case "skills" -> "Kỹ Năng & Chứng Chỉ";
            case "services", "packages" -> "Dịch Vụ & Bảng Giá";
            case "availability" -> "Đặt Lịch & Trạng Thái";
            case "offers", "applications", "request-detail", "apply" -> "Đề Xuất & Ứng Tuyển";
            case "bookings" -> "Đặt Lịch & Tiến Độ";
            case "wallet" -> "Ví & Thu Nhập";
            case "reviews" -> "Đánh Giá & Uy Tín";
            case "complaints" -> "Khiếu Nại & Tranh Chấp";
            case "chat" -> "Tin Nhắn Khách Hàng";
            case "fees" -> "Thanh Toán & Phí Sàn";
            case "posts" -> "Bài Viết & Portfolio";
            default -> "Provider Workspace";
        };
    }

    private String adminPageTitle(String page) {
        return switch (page) {
            case "dashboard" -> "Dashboard Tổng Quan";
            case "users" -> "Quản Lý Người Dùng";
            case "locked" -> "Khóa Tài Khoản";
            case "unlock" -> "Mở Khóa Tài Khoản";
            case "providers", "provider-detail" -> "Quản Lý Provider";
            case "pending-providers" -> "Duyệt Provider & Hồ Sơ";
            case "skills" -> "Duyệt Kỹ Năng & Chứng Chỉ";
            case "services" -> "Quản Lý Dịch Vụ";
            case "pending-services" -> "Duyệt Dịch Vụ Mới";
            case "price-requests" -> "Duyệt Điều Chỉnh Giá";
            case "blocked-services" -> "Dịch Vụ Vi Phạm";
            case "orders" -> "Đơn Hàng & Escrow";
            case "reviews" -> "Đánh Giá & Kiểm Duyệt";
            case "complaints" -> "Khiếu Nại & Tranh Chấp";
            case "reports" -> "Báo Cáo & Xuất Dữ Liệu";
            case "statistics" -> "Thống Kê Hệ Thống";
            case "finance" -> "Dòng Tiền Escrow";
            case "packages" -> "Gói Đăng Tin";
            default -> "Admin Portal (B4)";
        };
    }

    public record AdminUserRow(User user, boolean blocked, String status) {}
    public record PriceRequest(Long id, String service, String provider, BigDecimal currentPrice, BigDecimal proposedPrice, String reason) {}
    public record AdminReviewRow(Long id, String customer, String provider, int rating, String comment, boolean hidden, String date) {}
    public record AdminComplaintRow(Long id, String orderCode, String customer, String provider, String reason, ComplaintStatus status) {}
    public record AdminOrderFinanceRow(RentalOrder order, BigDecimal settlementBasis, BigDecimal fee, BigDecimal net,
                                       List<RentalOrderStatusHistory> history) {}
    public record ReportRow(Long id, String target, String type, String detail, String reporter, String date, ComplaintStatus status) {}
    public record WithdrawalRow(String id, String provider, String bank, String account, BigDecimal amount, String date, String status) {}
    public record FeeInvoice(String code, String name, BigDecimal amount, String method, String date) {}
    public record PackageRow(String code, String name, BigDecimal price, String duration, String label, List<String> benefits) {}
    public record Certification(String name, String issuer, String issuedDate, String certificateId) {}
    public record ScheduleRow(String shift, String time, boolean[] days) {}
    public record PackageOrder(String code, String provider, String packageName, BigDecimal amount, String activated, String expired) {}
    private record TimePointRaw(String label, long orders, BigDecimal revenue) {}
    public record TimePoint(String label, long orders, BigDecimal revenue, int orderHeight, int revenueHeight) {}
    public record StatusSlice(String label, long count, double percent, String color) {}
    private record FinancePointRaw(String label, BigDecimal inflow, BigDecimal outflow) {}
    private record CommissionPointRaw(String label, BigDecimal commission) {}
    public record FinancePoint(String label, BigDecimal inflow, BigDecimal outflow, int inflowHeight, int outflowHeight) {}
    public record ProviderWalletOrderRow(RentalOrder order, BigDecimal grossSettlement, BigDecimal fee, BigDecimal net) {}
    public record CommissionPoint(String label, BigDecimal commission, int height) {}
}
