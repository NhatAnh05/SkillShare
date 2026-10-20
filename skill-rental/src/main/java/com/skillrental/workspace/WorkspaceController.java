package com.skillrental.workspace;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.exception.InvalidOrderTransitionException;
import com.skillrental.exception.OrderAccessDeniedException;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.OrderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@Controller
@RequiredArgsConstructor
public class WorkspaceController {
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("2000000");
    private static final Set<String> PROVIDER_MODES = Set.of("AVAILABLE", "LIMITED", "OFF");
    private static final Set<String> PACKAGE_CODES = Set.of("BASIC", "PRO", "ELITE");

    private final CurrentUserService current;
    private final UserRepository users;
    private final ProviderProfileRepository providers;
    private final SkillRepository skills;
    private final ServiceRequestRepository requests;
    private final ApplyRepository applies;
    private final BookingRepository bookings;
    private final RentalOrderRepository orders;
    private final PaymentRepository payments;
    private final ReviewRepository reviews;
    private final ComplaintRepository complaints;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final WorkspaceState state;
    private final OrderStatusService orderStatus;

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
                model.addAttribute("packages", providerPackages());
                model.addAttribute("addons", providerAddons());
                model.addAttribute("enabledPackageCodes", state.enabledPackageCodes());
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
    public String newPackage(@RequestParam String name, @RequestParam BigDecimal price,
                             Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        if (name == null || name.isBlank() || price == null || price.signum() < 0) {
            ra.addFlashAttribute("error", "Tên gói và giá dịch vụ không hợp lệ.");
            return "redirect:/provider/services";
        }
        ra.addFlashAttribute("success", "Đã tạo gói dịch vụ demo: " + name.trim() + " - " + money(price) + ".");
        return "redirect:/provider/services";
    }

    @PostMapping("/provider/packages/{code}/toggle")
    public String togglePackage(@PathVariable String code, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!PACKAGE_CODES.contains(normalized)) {
            ra.addFlashAttribute("error", "Mã gói dịch vụ không hợp lệ.");
            return "redirect:/provider/services";
        }
        state.togglePackage(normalized);
        ra.addFlashAttribute("success", "Đã cập nhật trạng thái gói dịch vụ.");
        return "redirect:/provider/services";
    }

    @PostMapping("/provider/addons")
    public String addon(@RequestParam String name, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.PROVIDER);
        ra.addFlashAttribute("success", "Đã thêm dịch vụ gia tăng: " + name + ".");
        return "redirect:/provider/services";
    }

    @PostMapping("/provider/orders/{id}/processing")
    public String providerProcessing(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        RentalOrder o = ownedProviderOrder(id, u);
        try {
            orderStatus.transition(o, OrderStatus.PROCESSING, u, "Provider nhận xử lý");
            ra.addFlashAttribute("success", "Đã nhận xử lý đơn hàng.");
        } catch (InvalidOrderTransitionException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/provider/bookings";
    }

    @PostMapping("/provider/orders/{id}/complete")
    public String providerComplete(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        RentalOrder o = ownedProviderOrder(id, u);
        try {
            orderStatus.transition(o, OrderStatus.COMPLETED, u, "Provider đánh dấu hoàn tất");
            ra.addFlashAttribute("success", "Đã đánh dấu hoàn tất đơn hàng.");
        } catch (InvalidOrderTransitionException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
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
    public String withdraw(@RequestParam BigDecimal amount, Authentication authentication, RedirectAttributes ra) {
        User u = require(authentication, Role.PROVIDER);
        BigDecimal available = providerWalletBalance(u);
        if (amount == null || amount.signum() <= 0) {
            ra.addFlashAttribute("error", "Số tiền rút không hợp lệ.");
        } else if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            ra.addFlashAttribute("error", "Số tiền rút tối thiểu trong demo là 2.000.000đ.");
        } else if (amount.compareTo(available) > 0) {
            ra.addFlashAttribute("error", "Số tiền rút vượt số dư khả dụng " + money(available) + ".");
        } else {
            ra.addFlashAttribute("success", "Đã ghi nhận yêu cầu rút " + money(amount) + ". Hệ thống demo sẽ mô phỏng xử lý trong 5-15 phút.");
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
        model.addAttribute("providerApproved", state.isProviderApproved(id));
        model.addAttribute("state", state);
        return "workspace/admin/page";
    }

    @GetMapping("/admin")
    public String adminHome() { return "redirect:/admin/dashboard"; }

    @GetMapping("/admin/{page}")
    public String adminPage(@PathVariable String page, @RequestParam(required = false) String q, @RequestParam(required = false) String roleFilter, Authentication authentication, Model model) {
        User admin = require(authentication, Role.ADMIN);
        List<User> allUsers = users.findAll();
        state.primeDemo(allUsers);
        model.addAttribute("page", page);
        model.addAttribute("workspaceTitle", adminPageTitle(page));
        model.addAttribute("admin", admin);
        model.addAttribute("users", allUsers);
        model.addAttribute("providers", providers.findAll());
        model.addAttribute("skills", skills.findAll());
        model.addAttribute("orders", orders.findAll().stream().sorted(Comparator.comparing(RentalOrder::getId).reversed()).toList());
        model.addAttribute("reviews", reviews.findAll().stream().sorted(Comparator.comparing(Review::getId).reversed()).toList());
        model.addAttribute("complaints", complaints.findAll().stream().sorted(Comparator.comparing(Complaint::getId).reversed()).toList());
        model.addAttribute("state", state);
        model.addAttribute("adminQuery", q);
        model.addAttribute("roleFilter", roleFilter);

        switch (page) {
            case "dashboard" -> fillAdminDashboard(model);
            case "users" -> model.addAttribute("userRows", adminUsers(allUsers.stream().filter(u -> (q == null || q.isBlank()) || u.getFullName().toLowerCase().contains(q.toLowerCase()) || u.getEmail().toLowerCase().contains(q.toLowerCase()) || (u.getPhone() != null && u.getPhone().contains(q))).filter(u -> roleFilter == null || roleFilter.isBlank() || u.getRole().name().equalsIgnoreCase(roleFilter)).toList()));
            case "locked" -> model.addAttribute("lockedRows", allUsers.stream().filter(u -> state.isBlocked(u.getId())).toList());
            case "unlock" -> model.addAttribute("unlockRows", allUsers.stream().filter(u -> state.isBlocked(u.getId())).toList());
            case "providers" -> model.addAttribute("providerRows", providers.findAll());
            case "pending-providers" -> model.addAttribute("pendingProviders", providers.findAll().stream().filter(p -> !state.isProviderApproved(p.getId())).toList());
            case "skills" -> model.addAttribute("pendingSkills", skills.findAll().stream().filter(s -> !state.isSkillApproved(s.getId())).limit(5).toList());
            case "services" -> model.addAttribute("serviceRows", serviceRows());
            case "pending-services" -> model.addAttribute("pendingServices", serviceRows().stream().filter(s -> !state.isServiceApproved(s.id())).toList());
            case "price-requests" -> model.addAttribute("priceRequests", priceRequests());
            case "blocked-services" -> model.addAttribute("blockedServices", serviceRows().stream().filter(s -> state.isServiceBlocked(s.id())).toList());
            case "reviews" -> model.addAttribute("adminReviews", adminReviewRows());
            case "complaints" -> model.addAttribute("complaintRows", adminComplaintRows());
            case "reports" -> model.addAttribute("reportRows", reportRows());
            case "statistics" -> fillAdminStatistics(model);
            case "finance" -> fillAdminFinance(model);
            case "packages" -> {
                model.addAttribute("packages", adminPackages());
                model.addAttribute("packageOrders", packageOrders());
            }
        }
        return "workspace/admin/page";
    }

    @PostMapping("/admin/orders/{id}/intervene")
    public String interveneOrder(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        RentalOrder o = orders.findById(id).orElseThrow();
        ra.addFlashAttribute("success", "Đã mở hồ sơ can thiệp cho đơn DH" + o.getId() + " trong demo Escrow.");
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
        BigDecimal active = orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PROCESSING)
                .map(RentalOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        ra.addFlashAttribute("success", "Đã đối soát demo Escrow. Tổng tiền đang bảo lãnh: " + money(active) + ".");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/users/{id}/lock")
    public String lockUser(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User admin = require(authentication, Role.ADMIN);
        if (Objects.equals(admin.getId(), id)) ra.addFlashAttribute("error", "Không thể tự khóa tài khoản Admin hiện tại.");
        else { state.blockUser(id); ra.addFlashAttribute("success", "Đã khóa tài khoản trong workspace demo."); }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.unblockUser(id);
        ra.addFlashAttribute("success", "Đã mở khóa tài khoản trong workspace demo.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/providers/{id}/approve")
    public String approveProvider(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.approveProvider(id);
        ra.addFlashAttribute("success", "Đã duyệt Provider và cấp quyền hoạt động trong demo.");
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
        state.approveService(id);
        state.restoreService(id);
        ra.addFlashAttribute("success", "Đã duyệt bài đăng dịch vụ trong demo.");
        return "redirect:/admin/pending-services";
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
        state.blockService(id);
        ra.addFlashAttribute("success", "Đã khóa dịch vụ vi phạm trong demo.");
        return "redirect:/admin/services";
    }

    @PostMapping("/admin/services/{id}/restore")
    public String restoreService(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.restoreService(id);
        ra.addFlashAttribute("success", "Đã khôi phục dịch vụ.");
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
    public String approveWithdrawal(@PathVariable String id, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.approveWithdrawal(id);
        ra.addFlashAttribute("success", "Đã duyệt lệnh chuyển khoản demo " + id + ".");
        return "redirect:/admin/finance";
    }

    @PostMapping("/admin/packages/{code}/save")
    public String savePackage(@PathVariable String code, Authentication authentication, RedirectAttributes ra) {
        require(authentication, Role.ADMIN);
        state.togglePackage("ADMIN-" + code);
        ra.addFlashAttribute("success", "Đã lưu cấu hình gói " + code + " trong demo.");
        return "redirect:/admin/packages";
    }

    @GetMapping("/admin/reports/export")
    public ResponseEntity<byte[]> exportReport(Authentication authentication) {
        require(authentication, Role.ADMIN);
        StringBuilder csv = new StringBuilder("ID,HoTen,Email,VaiTro,TrangThai\n");
        for (User u : users.findAll()) {
            csv.append(u.getId()).append(',').append(csv(u.getFullName())).append(',').append(csv(u.getEmail())).append(',')
                    .append(u.getRole()).append(',').append(state.isBlocked(u.getId()) ? "BLOCKED" : "ACTIVE").append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillrental-admin-report.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(utf8Bom(csv.toString()));
    }

    private void prepareProviderBaseModel(Model model, ProviderProfile provider, String page) {
        model.addAttribute("provider", provider);
        model.addAttribute("page", page);
        model.addAttribute("workspaceTitle", providerPageTitle(page));
        model.addAttribute("providerMode", state.providerMode(provider.getId()));
        model.addAttribute("enabledPackageCodes", state.enabledPackageCodes());
        model.addAttribute("avgRating", provider.getRating() == null ? 0.0 : provider.getRating());
        model.addAttribute("reviewCount", provider.getReviewCount() == null ? 0 : provider.getReviewCount());

        // Default collections keep the template contract stable even when a page has no data.
        model.addAttribute("allSkills", List.of());
        model.addAttribute("certifications", List.of());
        model.addAttribute("packages", List.of());
        model.addAttribute("addons", List.of());
        model.addAttribute("schedule", List.of());
        model.addAttribute("requests", List.of());
        model.addAttribute("applications", List.of());
        model.addAttribute("providerOrders", List.of());
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

    private void fillProviderDashboard(Model model, User user, ProviderProfile provider) {
        List<RentalOrder> ownOrders = providerOrders(user);
        BigDecimal completedIncome = ownOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(RentalOrder::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("monthIncome", completedIncome.multiply(new BigDecimal("0.95")));
        model.addAttribute(
                "activeCount",
                ownOrders.stream()
                        .filter(order -> order.getStatus() == OrderStatus.PAID
                                || order.getStatus() == OrderStatus.PROCESSING)
                        .count()
        );
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
        List<RentalOrder> providerOrders = providerOrders(user);

        BigDecimal lifetime = providerOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(RentalOrder::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal escrow = providerOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAID
                        || order.getStatus() == OrderStatus.PROCESSING)
                .map(RentalOrder::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("walletBalance", lifetime.multiply(new BigDecimal("0.90")));
        model.addAttribute("escrow", escrow);
        model.addAttribute("lifetime", lifetime);
        model.addAttribute("withdrawals", providerWithdrawals());
    }

    private BigDecimal providerWalletBalance(User user) {
        return providerOrders(user).stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(RentalOrder::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(new BigDecimal("0.90"));
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
        BigDecimal inflow = paidSum();
        BigDecimal escrow = orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PROCESSING).map(RentalOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingProviders = providers.findAll().stream().filter(p -> !state.isProviderApproved(p.getId())).count();
        model.addAttribute("userCount", userCount);
        model.addAttribute("providerCount", providerCount);
        model.addAttribute("pendingProviders", pendingProviders);
        model.addAttribute("commission", inflow.multiply(new BigDecimal("0.10")));
        model.addAttribute("escrow", escrow);
        model.addAttribute("activeOrders", orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PROCESSING).count());
    }

    private void fillAdminStatistics(Model model) {
        List<RentalOrder> all = orders.findAll();
        long completed = all.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        double completion = all.isEmpty() ? 0 : (completed * 100.0 / all.size());
        double avgRating = reviews.findAll().stream().mapToInt(Review::getRating).average().orElse(5.0);
        model.addAttribute("completionRate", completion);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("retentionRate", 68.5);
        model.addAttribute("matchTime", 2.4);
        model.addAttribute("topProviders", providers.findAll().stream().sorted(Comparator.comparing(ProviderProfile::getRating).reversed()).limit(3).toList());
    }

    private void fillAdminFinance(Model model) {
        BigDecimal inflow = paidSum();
        BigDecimal outflow = orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).map(RentalOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = inflow.multiply(new BigDecimal("0.10"));
        BigDecimal escrow = orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PROCESSING).map(RentalOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("inflow", inflow);
        model.addAttribute("outflow", outflow);
        model.addAttribute("commission", commission);
        model.addAttribute("escrow", escrow);
        model.addAttribute("withdrawals", adminWithdrawals());
    }

    private List<AdminUserRow> adminUsers(List<User> source) {
        return source.stream().map(u -> new AdminUserRow(u, state.isBlocked(u.getId()), state.isBlocked(u.getId()) ? "Đã khóa" : "Hoạt động")).toList();
    }

    private List<ServiceRow> serviceRows() {
        List<ProviderProfile> ps = providers.findAll();
        List<String> names = List.of("Thiết kế website chuẩn SEO & Tối ưu tốc độ", "Thiết kế Logo & Bộ nhận diện thương hiệu", "Luyện thi IELTS 1 kèm 1 cấp tốc", "Tư vấn Spring Boot & REST API", "Digital Marketing & SEO");
        return ps.stream().map(p -> new ServiceRow(p.getId(), names.get((p.getId().intValue() - 1) % names.size()), categoryFor(p), p.getUser().getFullName(), p.getHourlyRate(), p.getRating(), state.isServiceBlocked(p.getId()))).toList();
    }

    private List<PriceRequest> priceRequests() {
        return providers.findAll().stream().limit(2).map(p -> new PriceRequest(p.getId(), p.getHeadline(), p.getUser().getFullName(), p.getHourlyRate(), p.getHourlyRate().multiply(new BigDecimal("1.25")), "Bổ sung phạm vi bàn giao và dịch vụ hậu mãi")).toList();
    }

    private List<AdminReviewRow> adminReviewRows() {
        return reviews.findAll().stream().map(r -> new AdminReviewRow(r.getId(), r.getCustomer().getFullName(), r.getProvider().getFullName(), r.getRating(), r.getComment(), state.isReviewHidden(r.getId()), r.getCreatedAt() == null ? "-" : r.getCreatedAt().toLocalDate().toString())).toList();
    }

    private List<AdminComplaintRow> adminComplaintRows() {
        return complaints.findAll().stream().map(c -> new AdminComplaintRow(c.getId(), c.getOrder() == null ? "-" : "DH" + c.getOrder().getId(), c.getCustomer().getFullName(), c.getOrder() == null ? "-" : c.getOrder().getProvider().getFullName(), c.getSubject(), c.getStatus())).toList();
    }

    private List<ReportRow> reportRows() {
        return List.of(
                new ReportRow("REP-01", "Tài khoản: " + blockedUserName(), "Lừa đảo & phát tán virus", "Provider nhận cọc ngoài sàn và gửi link đáng ngờ", "Đã kiểm tra"),
                new ReportRow("REP-02", "Dịch vụ: Cài đặt tool hack game", "Nội dung vi phạm pháp luật", "Bài đăng quảng cáo phần mềm can thiệp trái phép", "Đã kiểm tra"),
                new ReportRow("REP-03", "Tạo đơn ảo", "Spam & Boom đơn hàng", "Hủy đơn liên tục gây ảnh hưởng Provider", "Đã kiểm tra")
        );
    }

    private String blockedUserName() {
        return users.findAll().stream().filter(u -> state.isBlocked(u.getId())).map(User::getFullName).findFirst().orElse("Người dùng vi phạm");
    }

    private List<WithdrawalRow> adminWithdrawals() {
        return List.of(
                new WithdrawalRow("WD-0912", "Nguyễn Văn Minh", "Vietcombank", "0071001234567", new BigDecimal("25000000"), "28/04/2025 09:15"),
                new WithdrawalRow("WD-0911", "Trần Ngọc Mai", "Techcombank", "19034567890123", new BigDecimal("18500000"), "27/04/2025 15:30"),
                new WithdrawalRow("WD-0910", "Phạm Gia Huy", "MB Bank", "08889999222", new BigDecimal("12000000"), "26/04/2025 11:20")
        );
    }

    private List<ProviderWithdrawal> providerWithdrawals() {
        return List.of(
                new ProviderWithdrawal("WD-88412", "12/09/2026 14:20", new BigDecimal("10000000"), "MB Bank (0987***321)", "Đã hoàn tất"),
                new ProviderWithdrawal("WD-87901", "01/09/2026 09:15", new BigDecimal("15000000"), "MB Bank (0987***321)", "Đã hoàn tất")
        );
    }

    private List<FeeInvoice> feeInvoices() {
        return List.of(
                new FeeInvoice("INV-2026-08", "Gói VIP Partner (Tháng 8)", new BigDecimal("299000"), "VNPay QR", "15/08/2026"),
                new FeeInvoice("INV-2026-07", "Gói VIP Partner (Tháng 7)", new BigDecimal("299000"), "Ví MoMo", "15/07/2026")
        );
    }

    private List<PackageRow> providerPackages() {
        return List.of(
                new PackageRow("BASIC", "Landing Page Giới Thiệu", new BigDecimal("1500000"), "3 Ngày", "Tiết kiệm - Cơ bản", List.of("Thiết kế 1 Trang Landing Page", "Responsive Mobile", "2 Lần Chỉnh Sửa")),
                new PackageRow("PRO", "Website Doanh Nghiệp Chuẩn SEO", new BigDecimal("3500000"), "5 Ngày", "Nổi bật - Bán chạy nhất", List.of("5-7 Trang giao diện", "Form báo giá & Chat", "Chuẩn SEO On-page", "5 Lần chỉnh sửa")),
                new PackageRow("ELITE", "Web/App Nâng Cao & Backend", new BigDecimal("7500000"), "12-14 Ngày", "Cao cấp - Fullstack", List.of("Fullstack Frontend + Backend", "VNPay / MoMo", "Admin phân quyền", "Hỗ trợ 3 tháng"))
        );
    }

    private List<Addon> providerAddons() {
        return List.of(new Addon("Giao Hàng Hỏa Tốc Trong 24 Giờ", new BigDecimal("500000")), new Addon("Thêm 1 Trang Giao Diện Tùy Biến", new BigDecimal("300000")), new Addon("Triển Khai Server & DNS/SSL", new BigDecimal("400000")));
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
                .filter(complaint -> complaint.getOrder() != null)
                .filter(complaint -> complaint.getOrder().getProvider() != null)
                .filter(complaint -> Objects.equals(complaint.getOrder().getProvider().getId(), user.getId()))
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
            throw new OrderAccessDeniedException("Không có quyền xử lý đơn này");
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
    public record ServiceRow(Long id, String name, String category, String provider, BigDecimal price, Double rating, boolean blocked) {}
    public record PriceRequest(Long id, String service, String provider, BigDecimal currentPrice, BigDecimal proposedPrice, String reason) {}
    public record AdminReviewRow(Long id, String customer, String provider, int rating, String comment, boolean hidden, String date) {}
    public record AdminComplaintRow(Long id, String orderCode, String customer, String provider, String reason, ComplaintStatus status) {}
    public record ReportRow(String id, String target, String type, String detail, String status) {}
    public record WithdrawalRow(String id, String provider, String bank, String account, BigDecimal amount, String date) {}
    public record ProviderWithdrawal(String id, String date, BigDecimal amount, String bank, String status) {}
    public record FeeInvoice(String code, String name, BigDecimal amount, String method, String date) {}
    public record PackageRow(String code, String name, BigDecimal price, String duration, String label, List<String> benefits) {}
    public record Addon(String name, BigDecimal price) {}
    public record Certification(String name, String issuer, String issuedDate, String certificateId) {}
    public record ScheduleRow(String shift, String time, boolean[] days) {}
    public record PackageOrder(String code, String provider, String packageName, BigDecimal amount, String activated, String expired) {}
}
