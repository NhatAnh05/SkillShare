# Skill Rental — Mã nguồn đầy đủ của tất cả file đã sửa hoặc tạo mới

Đường dẫn được tính từ thư mục `skill-rental/` chứa `pom.xml`. Không có đoạn code bị lược bỏ.

---

## `src/main/java/com/skillrental/controller/BookingController.java`

```java
package com.skillrental.controller;

import com.skillrental.dto.BookingForm;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller @RequiredArgsConstructor public class BookingController {
    private final MarketplaceService marketplace;
    private final CurrentUserService current;
    private final BookingService bookings;
    @GetMapping("/booking/provider/{id}") String form(@PathVariable Long id,@RequestParam(defaultValue="false")boolean premium,Model m) {
        var f=new BookingForm();
        f.setPremium(premium);
        f.setDirectBooking(true);
        m.addAttribute("form",f);
        m.addAttribute("provider",marketplace.get(id));
        return "booking/booking";
    }
    @PostMapping("/booking/provider/{id}") String submit(@PathVariable Long id,@Valid @ModelAttribute("form")BookingForm f,BindingResult br,Authentication a,Model m) {
        if(br.hasErrors()) {
            m.addAttribute("provider",marketplace.get(id));
            return "booking/booking";
        }
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            br.rejectValue("timeSlot", "past", "Khung giờ đã qua");
            m.addAttribute("provider", marketplace.get(id));
            return "booking/booking";
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0),
                              java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0))
                .contains(f.getTimeSlot())) {
            br.rejectValue("timeSlot", "invalid", "Khung giờ không hợp lệ");
            m.addAttribute("provider", marketplace.get(id));
            return "booking/booking";
        }
        var o=bookings.create(current.require(a),id,f);
        return "redirect:/payment/"+o.getId();
    }
}
```

---

## `src/main/java/com/skillrental/controller/HomeController.java`

```java
package com.skillrental.controller;

import com.skillrental.service.MarketplaceService;
import com.skillrental.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller @RequiredArgsConstructor public class HomeController {
    private final MarketplaceService marketplace;
    private final SkillRepository skills;
    @GetMapping("/") String root() {
        return "redirect:/marketplace";
    }
    @GetMapping("/marketplace") String home(Model m) {
        m.addAttribute("providers",marketplace.search(null,null,null,"rating"));
        m.addAttribute("skills", skills.findAll());
        return "home";
    }
}
```

---

## `src/main/java/com/skillrental/controller/MarketplaceController.java`

```java
package com.skillrental.controller;

import com.skillrental.service.MarketplaceService;
import com.skillrental.repository.SkillRepository;
import com.skillrental.repository.ReviewRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@Controller @RequiredArgsConstructor
public class MarketplaceController {
    private final MarketplaceService marketplace;
    private final SkillRepository skills;
    private final ReviewRepository reviewRepository;
    @GetMapping("/providers")
    String search(@RequestParam(required=false)String q,@RequestParam(required=false)String skill,
    @RequestParam(required=false)BigDecimal minPrice,@RequestParam(required=false)BigDecimal maxPrice,
    @RequestParam(defaultValue="rating")String sort,Model model) {
        model.addAttribute("providers",marketplace.search(q,skill,minPrice,maxPrice,sort));
        model.addAttribute("skills",skills.findAll().stream().sorted((a,b)->a.getName().compareToIgnoreCase(b.getName())).toList());
        model.addAttribute("q",q);
        model.addAttribute("skill",skill);
        model.addAttribute("minPrice",minPrice==null?BigDecimal.ZERO:minPrice);
        model.addAttribute("maxPrice",maxPrice==null?new BigDecimal("2000000"):maxPrice);
        model.addAttribute("sort",sort);
        return "marketplace/search";
    }
    @GetMapping("/providers/{id}")String detail(@PathVariable Long id,Model model) {
        var provider = marketplace.get(id);
        var reviews = reviewRepository.findByProviderOrderByIdDesc(provider.getUser());
        int[] counts = new int[5];
        reviews.forEach(review -> counts[review.getRating() - 1]++);
        long total = reviews.size();
        int[] percents = IntStream.range(0, 5)
                .map(i -> total == 0 ? 0 : (int) Math.round(counts[i] * 100.0 / total)).toArray();
        model.addAttribute("provider", provider);
        model.addAttribute("reviews", reviews.stream().limit(4).toList());
        model.addAttribute("ratingCounts", counts);
        model.addAttribute("ratingPercent", percents);
        model.addAttribute("portfolioNames", List.of("Dự án thương mại điện tử", "Ứng dụng thanh toán", "Nền tảng quản lý", "Tối ưu hệ thống"));
        return "marketplace/provider-detail";
    }
}
```

---

## `src/main/java/com/skillrental/controller/OrderController.java`

```java
package com.skillrental.controller;

import com.skillrental.dto.PaymentForm;
import com.skillrental.entity.OrderStatus;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class OrderController {
    private final CurrentUserService current;
    private final OrderService orders;
    @GetMapping("/orders") String list(Authentication a,Model m) {
        var u=current.require(a);
        m.addAttribute("orders",orders.list(u));
        m.addAttribute("completedCount",orders.completed(u));
        m.addAttribute("processingCount",orders.processingCount(u));
        return "orders/orders";
    }
    @GetMapping("/payment/{id}") String payment(@PathVariable Long id,Authentication a,Model m) {
        m.addAttribute("order",orders.owned(id,current.require(a)));
        m.addAttribute("form",new PaymentForm());
        return "booking/payment";
    }
    @PostMapping("/payment/{id}") String pay(@PathVariable Long id,@Valid @ModelAttribute("form")PaymentForm f,BindingResult br,Authentication a,Model m,RedirectAttributes ra) {
        var o=orders.owned(id,current.require(a));
        if(br.hasErrors()) {
            m.addAttribute("order",o);
            return "booking/payment";
        }
        try {
            orders.pay(o,f.getMethod(),f.getCouponCode(),f.isAcceptedTerms());
            ra.addFlashAttribute("success","Thanh toán thành công.");
            return "redirect:/booking/complete/" + id;
        } catch(Exception e) {
            br.reject("pay",e.getMessage());
            m.addAttribute("order",o);
            return "booking/payment";
        }
    }
    @GetMapping("/booking/complete/{id}")
    String bookingComplete(@PathVariable Long id, Authentication authentication, Model model) {
        var order = orders.owned(id, current.require(authentication));
        model.addAttribute("order", order);
        return "booking/complete";
    }

    @GetMapping("/orders/table") String table(Authentication auth,Model model) {
        var u=current.require(auth);
        model.addAttribute("orders",orders.list(u));
        return "orders/orders :: orderTable";
    }
    @GetMapping("/orders/{id}/details") String details(@PathVariable Long id,Authentication auth,Model model) {
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        return "orders/progress :: details";
    }
    @PostMapping("/orders/{id}/state") String state(@PathVariable Long id,@RequestParam String action,Authentication auth,Model model) {
        var order=orders.owned(id,current.require(auth));
        if("complete".equals(action)&&order.getStatus()==OrderStatus.PROCESSING) {
            orders.complete(order);
        }
        else if("processing".equals(action)&&order.getStatus()==OrderStatus.PAID) {
            orders.processing(order);
        }
        else throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Trạng thái không cho phép");
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        return "orders/progress :: details";
    }
    @GetMapping("/orders/{id}/progress") String progress(@PathVariable Long id,Authentication a,Model m) {
        m.addAttribute("order",orders.owned(id,current.require(a)));
        return "orders/progress";
    }
    @PostMapping("/orders/{id}/processing") String processing(@PathVariable Long id,Authentication a) {
        orders.processing(orders.owned(id,current.require(a)));
        return "redirect:/orders/"+id+"/progress";
    }
    @PostMapping("/orders/{id}/complete") String complete(@PathVariable Long id,Authentication a,RedirectAttributes ra) {
        orders.complete(orders.owned(id,current.require(a)));
        ra.addFlashAttribute("success","Đã xác nhận hoàn thành.");
        return "redirect:/orders";
    }
}
```

---

## `src/main/java/com/skillrental/controller/RequestController.java`

```java
package com.skillrental.controller;

import com.skillrental.dto.ServiceRequestForm;
import com.skillrental.service.*;
import com.skillrental.entity.RequestStatus;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor @RequestMapping("/requests") public class RequestController {
    private final CurrentUserService current;
    private final RequestService service;
    @GetMapping String list(Authentication a,Model m) {
        var requests = service.list(current.require(a));
        m.addAttribute("requests", requests);
        m.addAttribute("requestStats", List.of(
            Map.of("label", "Tổng Request", "value", requests.size()),
            Map.of("label", "Request đang mở", "value", requests.stream().filter(r -> r.getStatus() == RequestStatus.OPEN).count()),
            Map.of("label", "Request đang thực hiện", "value", requests.stream().filter(r -> r.getStatus() == RequestStatus.IN_PROGRESS).count()),
            Map.of("label", "Request đã hoàn thành", "value", requests.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).count())
        ));
        m.addAttribute("applyMap", requests.stream().collect(Collectors.toMap(
            r -> r.getId(), service::applies
        )));
        return "requests/list";
    }
    @GetMapping("/create") String create(Model m) {
        m.addAttribute("form",new ServiceRequestForm());
        return "requests/create";
    }
    @PostMapping String create(@Valid @ModelAttribute("form")ServiceRequestForm f,BindingResult br,Authentication a,RedirectAttributes ra) {
        if(br.hasErrors())return "requests/create";
        service.create(current.require(a),f);
        ra.addFlashAttribute("success","Đăng request thành công.");
        return "redirect:/requests";
    }
    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Authentication auth, Model model) {
        var request = service.owned(id, current.require(auth));
        if (request.getStatus() != RequestStatus.OPEN) return "redirect:/requests";
        ServiceRequestForm form = new ServiceRequestForm();
        form.setTitle(request.getTitle());
        form.setRequiredSkills(request.getRequiredSkills());
        form.setDescription(request.getDescription());
        form.setBudget(request.getBudget());
        form.setDeadline(request.getDeadline());
        model.addAttribute("form", form);
        model.addAttribute("request", request);
        return "requests/edit";
    }

    @PostMapping("/{id}/edit")
    String editSubmit(@PathVariable Long id, @Valid @ModelAttribute("form") ServiceRequestForm form,
                      BindingResult result, Authentication auth, Model model, RedirectAttributes attrs) {
        var request = service.owned(id, current.require(auth));
        if (request.getStatus() != RequestStatus.OPEN) return "redirect:/requests";
        if (result.hasErrors()) {
            model.addAttribute("request", request);
            return "requests/edit";
        }
        service.updateOpenRequest(request, form);
        attrs.addFlashAttribute("success", "Đã cập nhật Request.");
        return "redirect:/requests";
    }

    @GetMapping("/{id}/applies") String applies(@PathVariable Long id,Authentication a,Model m) {
        var r=service.owned(id,current.require(a));
        m.addAttribute("request",r);
        m.addAttribute("applies",service.applies(r));
        return "requests/applies";
    }
    @PostMapping("/{id}/applies/{applyId}/choose") String choose(@PathVariable Long id,@PathVariable Long applyId,Authentication a,RedirectAttributes ra) {
        var r=service.owned(id,current.require(a));
        service.choose(r,applyId);
        ra.addFlashAttribute("success","Đã chọn Provider.");
        return "redirect:/requests/"+id+"/applies";
    }
}
```

---

## `src/main/java/com/skillrental/controller/SettingsController.java`

```java
package com.skillrental.controller;

import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SettingsController {
    private final CurrentUserService currentUser;
    private final UserRepository users;

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        User user = currentUser.require(authentication);
        int count = 0;
        if (filled(user.getFullName())) count++;
        if (filled(user.getEmail())) count++;
        if (filled(user.getPhone())) count++;
        if (filled(user.getAvatarUrl())) count++;
        if (filled(user.getBio())) count++;
        model.addAttribute("completion", count * 20);
        return "customer/settings";
    }

    @GetMapping("/settings/coming-soon")
    public String comingSoon(@RequestParam(defaultValue = "Tính năng") String feature, Model model) {
        model.addAttribute("feature", feature);
        return "customer/coming-soon";
    }

    // TODO: nghiệp vụ thật sẽ bổ sung sau: đăng ký và xác thực OTP, recovery codes, audit log.
    // Đây chỉ là cờ tùy chọn giao diện; KHÔNG cho phép khẳng định 2FA đã hoạt động.
    @PostMapping("/settings/2fa-preference")
    public String twoFactorPreference(@RequestParam(defaultValue = "false") boolean enabled,
                                      Authentication authentication, RedirectAttributes attributes) {
        User user = currentUser.require(authentication);
        user.setTwoFactorPreference(enabled);
        users.save(user);
        attributes.addFlashAttribute("success", "Đã lưu tùy chọn demo; 2FA chưa bảo vệ đăng nhập.");
        return "redirect:/change-password";
    }

    private boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
```

---

## `src/main/java/com/skillrental/dto/BookingForm.java`

```java
package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;

@Data public class BookingForm {
    @NotBlank private String serviceName;
    @NotNull @FutureOrPresent @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate bookingDate;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.TIME) private LocalTime timeSlot;
    @Size(max=1200) private String note;
    @jakarta.validation.constraints.Min(2) @jakarta.validation.constraints.Max(2) private int durationHours = 2;
    private boolean premium;
    private boolean directBooking=true;
}
```

---

## `src/main/java/com/skillrental/entity/User.java`

```java
package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String fullName;
    @Column(nullable=false,unique=true,length=160) private String email;
    @Column(nullable=false) private String password;
    @Column(length=20) private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
    @Column(length=500) private String avatarUrl;
    @Column(length=1200) private String bio;
    @Column(length=120) private String resetToken;
    @Column(nullable=false) @Builder.Default private Boolean twoFactorPreference = false;
    private LocalDateTime createdAt;
    @PrePersist void pre() {
        if(createdAt==null) createdAt=LocalDateTime.now();
    }
}
```

---

## `src/main/java/com/skillrental/service/BookingService.java`

```java
package com.skillrental.service;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor public class BookingService {
    private final ProviderProfileRepository providers;
    private final BookingRepository bookings;
    private final RentalOrderRepository orders;
    @Transactional public RentalOrder create(User customer,Long providerId,BookingForm f) {
        var p=providers.findById(providerId).orElseThrow();
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            throw new IllegalArgumentException("Khung giờ đã qua");
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10,0), java.time.LocalTime.of(12,0),
                java.time.LocalTime.of(18,0), java.time.LocalTime.of(20,0)).contains(f.getTimeSlot())) {
            throw new IllegalArgumentException("Khung giờ không hợp lệ");
        }
        BigDecimal rate=p.getHourlyRate().multiply(BigDecimal.valueOf(f.getDurationHours()));
        if(f.isPremium()) rate=rate.multiply(new BigDecimal("1.25"));
        var b=bookings.save(Booking.builder().customer(customer).provider(p.getUser()).serviceName(f.getServiceName()).bookingDate(f.getBookingDate()).timeSlot(f.getTimeSlot()).note(f.getNote()).premium(f.isPremium()).directBooking(f.isDirectBooking()).amount(rate).status(BookingStatus.PENDING).build());
        return orders.save(RentalOrder.builder().booking(b).customer(customer).provider(p.getUser()).subtotal(rate).discount(BigDecimal.ZERO).total(rate).status(OrderStatus.PENDING_PAYMENT).createdAt(LocalDateTime.now()).build());
    }
}
```

---

## `src/main/java/com/skillrental/service/RequestService.java`

```java
package com.skillrental.service;

import com.skillrental.dto.ServiceRequestForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor public class RequestService {
    private final ServiceRequestRepository requests;
    private final ApplyRepository applies;
    public List<ServiceRequest> list(User u) {
        return requests.findByCustomerOrderByIdDesc(u);
    }
    @Transactional public ServiceRequest create(User u,ServiceRequestForm f) {
        return requests.save(ServiceRequest.builder().customer(u).title(f.getTitle()).requiredSkills(f.getRequiredSkills()).description(f.getDescription()).budget(f.getBudget()).deadline(f.getDeadline()).status(RequestStatus.OPEN).build());
    }
    @Transactional
    public void updateOpenRequest(ServiceRequest request, ServiceRequestForm form) {
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalArgumentException("Chỉ sửa được Request đang mở");
        }
        request.setTitle(form.getTitle());
        request.setRequiredSkills(form.getRequiredSkills());
        request.setDescription(form.getDescription());
        request.setBudget(form.getBudget());
        request.setDeadline(form.getDeadline());
        requests.save(request);
    }

    public ServiceRequest owned(Long id,User u) {
        var r=requests.findById(id).orElseThrow();
        if(!r.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Không có quyền");
        return r;
    }
    public List<Apply> applies(ServiceRequest r) {
        return applies.findByRequestOrderByAppliedAtDesc(r);
    }
    @Transactional public void choose(ServiceRequest r,Long id) {
        var selected=applies.findById(id).orElseThrow();
        if (r.getStatus() != RequestStatus.OPEN || !selected.getRequest().getId().equals(r.getId())) {
            throw new IllegalArgumentException("Apply không hợp lệ cho Request này");
        }
        var all=applies.findByRequestOrderByAppliedAtDesc(r);
        all.forEach(a->a.setStatus(a.getId().equals(id)?ApplyStatus.ACCEPTED:ApplyStatus.REJECTED));
        applies.saveAll(all);
        r.setSelectedProvider(selected.getProvider());
        r.setStatus(RequestStatus.IN_PROGRESS);
        requests.save(r);
    }
}
```

---

## `src/main/resources/db/figma-upgrade-mysql.sql`

```sql
-- MySQL existing installation: back up database first.
ALTER TABLE users ADD COLUMN two_factor_preference BOOLEAN NOT NULL DEFAULT FALSE;
-- UI preference only, no real OTP/2FA authentication.
```

---

## `src/main/resources/static/css/app.css`

```css
:root {
    --primary:#16A34A;
    --primary-dark:#15803D;
    --primary-light:#DCFCE7;
    --secondary:#0F172A;
    --bg:#F8FAFC;
    --surface:#fff;
    --text:#1E293B;
    --muted:#64748B;
    --border:#E2E8F0;
    --success:#22C55E;
    --warning:#F59E0B;
    --error:#EF4444;
    --info:#3B82F6
}
* {
    box-sizing:border-box
}
body {
    margin:0;
    font-family:Inter,Arial,sans-serif;
    background:var(--bg);
    color:var(--text)
}
a {
    text-decoration:none;
    color:inherit
}
button,input,select,textarea {
    font:inherit
}
.container {
    width:min(1180px,calc(100% - 32px));
    margin:auto
}
.header {
    height:72px;
    background:#fff;
    border-bottom:1px solid var(--border);
    position:sticky;
    top:0;
    z-index:20
}
.header .container {
    height:100%;
    display:flex;
    align-items:center;
    gap:22px
}
.logo {
    display:flex;
    align-items:center;
    gap:9px;
    color:var(--primary);
    font-weight:800
}
.logo i {
    width:34px;
    height:34px;
    background:var(--primary);
    color:#fff;
    border-radius:8px;
    display:grid;
    place-items:center;
    font-style:normal
}
.nav {
    display:flex;
    gap:16px;
    color:var(--muted);
    font-size:14px;
    font-weight:600
}
.nav a:hover {
    color:var(--primary)
}
.user {
    margin-left:auto;
    display:flex;
    align-items:center;
    gap:10px
}
.avatar {
    width:40px;
    height:40px;
    border-radius:50%;
    object-fit:cover;
    border:1px solid var(--border)
}
.page {
    padding:30px 0 50px
}
.layout {
    display:grid;
    grid-template-columns:230px 1fr;
    gap:24px
}
.sidebar {
    background:#fff;
    border:1px solid var(--border);
    border-radius:12px;
    padding:14px;
    height:max-content;
    position:sticky;
    top:96px
}
.sidebar a {
    display:block;
    padding:10px 12px;
    border-radius:8px;
    color:var(--muted);
    font-size:14px;
    font-weight:600
}
.sidebar a:hover {
    background:var(--primary-light);
    color:var(--primary)
}
.card {
    background:#fff;
    border:1px solid var(--border);
    border-radius:12px;
    padding:20px
}
.grid {
    display:grid;
    gap:18px
}
.grid2 {
    grid-template-columns:repeat(2,minmax(0,1fr))
}
.grid3 {
    grid-template-columns:repeat(3,minmax(0,1fr))
}
h1,h2,h3 {
    color:var(--secondary)
}
.title {
    font-size:28px;
    margin:0
}
.muted {
    color:var(--muted)
}
.hero {
    background:linear-gradient(120deg,#fff 55%,var(--primary-light));
    border:1px solid var(--border);
    border-radius:16px;
    padding:40px;
    display:grid;
    grid-template-columns:1.5fr .6fr;
    gap:24px
}
.hero h1 {
    font-size:42px;
    line-height:1.1;
    margin:0 0 14px
}
.hero .art {
    display:grid;
    place-items:center;
    font-size:72px
}
.btn {
    height:44px;
    border-radius:8px;
    padding:0 18px;
    border:1px solid transparent;
    font-weight:700;
    cursor:pointer;
    display:inline-flex;
    align-items:center;
    justify-content:center
}
.btn-primary {
    background:var(--primary);
    color:#fff
}
.btn-primary:hover {
    background:var(--primary-dark)
}
.btn-secondary {
    background:#fff;
    border-color:var(--border)
}
.btn-outline {
    background:#fff;
    border-color:var(--primary);
    color:var(--primary)
}
.btn-danger {
    background:var(--error);
    color:#fff
}
.btn-sm {
    height:36px;
    font-size:13px;
    padding:0 12px
}
.form-group {
    margin-bottom:15px
}
label {
    display:block;
    font-weight:700;
    font-size:13px;
    margin-bottom:7px
}
.input,.select,.textarea {
    width:100%;
    border:1px solid var(--border);
    border-radius:8px;
    background:#fff
}
.input,.select {
    height:44px;
    padding:0 12px
}
.textarea {
    min-height:120px;
    padding:12px
}
.row {
    display:grid;
    grid-template-columns:1fr 1fr;
    gap:14px
}
.error {
    color:var(--error);
    font-size:12px
}
.alert {
    padding:12px 14px;
    border-radius:8px;
    margin:12px 0
}
.success {
    background:#ECFDF5;
    color:#166534
}
.danger {
    background:#FEF2F2;
    color:#991B1B
}
.badge {
    padding:5px 9px;
    border-radius:16px;
    font-size:12px;
    font-weight:700;
    display:inline-block
}
.pending {
    background:#FEF3C7;
    color:#92400E
}
.info {
    background:#DBEAFE;
    color:#1D4ED8
}
.done {
    background:#DCFCE7;
    color:#15803D
}
.bad {
    background:#FEE2E2;
    color:#B91C1C
}
.provider {
    display:flex;
    gap:12px;
    align-items:center
}
.provider img {
    width:58px;
    height:58px;
    border-radius:50%;
    object-fit:cover
}
.tags {
    display:flex;
    gap:7px;
    flex-wrap:wrap;
    margin:12px 0
}
.tag {
    font-size:12px;
    border:1px solid var(--border);
    background:var(--bg);
    padding:5px 9px;
    border-radius:16px
}
.price {
    color:var(--primary);
    font-weight:800
}
.actions {
    display:flex;
    gap:8px;
    flex-wrap:wrap
}
table {
    width:100%;
    border-collapse:collapse;
    background:#fff;
    border:1px solid var(--border)
}
th,td {
    padding:13px;
    border-bottom:1px solid var(--border);
    text-align:left;
    font-size:14px
}
th {
    background:#F8FAFC;
    color:var(--muted);
    font-size:12px
}
.tablewrap {
    overflow:auto;
    border-radius:12px
}
.auth {
    min-height:100vh;
    display:grid;
    grid-template-columns:1fr 1fr;
    background:#fff
}
.auth-art {
    background:linear-gradient(135deg,#0F172A,#14532D);
    color:#fff;
    padding:60px;
    display:flex;
    flex-direction:column;
    justify-content:center
}
.auth-art h1 {
    color:#fff;
    font-size:44px
}
.auth-box {
    display:grid;
    place-items:center;
    padding:36px
}
.auth-card {
    width:min(440px,100%)
}
.timeline {
    display:grid;
    grid-template-columns:repeat(4,1fr);
    margin:30px 0
}
.step {
    text-align:center
}
.dot {
    width:36px;
    height:36px;
    border-radius:50%;
    border:2px solid var(--border);
    display:grid;
    place-items:center;
    margin:auto
}
.step.on .dot {
    background:var(--primary);
    border-color:var(--primary);
    color:#fff
}
.chat {
    display:grid;
    grid-template-columns:280px 1fr;
    min-height:580px;
    border:1px solid var(--border);
    border-radius:12px;
    overflow:hidden;
    background:#fff
}
.chat-list {
    border-right:1px solid var(--border);
    background:#F8FAFC
}
.chat-person {
    display:flex;
    gap:10px;
    padding:13px;
    border-bottom:1px solid var(--border)
}
.chat-main {
    display:flex;
    flex-direction:column
}
.chat-head {
    padding:13px;
    border-bottom:1px solid var(--border)
}
.messages {
    flex:1;
    padding:18px;
    display:flex;
    flex-direction:column;
    gap:9px
}
.bubble {
    max-width:70%;
    background:#F1F5F9;
    padding:10px 12px;
    border-radius:12px
}
.bubble.own {
    margin-left:auto;
    background:var(--primary);
    color:#fff
}
.chat-form {
    display:flex;
    gap:8px;
    padding:12px;
    border-top:1px solid var(--border)
}
.chat-form .input {
    flex:1
}
@media(max-width:900px) {
    .layout {
        grid-template-columns:1fr
    }
    .sidebar {
        position:static;
        display:flex;
        overflow:auto
    }
    .grid3 {
        grid-template-columns:1fr 1fr
    }
    .nav {
        display:none
    }
    .auth {
        grid-template-columns:1fr
    }
    .auth-art {
        display:none
    }
    .chat {
        grid-template-columns:1fr
    }
    .chat-list {
        display:none
    }
}
@media(max-width:640px) {
    .grid2,.grid3,.row {
        grid-template-columns:1fr
    }
    .hero {
        grid-template-columns:1fr;
        padding:26px
    }
    .hero .art {
        display:none
    }
    .hero h1 {
        font-size:34px
    }
}
/* ĐIỀU CHỈNH UI SAU REVIEW — GIỮ NGUYÊN BẢNG MÀU GỐC */
html {
    font-size:16px
}
body {
    font-size:16px;
    line-height:1.55
}
button,input,select,textarea {
    font-size:1rem
}
.title {
    font-size:clamp(1.6rem,2.7vw,2rem)
}
.muted {
    font-size:.96rem
}
label,th,.tag,.badge {
    font-size:.91rem
}
td {
    font-size:.96rem
}
h3 {
    font-size:1.18rem
}
.btn {
    font-size:.96rem
}
.btn-sm {
    font-size:.92rem;
    height:40px
}
.btn-primary {
    background:#16A34A;
    color:#fff;
    border-color:#16A34A;
    box-shadow:0 2px 5px rgba(22,163,74,.12)
}
.btn-primary:hover,.btn-primary:focus-visible {
    background:#15803D;
    border-color:#15803D;
    color:#fff
}
.rating-star {
    color:#F59E0B;
    font-size:1.15em;
    vertical-align:middle
}
.icon-sprite {
    position:absolute;
    width:0;
    height:0;
    overflow:hidden
}
.icon {
    width:24px;
    height:24px;
    min-width:24px;
    min-height:24px;
    fill:none;
    stroke:currentColor;
    stroke-width:1.8;
    stroke-linecap:round;
    stroke-linejoin:round;
    vertical-align:middle
}
.header .container.header-inner {
    width:min(1480px,calc(100% - 32px));
    gap:16px;
    justify-content:space-between
}
.header .logo {
    flex-shrink:0
}
.logo-mark {
    display:grid;
    place-items:center;
    width:36px;
    height:36px;
    border-radius:8px;
    color:#fff;
    background:var(--primary)
}
.header .nav {
    display:flex;
    gap:2px;
    align-items:center;
    justify-content:center;
    flex-wrap:wrap
}
.nav-item,.side-item {
    height:44px;
    min-height:44px;
    padding:0 16px;
    display:inline-flex;
    align-items:center;
    gap:8px;
    border:0;
    background:transparent;
    cursor:pointer;
    white-space:nowrap;
    font-size:.94rem;
    text-align:left;
    font-weight:600;
    line-height:1.1;
    color:var(--muted);
    border-radius:8px
}
.nav-item:hover,.side-item:hover {
    color:var(--primary);
    background:var(--primary-light)
}
.header-actions {
    display:flex;
    align-items:center;
    gap:10px;
    margin-left:auto
}
.menu-wrap {
    position:relative
}
.menu-wrap>summary {
    list-style:none;
    cursor:pointer
}
.menu-wrap>summary::-webkit-details-marker {
    display:none
}
.icon-button {
    width:44px;
    height:44px;
    display:grid;
    place-items:center;
    background:transparent;
    border:1px solid var(--border);
    border-radius:8px;
    cursor:pointer;
    color:var(--text)
}
.avatar-button {
    height:44px;
    display:flex;
    align-items:center;
    gap:8px;
    border-radius:8px;
    padding:2px 6px;
    border:1px solid var(--border);
    cursor:pointer
}
.username {
    font-size:.9rem;
    max-width:130px;
    overflow:hidden;
    text-overflow:ellipsis;
    white-space:nowrap
}
.menu-panel {
    position:absolute;
    z-index:65;
    right:0;
    top:calc(100% + 9px);
    min-width:250px;
    padding:8px;
    background:#fff;
    border:1px solid var(--border);
    border-radius:12px;
    box-shadow:0 14px 38px rgba(15,23,42,.12)
}
.menu-panel>p {
    padding:8px
}
.menu-panel a,.menu-logout {
    width:100%;
    height:44px;
    border:none;
    background:none;
    display:flex;
    align-items:center;
    gap:8px;
    padding:0 12px;
    font-weight:600;
    font-size:.95rem;
    border-radius:8px;
    color:var(--text);
    cursor:pointer;
    text-align:left
}
.menu-panel a:hover,.menu-logout:hover {
    background:var(--primary-light);
    color:var(--primary)
}
.management-layout {
    grid-template-columns:248px minmax(0,1fr)
}
.page-content {
    min-width:0
}
.sidebar {
    position:sticky;
    top:18px;
    padding:12px
}
.sidebar .side-item {
    display:flex;
    width:100%;
    padding:0 16px;
    gap:8px
}
.sidebar-logo {
    padding:10px 12px 16px
}
.sidebar-caption {
    margin:12px 16px;
    color:var(--muted);
    font-size:.8rem;
    font-weight:800;
    letter-spacing:.09em
}
.side-button {
    font-family:inherit
}
.sidebar form {
    margin:0
}
.public-content {
    min-width:0
}
.footer {
    border-top:1px solid var(--border);
    background:#fff;
    margin-top:auto;
    padding:20px 0;
    color:var(--muted);
    font-size:.92rem
}
.footer-inner {
    display:flex;
    justify-content:space-between;
    gap:12px
}
.footer a {
    color:var(--primary);
    font-weight:700
}
body {
    min-height:100vh;
    display:flex;
    flex-direction:column
}
main.page {
    flex:1
}
.section-top {
    display:flex;
    align-items:center;
    justify-content:space-between;
    gap:12px
}
.filter-card {
    margin:20px 0
}
.filter-grid {
    display:grid;
    grid-template-columns:1fr 1fr 1fr;
    gap:18px
}
.filter-card #skill-filter {
    margin-bottom:6px
}
.filter-card #skill-select {
    margin-bottom:6px
}
.price-field {
    border:0;
    padding:0;
    margin:0 0 22px
}
.price-field legend {
    font-size:.95rem;
    font-weight:700;
    margin-bottom:10px
}
.price-current {
    display:flex;
    gap:12px;
    align-items:center;
    justify-content:space-between;
    font-weight:700;
    color:var(--secondary)
}
.range-dual {
    position:relative;
    height:44px;
    margin:12px 10px 0
}
.range-track,.range-fill {
    position:absolute;
    top:18px;
    height:6px;
    border-radius:8px;
    left:0;
    right:0;
    background:var(--border)
}
.range-fill {
    right:auto;
    background:var(--primary);
    pointer-events:none
}
.range-dual input[type=range] {
    position:absolute;
    left:0;
    top:0;
    width:100%;
    height:44px;
    margin:0;
    appearance:none;
    background:transparent;
    pointer-events:none;
    outline:none
}
.range-dual input[type=range]::-webkit-slider-thumb {
    appearance:none;
    pointer-events:auto;
    width:22px;
    height:22px;
    background:#fff;
    border:3px solid var(--primary);
    border-radius:50%;
    cursor:grab;
    box-shadow:0 2px 6px #0002
}
.range-dual input[type=range]::-moz-range-thumb {
    pointer-events:auto;
    width:16px;
    height:16px;
    background:#fff;
    border:3px solid var(--primary);
    border-radius:50%;
    cursor:grab
}
.range-dual input[type=range]:focus-visible::-webkit-slider-thumb {
    outline:3px solid var(--info)
}
/* Login viewport: ẩn phần minh họa khi chiều cao nhỏ; form luôn trong viewport phổ biến */
.login-body {
    height:100vh;
    height:100dvh;
    min-height:0;
    overflow:hidden
}
.auth-login {
    height:100vh;
    height:100dvh;
    min-height:0;
    grid-template-columns:minmax(0,1fr) minmax(0,1fr);
    overflow:hidden
}
.auth-login .auth-art {
    min-width:0;
    min-height:0;
    padding:clamp(18px,4vw,60px)
}
.auth-login .auth-art h2 {
    color:#fff;
    font-size:clamp(1.6rem,3vw,2.75rem);
    line-height:1.15
}
.auth-login .auth-box {
    min-width:0;
    min-height:0;
    padding:clamp(10px,3vh,30px);
    overflow:hidden
}
.auth-login .auth-card {
    max-width:440px;
    width:100%;
    max-height:100%;
    overflow:hidden
}
.auth-brand {
    display:flex;
    align-items:center;
    gap:10px;
    color:#fff;
    font-size:1.13rem;
    font-weight:800
}
.auth-brand-mobile {
    color:var(--primary);
    margin-bottom:14px
}
.auth-login .auth-site-title {
    color:var(--secondary);
    font-size:clamp(1.3rem,2.8vh,1.85rem);
    line-height:1.15;
    margin:0 0 6px
}
.auth-form-title {
    font-size:clamp(1.45rem,3vh,2rem);
    margin:0 0 4px
}
.auth-note {
    margin:0 0 clamp(8px,2vh,20px)
}
.login-options {
    display:flex;
    justify-content:space-between;
    align-items:center;
    flex-wrap:wrap;
    gap:8px;
    margin-bottom:14px;
    font-size:.9rem
}
.checkbox-label {
    margin:0;
    font-weight:500
}
.login-options a,.auth-bottom a {
    color:var(--primary);
    font-weight:700
}
.login-submit {
    width:100%
}
.auth-bottom {
    margin:12px 0 0;
    text-align:center
}
.login-body .form-group {
    margin-bottom:12px
}
.password-wrap {
    position:relative
}
.password-wrap .input {
    padding-left:45px;
    padding-right:48px
}
.input-lock {
    position:absolute;
    left:12px;
    top:10px;
    color:var(--muted)
}
.password-toggle {
    position:absolute;
    right:2px;
    top:0;
    border:0;
    background:transparent;
    width:44px;
    height:44px;
    display:grid;
    place-items:center;
    cursor:pointer;
    color:var(--muted)
}
/* Floating chat */
.chat-widget {
    position:fixed;
    right:22px;
    bottom:20px;
    z-index:90
}
.chat-fab {
    border:0;
    border-radius:24px;
    padding:0 17px;
    height:54px;
    display:flex;
    align-items:center;
    gap:8px;
    background:var(--primary);
    color:#fff;
    box-shadow:0 6px 25px #0003;
    font-weight:700;
    cursor:pointer
}
.chat-fab:hover {
    background:var(--primary-dark)
}
.chat-popup {
    position:absolute;
    right:0;
    bottom:68px;
    width:min(410px,calc(100vw - 26px));
    height:min(570px,calc(100dvh - 110px));
    background:#fff;
    border:1px solid var(--border);
    border-radius:16px;
    box-shadow:0 12px 48px #0003;
    overflow:hidden;
    display:flex;
    flex-direction:column
}
.chat-popup[hidden] {
    display:none
}
.chat-popup-head {
    height:54px;
    display:flex;
    align-items:center;
    justify-content:space-between;
    padding:0 12px 0 18px;
    border-bottom:1px solid var(--border)
}
.chat-popup-body {
    display:grid;
    grid-template-columns:115px 1fr;
    flex:1;
    min-height:0
}
.chat-contacts {
    border-right:1px solid var(--border);
    background:var(--bg);
    overflow-y:auto
}
.chat-contact {
    width:100%;
    border:0;
    background:none;
    text-align:left;
    padding:12px 8px;
    font-size:.84rem;
    cursor:pointer;
    overflow-wrap:anywhere
}
.chat-contact.active,.chat-contact:hover {
    background:var(--primary-light);
    color:var(--primary)
}
.chat-pane {
    display:flex;
    flex-direction:column;
    min-width:0
}
.chat-peer {
    padding:9px;
    font-size:.9rem;
    font-weight:700;
    border-bottom:1px solid var(--border)
}
.chat-history {
    flex:1;
    min-height:0;
    overflow-y:auto;
    display:flex;
    flex-direction:column;
    gap:9px;
    padding:12px
}
.chat-message {
    max-width:90%;
    font-size:.9rem;
    border-radius:12px;
    background:var(--bg);
    padding:8px 10px;
    overflow-wrap:anywhere
}
.chat-message.own {
    background:var(--primary-light);
    align-self:flex-end
}
.chat-message small {
    display:block;
    color:var(--muted);
    font-size:.75rem
}
.chat-compose {
    display:flex;
    gap:6px;
    padding:8px;
    border-top:1px solid var(--border)
}
.chat-compose .input {
    min-width:0
}
.chat-feedback {
    padding:6px 12px;
    color:var(--error);
    margin:0
}
/* Sao kiểu chọn trực tiếp */
.star-picker {
    border:0;
    margin:0 0 16px;
    padding:0
}
.star-picker legend {
    font-size:.95rem;
    font-weight:700
}
.star-options {
    display:flex;
    align-items:center;
    gap:4px
}
.star-options input {
    position:absolute;
    opacity:0;
    width:1px;
    height:1px
}
.star-options label {
    font-size:2.35rem;
    cursor:pointer;
    color:#CBD5E1;
    line-height:1.2;
    margin:0;
    padding:3px 4px
}
.star-options label.selected,.star-options label.preview {
    color:#F59E0B
}
.star-options input:focus-visible+label {
    outline:2px solid var(--info);
    border-radius:6px
}
.star-hint {
    font-size:.9rem;
    color:var(--muted)
}
/* Modal đơn hàng */
.icon-actions {
    display:flex;
    gap:6px;
    align-items:center
}
.action-icon {
    width:44px;
    height:44px;
    border:1px solid var(--border);
    border-radius:8px;
    background:#fff;
    display:grid;
    place-items:center;
    cursor:pointer;
    color:var(--text)
}
.action-icon:hover {
    border-color:var(--primary);
    color:var(--primary)
}
.action-icon[disabled] {
    opacity:.35;
    cursor:not-allowed
}
.order-modal {
    border:0;
    border-radius:16px;
    max-width:min(650px,calc(100% - 24px));
    width:100%;
    padding:0;
    box-shadow:0 25px 70px #0004
}
.order-modal::backdrop {
    background:rgba(15,23,42,.55)
}
.modal-head {
    display:flex;
    align-items:center;
    justify-content:space-between;
    padding:15px 20px;
    border-bottom:1px solid var(--border)
}
.modal-head h2 {
    margin:0
}
.modal-content {
    padding:20px;
    max-height:calc(100dvh - 165px);
    overflow:auto
}
.modal-timeline {
    margin:18px 0
}
.modal-timeline .step {
    font-size:.9rem
}
.modal-actions {
    display:flex;
    gap:8px;
    flex-wrap:wrap
}
.tablewrap {
    overflow:auto
}
table {
    min-width:640px
}
@media(max-width:1180px) {
    .header .nav {
        gap:0
    }
    .nav-item {
        padding:0 9px
    }
    .username {
        display:none
    }
}
@media(max-width:900px) {
    .header .nav {
        display:flex;
        overflow-x:auto;
        justify-content:flex-start;
        flex:1;
        flex-wrap:nowrap
    }
    .header .nav .nav-item {
        font-size:0;
        min-width:44px;
        justify-content:center
    }
    .header-actions {
        margin-left:0
    }
    .management-layout {
        grid-template-columns:1fr
    }
    .sidebar {
        position:static;
        display:flex;
        align-items:center;
        overflow:auto;
        gap:4px
    }
    .sidebar-logo,.sidebar-caption {
        display:none
    }
    .sidebar .side-item {
        width:auto;
        min-width:44px
    }
    .sidebar .side-item span {
        white-space:nowrap
    }
    .sidebar form {
        flex-shrink:0
    }
    .filter-grid {
        grid-template-columns:1fr 1fr
    }
}
@media(max-width:650px) {
    .filter-grid,.grid2,.grid3,.row {
        grid-template-columns:1fr
    }
    .auth-login {
        grid-template-columns:1fr
    }
    .auth-login .auth-art {
        display:none
    }
    .auth-login .auth-box {
        display:grid
    }
    .hero {
        padding:24px
    }
    .footer-inner {
        flex-direction:column
    }
    .header .nav {
        display:none
    }
    .chat-widget {
        right:12px;
        bottom:12px
    }
    .chat-popup {
        height:min(520px,calc(100dvh - 88px))
    }
    .login-body .auth-card {
        padding:0
    }
}
@media(max-height:690px) {
    .auth-login .auth-art {
        display:none
    }
    .auth-login {
        grid-template-columns:1fr
    }
    .auth-login .auth-box {
        padding:8px 18px
    }
    .auth-login .auth-brand-mobile {
        margin-bottom:5px
    }
    .auth-login .form-group {
        margin-bottom:8px
    }
    .auth-login .input {
        height:40px
    }
    .auth-login .auth-note {
        margin-bottom:7px
    }
    .auth-login .btn {
        height:40px
    }
}
@media(max-height:450px) {
    .auth-login .auth-brand-mobile {
        display:none
    }
    .auth-login .auth-site-title,.auth-login .auth-form-title {
        font-size:1.15rem
    }
    .auth-login .auth-note {
        font-size:.8rem
    }
    .auth-login .form-group {
        margin-bottom:4px
    }
    .auth-login label {
        margin-bottom:2px
    }
    .auth-login .input {
        height:32px
    }
    .auth-login .login-options {
        margin-bottom:5px
    }
    .auth-login .btn {
        height:34px
    }
    .auth-login .auth-bottom {
        margin-top:4px
    }
}
.action-icon.is-disabled {
    opacity:.35;
    cursor:not-allowed;
    pointer-events:none
}
/* ================================================================
   SKILL RENTAL / FIGMA UPGRADE
   Scoped extensions: existing button/card/sidebar/chat/review styles retained.
   ================================================================ */
:root { --primary:#16A34A; --primary-dark:#15803D; --primary-light:#DCFCE7;
  --secondary:#0F172A; --bg:#F8FAFC; --border:#E2E8F0; --warning:#F59E0B; }
.sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; }
.public-page .public-content { max-width:1280px; }
.public-page .header .container { width:min(1300px,calc(100% - 32px)); gap:13px; }
.header-quick-search { flex:1; max-width:210px; min-width:118px; display:flex; align-items:center;
  background:#F8FAFC; border:1px solid var(--border); border-radius:9px; height:40px; padding:0 8px; }
.header-quick-search input { width:100%; min-width:0; border:0; background:transparent; outline:0; color:var(--secondary); font-size:13px; }
.header-quick-search button { border:0; background:transparent; cursor:pointer; color:var(--primary); font-size:24px; }
.public-page .header-inner .nav { gap:2px; }
.public-page .header-inner .nav-item { padding:0 9px; white-space:nowrap; }
.public-page .header-inner .nav-item .icon { width:18px; height:18px; }
.public-page .header-actions { gap:8px; }

/* 1. Login: on conventional laptop screens the entire view remains within 100dvh. */
body.login-v2 { background:#F4F9FF; overflow:hidden; }
.login-split { height:100vh; height:100dvh; display:grid; grid-template-columns:47% 53%; min-height:0; }
.login-story { position:relative; display:flex; flex-direction:column; justify-content:space-between;
  min-height:0; padding:26px clamp(24px,4vw,64px) 24px; color:#fff; overflow:hidden;
  background:linear-gradient(140deg,#064E3B 0%,#052E24 65%,#022C22 100%); }
.login-story:before { content:""; position:absolute; bottom:-21%; left:-12%; right:-12%; height:57%; opacity:.35;
  background:repeating-linear-gradient(90deg,transparent 0 31px,#4ADE80 32px 33px),
             repeating-linear-gradient(0deg,transparent 0 31px,#4ADE80 32px 33px);
  transform:perspective(350px) rotateX(60deg); pointer-events:none; }
.login-story .auth-brand { z-index:1; color:#fff; font-size:20px; font-weight:800; display:flex; align-items:center; gap:10px; }
.login-story .logo-mark { background:#16A34A; }
.login-story-body { position:relative; z-index:1; flex:1; display:flex; flex-direction:column; justify-content:center;
  max-width:520px; min-height:0; }
.login-story h2 { font-size:clamp(28px,3vw,48px); line-height:1.13; margin:0 0 14px; color:white; }
.login-story-body>p { font-size:clamp(14px,1.4vw,18px); max-width:420px; color:#D1FAE5; margin:0 0 10px; }
.login-decoration { position:relative; height:clamp(100px,24vh,225px); margin-top:12px; }
.deco-cube { display:block; width:115px; height:115px; position:absolute; top:42%; left:34%; border:2px solid #34D399;
  background:linear-gradient(135deg,#10B98155,transparent); transform:translateY(-50%) rotate(30deg) skew(-15deg,-15deg);
  box-shadow:0 0 45px #34D39955, 36px 36px 0 -2px #0B6B4D66; }
.deco-orbit { position:absolute; inset:10% 17%; border:1px solid #A7F3D055; border-radius:50%; transform:rotate(-22deg); }
.login-expert,.login-stat { position:absolute; z-index:2; display:flex; padding:10px 12px; gap:9px; align-items:center;
 background:#FFFFFF1C; border:1px solid #FFFFFF33; color:white; border-radius:12px; backdrop-filter:blur(9px); }
.login-expert { left:0; bottom:clamp(54px,13vh,120px); font-size:12px; }
.login-expert img { width:34px; height:34px; border-radius:50%; object-fit:cover; }
.login-expert small { opacity:.92; }
.login-stat { left:46%; bottom:20px; flex-direction:column; align-items:flex-start; max-width:210px; }
.login-stat strong { font-size:clamp(22px,2.5vw,36px); line-height:1; }
.login-stat span { font-size:12px; }
.login-copyright { position:relative; z-index:2; border-top:1px solid #FFFFFF25; padding-top:16px; font-size:12px; color:#D1FAE5; }
.login-panel { display:grid; place-items:center; padding:16px 5vw; min-width:0; min-height:0; }
.login-form-card { width:min(100%,440px); border-radius:15px; border:1px solid #D7E5E7; box-shadow:0 12px 36px #0F172A12;
  background:white; padding:clamp(20px,2.5vw,34px); }
.login-form-card h1 { font-size:clamp(21px,1.85vw,27px); margin:0 0 4px; }
.login-form-card>p.muted { margin:0 0 19px; font-size:13px; }
.login-form-card .form-group { margin-bottom:15px; }
.login-form-card .input { height:42px; }
.label-between { display:flex; justify-content:space-between; align-items:center; gap:10px; }
.label-between a,.login-signup a { font-size:13px; color:var(--primary-dark); font-weight:650; }
.login-form-card .checkbox-label { font-weight:500; margin:10px 0 17px; display:flex; align-items:center; gap:8px; }
.login-submit { width:100%; height:44px; }
.login-divider { position:relative; text-align:center; color:var(--muted); font-size:12px; margin:16px 0; }
.login-divider:before { content:""; position:absolute; left:0; right:0; top:50%; height:1px; background:var(--border); }
.login-divider span { position:relative; background:white; padding:0 10px; }
.login-socials { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; }
.login-socials button { height:40px; border:1px solid var(--border); border-radius:8px; background:white; font-size:23px;
  font-weight:800; color:#475569; opacity:.75; cursor:not-allowed; }
.login-signup { text-align:center; margin:18px 0 0; font-size:13px; }
@media (max-height:720px) and (min-width:720px) {
 .login-story { padding-top:16px; padding-bottom:14px; }
 .login-story h2 { font-size:32px; }
 .login-decoration { height:100px; }
 .login-expert { bottom:55px; }
 .login-stat { bottom:0; }
 .login-form-card { padding:17px 24px; }
 .login-form-card>p.muted { margin-bottom:8px; }
 .login-form-card .form-group { margin-bottom:8px; }
 .login-divider { margin:10px 0; }
 .login-signup { margin-top:9px; }
}
@media (max-width:719px) {
 body.login-v2 { overflow:auto; }
 .login-split { height:auto; min-height:100dvh; grid-template-columns:1fr; }
 .login-story { padding:14px 20px; min-height:0; }
 .login-story-body,.login-copyright { display:none; }
 .login-panel { padding:20px 12px; }
}

/* 2. Marketplace and provider cards */
.market-hero { display:grid; grid-template-columns:1fr 250px; min-height:235px; gap:24px; align-items:center;
 background:linear-gradient(105deg,#D9FBEA,#E9FFF5 64%,#D1FAE5); border-radius:16px; padding:30px clamp(22px,6vw,70px); }
.market-hero-copy { text-align:center; }
.market-hero h1 { color:var(--secondary); font-size:clamp(23px,2.5vw,34px); line-height:1.18; margin:5px 0 10px; }
.market-hero p { max-width:630px; margin:0 auto 18px; }
.market-hero .actions { justify-content:center; }
.eyebrow { color:var(--primary-dark); letter-spacing:.05em; font-size:11px; font-weight:800; }
.hero-polygon { width:155px; height:155px; margin:auto; clip-path:polygon(50% 0%,90% 20%,100% 65%,70% 100%,20% 95%,0% 40%);
 background:linear-gradient(140deg,#98E4C5,#16A34A77 45%,#0D8C6990); box-shadow:inset -18px -30px 70px #065F4622; }
.hero-search { max-width:550px; margin:18px auto 12px; }
.hero-search>label { text-align:center; font-size:14px; }
.hero-search .search-field { display:flex; gap:6px; }
.hero-search .search-field .input { flex:1; min-width:0; }
.hero-search .search-field .btn { width:48px; font-size:28px; padding:0; }
.skill-chips { display:flex; flex-wrap:wrap; gap:7px; justify-content:center; margin-bottom:26px; }
.skill-chip { background:#fff; border:1px solid var(--border); border-radius:18px; padding:5px 11px; font-size:12px; }
.skill-chip:hover { border-color:var(--primary); background:var(--primary-light); }
.more-link { color:var(--primary-dark); font-weight:700; }
.expert-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin-top:14px; }
.expert-card { display:flex; flex-direction:column; min-width:0; gap:9px; padding:16px; transition:box-shadow .2s, transform .2s; }
.expert-card:hover { transform:translateY(-2px); box-shadow:0 10px 24px #0F172A12; }
.expert-person { display:flex; align-items:center; gap:9px; min-width:0; }
.expert-person img { width:43px; height:43px; border-radius:50%; flex:none; object-fit:cover; }
.expert-person>div { min-width:0; }
.expert-person h3 { margin:0; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; font-size:14px; }
.verified { color:#15803D; font-size:10px; }
.expert-rating { font-size:11px; color:var(--muted); }
.expert-rating strong { color:var(--secondary); }
.expert-headline { margin:0; font-size:13px; font-weight:700; min-height:39px; }
.expert-gallery { display:grid; grid-template-columns:repeat(3,1fr); gap:5px; }
.portfolio-thumb { min-width:0; height:68px; display:grid; place-items:center; border-radius:7px;
 font-size:12px; font-weight:800; color:white; letter-spacing:.08em; }
.pt-1 { background:linear-gradient(140deg,#0F172A,#3B82F6); }
.pt-2 { background:linear-gradient(140deg,#14532D,#22C55E); }
.pt-3 { background:linear-gradient(140deg,#334155,#64748B); }
.expert-skills { margin:0!important; gap:4px!important; }
.expert-skills .tag { padding:3px 7px; font-size:10px; }
.expert-hours { font-size:11px; color:var(--muted); margin:0; }
.expert-price { padding-top:10px; border-top:1px solid var(--border); font-size:13px; margin-top:auto; }
.expert-price strong { font-size:17px; color:var(--secondary); }
.expert-actions { flex-wrap:nowrap; gap:7px; }
.expert-actions>.btn { flex:1; padding:0 6px; min-width:0; font-size:11px; white-space:nowrap; }
.filter-selected { display:inline-flex; gap:7px; align-items:center; color:var(--primary-dark); background:var(--primary-light);
 border-radius:14px; padding:6px 10px; }
.filter-selected a { font-size:20px; }

/* 3. Provider profile */
.provider-detail-layout { display:grid; grid-template-columns:minmax(0,1.75fr) minmax(270px,1fr); gap:24px; align-items:start; }
.detail-main { display:grid; gap:18px; min-width:0; }
.detail-main .card h2 { font-size:18px; }
.expert-profile img { width:84px; height:84px; border-radius:50%; }
.expert-overview hr,.booking-summary hr,.advanced-security hr { border:0; border-top:1px solid var(--border); margin:18px 0; }
.bio { line-height:1.7; }
.colorful-skills .tag { border:0; padding:6px 10px; color:#064E3B; }
.colorful-skills .skill-tint-0 { background:#DCFCE7; }.colorful-skills .skill-tint-1 { background:#DBEAFE; color:#1D4ED8; }
.colorful-skills .skill-tint-2 { background:#FEF3C7; color:#92400E; }.colorful-skills .skill-tint-3 { background:#F1F5F9; color:#334155; }
.portfolio-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
.portfolio-project { min-width:0; font-size:13px; }
.portfolio-project-art { height:118px; border-radius:9px; margin-bottom:6px; display:grid; place-items:center; }
.portfolio-project-art span { font-size:38px; color:#FFFFFF88; font-weight:800; }
.review-overview { display:grid; grid-template-columns:160px 1fr; align-items:center; gap:22px; }
.review-score { display:flex; flex-direction:column; text-align:center; gap:5px; }
.review-score strong { font-size:34px; color:var(--secondary); }
.review-score small { color:var(--muted); }
.rating-bars { display:grid; gap:8px; }
.rating-bar-row { display:grid; grid-template-columns:35px 1fr 20px; align-items:center; gap:7px; font-size:12px; }
.rating-track { height:7px; border-radius:7px; overflow:hidden; background:#E2E8F0; }
.rating-track>span { display:block; height:100%; border-radius:7px; background:#16A34A; }
.review-quote { border-top:1px solid var(--border); display:flex; gap:11px; padding-top:14px; margin-top:14px; }
.review-quote img { width:34px; height:34px; border-radius:50%; object-fit:cover; }
.review-quote p { margin:5px 0 0; }
.detail-price-card { position:sticky; top:92px; display:flex; flex-direction:column; gap:12px; padding:27px; }
.detail-price-card>p { margin:0; }.detail-price { color:var(--primary-dark); white-space:nowrap; }
.detail-price strong { font-size:clamp(26px,2.7vw,36px); }.detail-price-card .btn { width:100%; }

/* 4. Requests */
.request-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; margin:20px 0; }
.request-stats .card { position:relative; padding:15px; overflow:hidden; }.request-stats small { color:var(--muted); }
.stat-number { font-size:31px; font-weight:800; color:var(--secondary); }
.stat-symbol { position:absolute; right:14px; top:23px; color:#93C5FD; font-size:32px; }
.request-table-card { padding:0; }.request-table { min-width:860px; }.request-table th { font-size:12px; }
.request-table td { font-size:12px; vertical-align:middle; }.request-table td:first-child { font-weight:700; }
.avatar-stack { display:flex; align-items:center; padding-left:7px; }.avatar-stack img { width:30px; height:30px; margin-left:-7px; border:2px solid white; border-radius:50%; object-fit:cover; }
.avatar-stack span { border-radius:15px; background:#F1F5F9; font-size:11px; padding:3px 6px; }
.request-actions { display:flex; flex-direction:column; gap:4px; white-space:nowrap; }.request-actions a { color:var(--primary-dark); font-size:12px; }

/* 5. Booking */
.booking-stepper { display:flex; justify-content:center; align-items:center; gap:18px; margin:0 auto 24px; font-size:13px; }
.booking-stepper>div { display:flex; gap:8px; align-items:center; color:var(--muted); }
.booking-stepper b { display:grid; place-items:center; width:30px; height:30px; border-radius:8px; background:#F1F5F9; font-weight:800; }
.booking-stepper .is-current { color:var(--primary-dark); font-weight:750; }.booking-stepper .is-current b,.booking-stepper .is-done b { background:var(--primary); color:#fff; }
.booking-title { margin-bottom:18px; }.booking-layout { display:grid; grid-template-columns:1.1fr 1fr; gap:23px; align-items:start; }
.booking-form { display:grid; gap:5px; }.booking-form .form-group { margin-bottom:10px; }.booking-form label { font-size:14px; }
.date-picker { border:1px solid var(--border); border-radius:10px; padding:12px; max-width:410px; }
.calendar-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }
.calendar-top button { width:30px; height:30px; background:#F8FAFC; border:1px solid var(--border); border-radius:7px; cursor:pointer; }
.calendar-weekdays,.calendar-days { display:grid; grid-template-columns:repeat(7,1fr); gap:4px; text-align:center; }
.calendar-weekdays { font-size:11px; color:var(--muted); margin-bottom:5px; }
.calendar-days button { height:34px; min-width:0; border:0; background:transparent; border-radius:7px; cursor:pointer; color:var(--secondary); }
.calendar-days button:hover { background:#DCFCE7; }.calendar-days button[disabled] { opacity:.25; cursor:not-allowed; }
.calendar-days button.is-selected,.time-slots button.is-selected { background:var(--primary); color:white; }
.time-slots { display:flex; flex-wrap:wrap; gap:8px; }
.time-slots button { border:1px solid var(--border); border-radius:16px; padding:8px 11px; cursor:pointer; background:white; font-size:12px; }
.booking-submit { width:100%; margin-top:12px; }.booking-summary { position:sticky; top:92px; overflow:hidden; }
.booking-summary h2 { margin:-20px -20px 20px; padding:15px 20px; background:var(--secondary); color:#fff; font-size:17px; }
.booking-summary .provider { margin-bottom:22px; }.booking-summary .provider img { width:48px; height:48px; border-radius:50%; }
.booking-summary .provider p { margin:4px 0 0; color:var(--muted); font-size:12px; }
.booking-summary h3 { font-size:15px; }.price-line { display:flex; justify-content:space-between; align-items:center; gap:10px; margin:14px 0; font-size:13px; }
.price-line strong { white-space:nowrap; }.price-line.total strong { color:var(--primary-dark); font-size:24px; }
.booking-demo-notice { background:#ECFDF5; padding:11px; border-radius:8px; font-size:11px; color:#166534; }

/* 6. Settings */
.profile-meter { padding:15px 22px; margin-bottom:26px; }.profile-meter .section-top { margin-bottom:10px; }.profile-meter p { margin:4px 0; color:var(--muted); font-size:13px; }
.profile-track { height:9px; }.settings-title { margin:0; }.settings-grid { margin-top:24px; display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
.settings-tile { min-height:158px; display:flex; flex-direction:column; gap:9px; transition:transform .15s,border-color .15s; }.settings-tile:hover { transform:translateY(-2px); border-color:var(--primary); }
.settings-icon { width:38px; height:38px; border-radius:9px; background:var(--primary); color:#fff; display:grid; place-items:center; font-size:21px; }
.settings-tile strong { font-size:16px; }.settings-tile p { font-size:12px; color:var(--muted); margin:0; }
.empty-state { text-align:center; max-width:550px; margin:30px auto; }

/* 7. Password */
.password-column { width:min(100%,660px); margin:18px auto 0; display:grid; gap:15px; }
.password-column>.card { padding:22px 25px; }.password-meter { margin-top:10px; }
.meter-bars { display:grid; grid-template-columns:repeat(4,1fr); gap:4px; }.meter-bars span { height:5px; border-radius:4px; background:#E2E8F0; }
.password-meter[data-score="1"] .meter-bars span:nth-child(1) { background:#EF4444; }
.password-meter[data-score="2"] .meter-bars span:nth-child(-n+2) { background:#F59E0B; }
.password-meter[data-score="3"] .meter-bars span:nth-child(-n+3),.password-meter[data-score="4"] .meter-bars span { background:#16A34A; }
.password-meter small { display:block; margin-top:6px; color:var(--muted); }.password-match { position:absolute; right:48px; top:12px; color:var(--primary); font-weight:800; }
.advanced-security h2 { font-size:17px; margin-top:0; }.advanced-security h3 { font-size:14px; }
.security-setting { display:flex; justify-content:space-between; gap:15px; align-items:center; }.security-setting .muted { font-size:12px; }
.toggle-switch { display:block; position:relative; width:44px; height:24px; flex:none; cursor:pointer; }
.toggle-switch input { position:absolute; opacity:0; }.toggle-switch span { position:absolute; inset:0; background:#CBD5E1; border-radius:24px; }
.toggle-switch span:before { content:""; position:absolute; width:18px; height:18px; background:white; border-radius:50%; left:3px; top:3px; transition:transform .15s; }
.toggle-switch input:checked+span { background:var(--primary); }.toggle-switch input:checked+span:before { transform:translateX(20px); }
.toggle-switch input:focus-visible+span { outline:2px solid #0F172A; outline-offset:3px; }

@media (max-width:1150px) {
 .expert-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }
 .public-page .header-inner .nav-item { padding:0 5px; }
 .header-quick-search { max-width:155px; }
 .request-stats { grid-template-columns:repeat(2,minmax(0,1fr)); }
}
@media (max-width:900px) {
 .expert-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
 .provider-detail-layout,.booking-layout { grid-template-columns:1fr; }
 .detail-price-card,.booking-summary { position:static; }
 .market-hero { grid-template-columns:1fr 140px; padding:24px; }.hero-polygon { width:125px; height:125px; }
 .settings-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
 .header-quick-search { display:none; }
}
@media (max-width:620px) {
 .expert-grid { grid-template-columns:1fr; }
 .market-hero { grid-template-columns:1fr; }.hero-polygon { display:none; }
 .market-hero-copy { text-align:left; }.market-hero .actions { justify-content:flex-start; }
 .settings-grid,.request-stats { grid-template-columns:1fr; }
 .booking-stepper { gap:6px; font-size:11px; }.booking-stepper b { width:25px; height:25px; }
 .portfolio-grid { grid-template-columns:1fr 1fr; }.review-overview { grid-template-columns:1fr; }
}
```

---

## `src/main/resources/static/js/figma-ui.js`

```javascript
'use strict';

/* Skill Rental Figma enhancements. Original app.js owns chat/review/order modals. */
(() => {
    const calendar = document.getElementById('booking-calendar');
    const dateField = document.getElementById('booking-date');
    const slotField = document.getElementById('booking-time');
    const bookingForm = document.getElementById('booking-form');

    if (calendar && dateField && slotField && bookingForm) {
        const calendarMonth = document.getElementById('calendar-month');
        const days = document.getElementById('calendar-days');
        const previous = document.getElementById('calendar-prev');
        const next = document.getElementById('calendar-next');
        const premium = document.getElementById('booking-premium');
        const summary = document.querySelector('.booking-summary');
        const rate = Number(summary?.dataset.rate || 0);
        const money = amount => Math.round(amount).toLocaleString('vi-VN') + 'đ';
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        let shown = new Date(today.getFullYear(), today.getMonth(), 1);

        function isoDate(date) {
            return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        }

        function renderCalendar() {
            calendarMonth.textContent = new Intl.DateTimeFormat('vi-VN', {
                month: 'long', year: 'numeric'
            }).format(shown);
            days.replaceChildren();
            const offset = (shown.getDay() + 6) % 7;
            for (let empty = 0; empty < offset; empty++) {
                days.append(document.createElement('span'));
            }
            const count = new Date(shown.getFullYear(), shown.getMonth() + 1, 0).getDate();
            for (let day = 1; day <= count; day++) {
                const date = new Date(shown.getFullYear(), shown.getMonth(), day);
                const value = isoDate(date);
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = String(day);
                button.setAttribute('aria-label', `Chọn ngày ${day}/${shown.getMonth() + 1}`);
                button.disabled = date < today;
                button.classList.toggle('is-selected', dateField.value === value);
                button.addEventListener('click', () => {
                    dateField.value = value;
                    renderCalendar();
                    updateSlots();
                });
                days.append(button);
            }
            previous.disabled = shown.getFullYear() === today.getFullYear() &&
                shown.getMonth() === today.getMonth();
        }

        function updateSlots() {
            const currentLocalDate = isoDate(today);
            const isToday = dateField.value === currentLocalDate;
            document.querySelectorAll('.time-slots button[data-start]').forEach(button => {
                const [hour, minute] = button.dataset.start.split(':').map(Number);
                const now = new Date();
                button.disabled = !dateField.value ||
                    (isToday && (hour < now.getHours() ||
                        (hour === now.getHours() && minute <= now.getMinutes())));
                if (button.disabled && slotField.value === button.dataset.start) {
                    slotField.value = '';
                }
                button.classList.toggle('is-selected', slotField.value === button.dataset.start);
            });
        }

        previous.addEventListener('click', () => {
            shown = new Date(shown.getFullYear(), shown.getMonth() - 1, 1);
            renderCalendar();
        });
        next.addEventListener('click', () => {
            shown = new Date(shown.getFullYear(), shown.getMonth() + 1, 1);
            renderCalendar();
        });
        document.querySelectorAll('.time-slots button[data-start]').forEach(button => {
            button.addEventListener('click', () => {
                if (button.disabled) return;
                slotField.value = button.dataset.start;
                updateSlots();
            });
        });

        function updatePrice() {
            const base = rate * 2;
            const extra = premium?.checked ? base * .25 : 0;
            document.getElementById('booking-base').textContent = money(base);
            document.getElementById('booking-extra').textContent = money(extra);
            document.getElementById('booking-total').textContent = money(base + extra);
        }
        premium?.addEventListener('change', updatePrice);
        bookingForm.addEventListener('submit', event => {
            if (!dateField.value || !slotField.value) {
                event.preventDefault();
                alert('Vui lòng chọn ngày và khung giờ trước khi tiếp tục.');
            }
        });
        if (dateField.value) {
            const [year, month] = dateField.value.split('-').map(Number);
            shown = new Date(year, month - 1, 1);
        }
        renderCalendar();
        updateSlots();
        updatePrice();
    }

    const newPassword = document.getElementById('new-password');
    const confirmPassword = document.getElementById('confirm-password');
    const meter = document.querySelector('.password-meter');
    if (newPassword && confirmPassword && meter) {
        const label = document.getElementById('password-strength-label');
        const match = document.getElementById('password-match');

        function updatePasswordFeedback() {
            const value = newPassword.value;
            const score = value ? [
                value.length >= 8,
                value.length >= 12,
                /[A-Z]/.test(value) && /[a-z]/.test(value),
                /\d/.test(value) && /[^A-Za-z0-9]/.test(value)
            ].filter(Boolean).length : 0;
            const levels = ['Nhập mật khẩu', 'Yếu', 'Trung bình', 'Mạnh', 'Rất mạnh'];
            meter.dataset.score = String(score);
            label.textContent = levels[score];
            match.textContent = confirmPassword.value && confirmPassword.value === value ? '✓' : '';
            match.setAttribute('aria-label', match.textContent ? 'Mật khẩu xác nhận khớp' : '');
        }
        newPassword.addEventListener('input', updatePasswordFeedback);
        confirmPassword.addEventListener('input', updatePasswordFeedback);
        updatePasswordFeedback();

        const session = document.getElementById('current-session-agent');
        if (session) {
            const browser = /Edg\//.test(navigator.userAgent) ? 'Edge' :
                /Chrome\//.test(navigator.userAgent) ? 'Chrome' :
                    /Firefox\//.test(navigator.userAgent) ? 'Firefox' : 'Trình duyệt';
            session.textContent = `${browser} · Phiên đang sử dụng (chỉ hiển thị trên thiết bị này)`;
        }
    }
})();
```

---

## `src/main/resources/templates/auth/change-password.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Đổi mật khẩu · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="management-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <main class="page">
   <div class="container layout management-layout">
    <div th:replace="~{fragments :: sidebar}">
    </div>
    <section class="page-content">
     <h1 class="title">
      Đổi mật khẩu
     </h1>
     <p class="muted">
      Cập nhật mật khẩu để bảo vệ tài khoản.
     </p>
     <div th:replace="~{fragments :: flash}">
     </div>
     <div class="password-column">
      <form class="card" id="password-form" method="post" th:action="@{/change-password}" th:object="${form}">
       <div class="alert danger" th:each="e : ${#fields.globalErrors()}" th:if="${#fields.hasGlobalErrors()}" th:text="${e}">
       </div>
       <div class="form-group">
        <label for="old-password">
         Mật khẩu hiện tại
        </label>
        <div class="password-wrap">
         <svg class="icon input-lock">
          <use href="#i-lock">
          </use>
         </svg>
         <input autocomplete="current-password" class="input password-input" id="old-password" required="" th:field="*{currentPassword}" type="password"/>
         <button aria-label="Hiện mật khẩu" class="password-toggle" data-toggle-password="" type="button">
          <svg class="icon">
           <use href="#i-eye">
           </use>
          </svg>
         </button>
        </div>
       </div>
       <div class="form-group">
        <label for="new-password">
         Mật khẩu mới
        </label>
        <div class="password-wrap">
         <svg class="icon input-lock">
          <use href="#i-lock">
          </use>
         </svg>
         <input autocomplete="new-password" class="input password-input" id="new-password" minlength="6" required="" th:field="*{newPassword}" type="password"/>
         <button aria-label="Hiện mật khẩu" class="password-toggle" data-toggle-password="" type="button">
          <svg class="icon">
           <use href="#i-eye">
           </use>
          </svg>
         </button>
        </div>
        <div class="password-meter">
         <div class="meter-bars">
          <span>
          </span>
          <span>
          </span>
          <span>
          </span>
          <span>
          </span>
         </div>
         <small id="password-strength-label">
          Nhập mật khẩu để kiểm tra độ mạnh
         </small>
        </div>
       </div>
       <div class="form-group">
        <label for="confirm-password">
         Xác nhận mật khẩu mới
        </label>
        <div class="password-wrap">
         <svg class="icon input-lock">
          <use href="#i-lock">
          </use>
         </svg>
         <input autocomplete="new-password" class="input password-input" id="confirm-password" required="" th:field="*{confirmPassword}" type="password"/>
         <button aria-label="Hiện mật khẩu" class="password-toggle" data-toggle-password="" type="button">
          <svg class="icon">
           <use href="#i-eye">
           </use>
          </svg>
         </button>
         <span aria-live="polite" class="password-match" id="password-match">
         </span>
        </div>
       </div>
       <button class="btn btn-primary" style="width:100%" type="submit">
        Cập nhật mật khẩu
       </button>
      </form>
      <section class="card advanced-security">
       <h2>
        Bảo mật nâng cao
       </h2>
       <div class="security-setting">
        <div>
         <strong>
          Xác thực hai yếu tố (2FA)
         </strong>
         <p class="muted">
          Tính năng 2FA chưa được tích hợp. Nút bật sẽ chỉ lưu tùy chọn thử nghiệm, không bổ sung bước OTP vào đăng nhập.
         </p>
        </div>
        <form method="post" th:action="@{/settings/2fa-preference}">
         <label class="toggle-switch" for="two-factor">
          <input id="two-factor" name="enabled" onchange="this.form.requestSubmit()" th:checked="${currentUser.twoFactorPreference}" type="checkbox" value="true"/>
          <span>
          </span>
         </label>
        </form>
       </div>
       <hr/>
       <h3>
        Phiên đăng nhập hiện tại
       </h3>
       <p class="muted" id="current-session-agent">
        Trình duyệt hiện tại · thiết bị của bạn
       </p>
       <button class="btn btn-secondary" disabled="" title="Chưa có cơ chế quản lý nhiều phiên đăng nhập" type="button">
        Đăng xuất tất cả phiên khác (đang phát triển)
       </button>
      </section>
     </div>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/auth/login.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Đăng nhập · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
 </head>
 <body class="login-v2">
  <div class="login-split">
   <section class="login-story">
    <a class="auth-brand" th:href="@{/marketplace}">
     <span class="logo-mark">
      S
     </span>
     <span>
      Skill Rental
     </span>
    </a>
    <div class="login-story-body">
     <h2>
      Thuê đúng kỹ năng.
      <br/>
      Hoàn thành đúng việc.
     </h2>
     <p>
      Kết nối khách hàng với Provider phù hợp trên một nền tảng thống nhất.
     </p>
     <div aria-hidden="true" class="login-decoration">
      <span class="deco-cube">
      </span>
      <span class="deco-orbit">
      </span>
     </div>
     <div class="login-expert">
      <img alt="" src="https://i.pravatar.cc/100?img=11"/>
      <span>
       Nguyễn Văn Minh
       <br/>
       <small>
        <span class="rating-star">
         ★
        </span>
        4.9 · Provider demo
       </small>
      </span>
     </div>
     <div class="login-stat">
      <strong>
       10,000+
      </strong>
      <span>
       Chuyên gia · Minh họa thiết kế
      </span>
     </div>
    </div>
    <div class="login-copyright">
     © 2026 Skill Rental. Nền tảng kết nối kỹ năng.
    </div>
   </section>
   <main class="login-panel">
    <section class="login-form-card">
     <h1>
      Đăng nhập vào Skill Rental
     </h1>
     <p class="muted">
      Vui lòng đăng nhập để sử dụng dịch vụ
     </p>
     <div class="alert danger" th:if="${param.error}">
      Email hoặc mật khẩu không đúng.
     </div>
     <div class="alert success" th:if="${param.logout}">
      Bạn đã đăng xuất.
     </div>
     <div th:replace="~{fragments :: flash}">
     </div>
     <form method="post" th:action="@{/login}">
      <div class="form-group">
       <label for="email">
        Email
       </label>
       <input autocomplete="username" class="input" id="email" name="username" placeholder="Nhập email của bạn" required="" type="email"/>
      </div>
      <div class="form-group">
       <div class="label-between">
        <label for="password">
         Mật khẩu
        </label>
        <a th:href="@{/forgot-password}">
         Quên mật khẩu?
        </a>
       </div>
       <input autocomplete="current-password" class="input" id="password" name="password" placeholder="Nhập mật khẩu" required="" type="password"/>
      </div>
      <label class="checkbox-label">
       <input name="remember-me" type="checkbox" value="on"/>
       Ghi nhớ đăng nhập
      </label>
      <button class="btn btn-primary login-submit" type="submit">
       Đăng nhập ngay
      </button>
     </form>
     <div class="login-divider">
      <span>
       Hoặc đăng nhập với
      </span>
     </div>
     <div class="login-socials">
      <button disabled="" title="Chưa tích hợp Google OAuth" type="button">
       G
      </button>
      <button disabled="" title="Chưa tích hợp Facebook OAuth" type="button">
       f
      </button>
      <button disabled="" title="Chưa tích hợp LinkedIn OAuth" type="button">
       in
      </button>
     </div>
     <p class="login-signup">
      Chưa có tài khoản?
      <a th:href="@{/register}">
       Đăng ký miễn phí
      </a>
     </p>
    </section>
   </main>
  </div>
 </body>
</html>
```

---

## `src/main/resources/templates/booking/booking.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Đặt Provider · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="public-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <div aria-label="Các bước đặt lịch" class="booking-stepper">
     <div class="is-current">
      <b>
       1
      </b>
      <span>
       Chọn lịch
      </span>
     </div>
     <span>
      →
     </span>
     <div>
      <b>
       2
      </b>
      <span>
       Thanh toán
      </span>
     </div>
     <span>
      →
     </span>
     <div>
      <b>
       3
      </b>
      <span>
       Hoàn tất
      </span>
     </div>
    </div>
    <h1 class="title booking-title">
     Đặt Provider
    </h1>
    <div class="booking-layout">
     <form class="card booking-form" id="booking-form" method="post" th:action="@{/booking/provider/{id}(id=${provider.id})}" th:object="${form}">
      <input th:field="*{directBooking}" type="hidden"/>
      <input th:field="*{durationHours}" type="hidden" value="2"/>
      <div class="form-group">
       <label for="service">
        Dịch vụ
       </label>
       <select class="select" id="service" required="" th:field="*{serviceName}">
        <option value="">
         -- Chọn dịch vụ --
        </option>
        <option th:text="${'Tư vấn '+provider.headline}" th:value="${'Tư vấn '+provider.headline}">
        </option>
        <option th:text="${'Hỗ trợ thực hiện '+provider.headline}" th:value="${'Hỗ trợ thực hiện '+provider.headline}">
        </option>
       </select>
       <small class="error" th:errors="*{serviceName}">
       </small>
      </div>
      <div class="form-group">
       <label>
        Ngày làm việc
       </label>
       <p class="muted">
        Chọn ngày mong muốn (chưa kiểm tra tình trạng trống theo thời gian thực).
       </p>
       <input id="booking-date" required="" th:field="*{bookingDate}" type="hidden"/>
       <div aria-label="Lịch chọn ngày" class="date-picker" id="booking-calendar" role="group">
        <div class="calendar-top">
         <button aria-label="Tháng trước" id="calendar-prev" type="button">
          ‹
         </button>
         <strong id="calendar-month">
         </strong>
         <button aria-label="Tháng sau" id="calendar-next" type="button">
          ›
         </button>
        </div>
        <div class="calendar-weekdays">
         <span>
          T2
         </span>
         <span>
          T3
         </span>
         <span>
          T4
         </span>
         <span>
          T5
         </span>
         <span>
          T6
         </span>
         <span>
          T7
         </span>
         <span>
          CN
         </span>
        </div>
        <div class="calendar-days" id="calendar-days">
        </div>
       </div>
       <small class="error" th:errors="*{bookingDate}">
       </small>
      </div>
      <div class="form-group">
       <label>
        Khung giờ (2 giờ)
       </label>
       <input id="booking-time" required="" th:field="*{timeSlot}" type="hidden"/>
       <div aria-label="Chọn khung giờ" class="time-slots" role="group">
        <button data-start="10:00" type="button">
         10:00 – 12:00
        </button>
        <button data-start="12:00" type="button">
         12:00 – 14:00
        </button>
        <button data-start="18:00" type="button">
         18:00 – 20:00
        </button>
        <button data-start="20:00" type="button">
         20:00 – 22:00
        </button>
       </div>
       <small class="error" th:errors="*{timeSlot}">
       </small>
      </div>
      <div class="form-group">
       <label for="booking-note">
        Ghi chú
       </label>
       <textarea class="textarea" id="booking-note" placeholder="Mô tả nhu cầu của bạn…" th:field="*{note}"></textarea>
      </div>
      <label class="checkbox-label">
       <input id="booking-premium" th:field="*{premium}" type="checkbox"/>
       Đặt kỹ năng cao (+25%)
      </label>
      <button class="btn btn-primary booking-submit" type="submit">
       Tiếp tục thanh toán →
      </button>
     </form>
     <aside class="card booking-summary" th:attr="data-rate=${provider.hourlyRate}">
      <h2>
       Tóm tắt đơn hàng
      </h2>
      <div class="provider">
       <img alt="Ảnh Provider" th:src="${provider.user.avatarUrl}"/>
       <div>
        <strong th:text="${provider.user.fullName}">
        </strong>
        <p th:text="${provider.headline}">
        </p>
       </div>
      </div>
      <h3>
       Chi tiết chi phí
      </h3>
      <div class="price-line">
       <span>
        Dịch vụ cơ bản (2 giờ)
       </span>
       <strong id="booking-base">
       </strong>
      </div>
      <div class="price-line">
       <span>
        Phí kỹ năng cao (+25%)
       </span>
       <strong id="booking-extra">
        0đ
       </strong>
      </div>
      <hr/>
      <div class="price-line total">
       <span>
        Tổng cộng
       </span>
       <strong id="booking-total">
       </strong>
      </div>
      <p class="booking-demo-notice">
       Thanh toán ở phiên bản demo, chưa có dịch vụ giữ tiền trung gian thực tế.
      </p>
     </aside>
    </div>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/booking/complete.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Hoàn tất đặt lịch · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="public-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <div class="booking-stepper">
     <div class="is-done">
      <b>
       ✓
      </b>
      <span>
       Chọn lịch
      </span>
     </div>
     <span>
      →
     </span>
     <div class="is-done">
      <b>
       ✓
      </b>
      <span>
       Thanh toán
      </span>
     </div>
     <span>
      →
     </span>
     <div class="is-current">
      <b>
       3
      </b>
      <span>
       Hoàn tất
      </span>
     </div>
    </div>
    <section class="card empty-state">
     <h1>
      Đã ghi nhận đặt lịch
     </h1>
     <p class="muted" th:text="${'Mã đơn: #SR-' + order.id}">
     </p>
     <p th:text="${order.booking.serviceName}">
     </p>
     <p class="alert success">
      Đã ghi nhận bước thanh toán mô phỏng; chưa có giao dịch tiền thật.
     </p>
     <a class="btn btn-primary" th:href="@{/orders}">
      Xem đơn hàng của tôi
     </a>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/booking/payment.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width,initial-scale=1" name="viewport"/>
  <title>
   Thanh toán · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link href="/css/app.css" rel="stylesheet"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body>
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <div aria-label="Các bước đặt lịch" class="booking-stepper">
     <div class="is-done">
      <b>
       ✓
      </b>
      <span>
       Chọn lịch
      </span>
     </div>
     <span>
      →
     </span>
     <div class="is-current">
      <b>
       2
      </b>
      <span>
       Thanh toán
      </span>
     </div>
     <span>
      →
     </span>
     <div>
      <b>
       3
      </b>
      <span>
       Hoàn tất
      </span>
     </div>
    </div>
    <p class="alert success">
     Thanh toán trong project hiện chỉ là mô phỏng: không chuyển tiền, không giữ tiền trung gian.
    </p>
    <h1 class="title">
     Thanh toán
    </h1>
    <div class="grid grid2" style="margin-top:20px">
     <form class="card" method="post" th:action="@{/payment/{id}(id=${order.id})}" th:object="${form}">
      <div class="alert danger" th:each="e:${#fields.globalErrors()}" th:if="${#fields.hasGlobalErrors()}" th:text="${e}">
      </div>
      <div class="form-group">
       <label>
        Phương thức
       </label>
       <select class="select" th:field="*{method}">
        <option value="">
         -- Chọn --
        </option>
        <option value="MOMO">
         MoMo
        </option>
        <option value="VNPAY">
         VNPay
        </option>
        <option value="BANK">
         Chuyển khoản
        </option>
        <option value="COD">
         Thanh toán sau
        </option>
       </select>
      </div>
      <div class="form-group">
       <label>
        Coupon
       </label>
       <input class="input" placeholder="WELCOME10 / SKILL20" th:field="*{couponCode}"/>
      </div>
      <label>
       <input th:field="*{acceptedTerms}" type="checkbox"/>
       Tôi đồng ý điều khoản
      </label>
      <button class="btn btn-primary" style="width:100%;margin-top:14px">
       Xác nhận thanh toán
      </button>
     </form>
     <div class="card">
      <h3>
       Tóm tắt đơn hàng · Thanh toán demo
      </h3>
      <p th:text="${order.booking.serviceName}">
      </p>
      <p class="muted" th:text="${order.provider.fullName}">
      </p>
      <p class="price" th:text="${#numbers.formatDecimal(order.subtotal,0,'POINT',0,'COMMA')+'đ'}">
      </p>
     </div>
    </div>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" src="/js/app.js">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/customer/coming-soon.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Đang phát triển · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="management-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <main class="page">
   <div class="container layout management-layout">
    <div th:replace="~{fragments :: sidebar}">
    </div>
    <section class="page-content">
     <div class="card empty-state">
      <h1 th:text="${feature}">
       Tính năng
      </h1>
      <p class="muted">
       Tính năng này đang được phát triển; hiện chưa có nghiệp vụ backend để thực hiện giao dịch hay xác thực.
      </p>
      <a class="btn btn-primary" th:href="@{/settings}">
       Quay lại cài đặt
      </a>
     </div>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/customer/settings.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Cài đặt tài khoản · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="management-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <main class="page">
   <div class="container layout management-layout">
    <div th:replace="~{fragments :: sidebar}">
    </div>
    <section class="page-content">
     <section class="profile-meter card">
      <div class="section-top">
       <div>
        <strong>
         Hoàn thiện hồ sơ
        </strong>
        <p th:text="${'Hồ sơ của bạn đã hoàn thành '+completion+'%. Hãy cập nhật để có nhiều cơ hội hơn!'}">
        </p>
       </div>
       <strong class="price" th:text="${completion+'%'}">
       </strong>
      </div>
      <div class="rating-track profile-track">
       <span th:style="|width:${completion}%|">
       </span>
      </div>
     </section>
     <h1 class="title settings-title">
      Cài đặt tài khoản
     </h1>
     <p class="muted">
      Quản lý thông tin đăng nhập và hồ sơ.
     </p>
     <div class="settings-grid">
      <a class="card settings-tile" th:href="@{/profile}">
       <span class="settings-icon">
        ♙
       </span>
       <strong>
        Trang cá nhân
       </strong>
       <p>
        Họ tên, email, điện thoại, avatar và giới thiệu.
       </p>
      </a>
      <a class="card settings-tile" th:href="@{/change-password}">
       <span class="settings-icon">
        ♙
       </span>
       <strong>
        Bảo mật
       </strong>
       <p>
        Đổi mật khẩu đăng nhập và xem thiết lập bảo mật.
       </p>
      </a>
      <a class="card settings-tile" th:href="@{/settings/coming-soon(feature='Ví và bảng kê')}">
       <span class="settings-icon">
        ▣
       </span>
       <strong>
        Ví & Bảng kê
       </strong>
       <p>
        Quản lý giao dịch và lịch sử thanh toán (đang phát triển).
       </p>
      </a>
      <a class="card settings-tile" th:href="@{/settings/coming-soon(feature='Thông báo')}">
       <span class="settings-icon">
        ♧
       </span>
       <strong>
        Thông báo
       </strong>
       <p>
        Chọn thông báo qua email và trên ứng dụng (đang phát triển).
       </p>
      </a>
      <a class="card settings-tile" th:href="@{/settings/coming-soon(feature='Xác thực KYC')}">
       <span class="settings-icon">
        ✓
       </span>
       <strong>
        Xác thực KYC
       </strong>
       <p>
        Cung cấp thông tin để xác thực danh tính (đang phát triển).
       </p>
      </a>
      <a class="card settings-tile" th:href="@{/settings/coming-soon(feature='Ngôn ngữ')}">
       <span class="settings-icon">
        ◎
       </span>
       <strong>
        Ngôn ngữ
       </strong>
       <p>
        Giao diện hiện sử dụng Tiếng Việt. Đang phát triển thêm ngôn ngữ.
       </p>
      </a>
     </div>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/fragments.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <body>
  <svg aria-hidden="true" class="icon-sprite" th:fragment="icons" xmlns="http://www.w3.org/2000/svg">
   <symbol id="i-home" viewbox="0 0 24 24">
    <path d="m3 10 9-7 9 7v10H3z">
    </path>
    <path d="M9 20v-7h6v7">
    </path>
   </symbol>
   <symbol id="i-search" viewbox="0 0 24 24">
    <circle cx="10.5" cy="10.5" r="6.5">
    </circle>
    <path d="m16 16 5 5">
    </path>
   </symbol>
   <symbol id="i-user" viewbox="0 0 24 24">
    <circle cx="12" cy="7.5" r="4">
    </circle>
    <path d="M4 21c0-5 3-8 8-8s8 3 8 8">
    </path>
   </symbol>
   <symbol id="i-list" viewbox="0 0 24 24">
    <path d="M8 5h13M8 12h13M8 19h13M3 5h1M3 12h1M3 19h1">
    </path>
   </symbol>
   <symbol id="i-bag" viewbox="0 0 24 24">
    <rect height="15" rx="2" width="18" x="3" y="6">
    </rect>
    <path d="M8 6V4a4 4 0 0 1 8 0v2">
    </path>
   </symbol>
   <symbol id="i-chat" viewbox="0 0 24 24">
    <path d="M20 16a3 3 0 0 1-3 3H8l-5 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3z">
    </path>
   </symbol>
   <symbol id="i-lock" viewbox="0 0 24 24">
    <rect height="11" rx="2" width="16" x="4" y="10">
    </rect>
    <path d="M8 10V7a4 4 0 0 1 8 0v3">
    </path>
   </symbol>
   <symbol id="i-settings" viewbox="0 0 24 24">
    <circle cx="12" cy="12" r="3">
    </circle>
    <path d="M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M19 5l-2 2M7 17l-2 2">
    </path>
   </symbol>
   <symbol id="i-bell" viewbox="0 0 24 24">
    <path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 9h18c0-1-3-2-3-9M10 21h4">
    </path>
   </symbol>
   <symbol id="i-eye" viewbox="0 0 24 24">
    <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z">
    </path>
    <circle cx="12" cy="12" r="3">
    </circle>
   </symbol>
   <symbol id="i-check" viewbox="0 0 24 24">
    <path d="M4 12l5 5L20 6">
    </path>
   </symbol>
   <symbol id="i-alert" viewbox="0 0 24 24">
    <path d="M12 3 2 21h20L12 3Z">
    </path>
    <path d="M12 9v5m0 3v1">
    </path>
   </symbol>
   <symbol id="i-logout" viewbox="0 0 24 24">
    <path d="M9 4H4v16h5m4-12 4 4-4 4m-8-4h12">
    </path>
   </symbol>
   <symbol id="i-x" viewbox="0 0 24 24">
    <path d="M5 5l14 14M19 5 5 19">
    </path>
   </symbol>
  </svg>
  <header class="header" th:fragment="header">
   <div class="container header-inner">
    <a aria-label="Skill Rental - Trang chủ" class="logo" th:href="@{/marketplace}">
     <span class="logo-mark">
      S
     </span>
     <span>
      SKILL RENTAL
     </span>
    </a>
    <nav aria-label="Điều hướng chính" class="nav">
     <a class="nav-item" th:href="@{/marketplace}">
      <svg class="icon">
       <use href="#i-home">
       </use>
      </svg>
      <span>
       Trang chủ
      </span>
     </a>
     <a class="nav-item" th:href="@{/providers}">
      <svg class="icon">
       <use href="#i-search">
       </use>
      </svg>
      <span>
       Tìm Provider
      </span>
     </a>
     <a class="nav-item" th:href="@{/requests}">
      <svg class="icon">
       <use href="#i-list">
       </use>
      </svg>
      <span>
       Request của tôi
      </span>
     </a>
     <a class="nav-item" th:href="@{/orders}">
      <svg class="icon">
       <use href="#i-bag">
       </use>
      </svg>
      <span>
       Đơn hàng
      </span>
     </a>
     <button class="nav-item nav-button" data-open-chat="" type="button">
      <svg class="icon">
       <use href="#i-chat">
       </use>
      </svg>
      <span>
       Tin nhắn
      </span>
     </button>
    </nav>
    <form class="header-quick-search" th:action="@{/providers}" method="get" role="search"><label class="sr-only" for="header-search">Tìm kiếm nhanh</label><input id="header-search" name="q" placeholder="Tìm chuyên gia…" aria-label="Tìm kiếm nhanh"><button type="submit" aria-label="Tìm">⌕</button></form>
    <div class="header-actions" th:if="${currentUser!=null}">
     <details class="menu-wrap">
      <summary aria-label="Thông báo" class="icon-button">
       <svg class="icon">
        <use href="#i-bell">
        </use>
       </svg>
      </summary>
      <div class="menu-panel">
       <p class="muted">
        Chưa có thông báo mới.
       </p>
      </div>
     </details>
     <details class="menu-wrap">
      <summary aria-label="Mở menu tài khoản" class="avatar-button">
       <img alt="Ảnh đại diện" class="avatar" th:src="${currentUser.avatarUrl}"/>
       <span class="username" th:text="${currentUser.fullName}">
        Tài khoản
       </span>
      </summary>
      <div class="menu-panel menu-account">
       <a th:href="@{/profile}">
        <svg class="icon">
         <use href="#i-user">
         </use>
        </svg>
        Xem trang cá nhân
       </a>
       <a th:href="@{/settings}">
        <svg class="icon">
         <use href="#i-settings">
         </use>
        </svg>
        Cài đặt
       </a>
       <a th:href="@{/change-password}">
        <svg class="icon">
         <use href="#i-lock">
         </use>
        </svg>
        Đổi mật khẩu
       </a>
       <form method="post" th:action="@{/logout}">
        <button class="menu-logout" type="submit">
         <svg class="icon">
          <use href="#i-logout">
          </use>
         </svg>
         Đăng xuất
        </button>
       </form>
      </div>
     </details>
    </div>
   </div>
  </header>
  <aside aria-label="Điều hướng quản lý" class="sidebar" th:fragment="sidebar">
   <a class="logo sidebar-logo" th:href="@{/marketplace}">
    <span class="logo-mark">
     S
    </span>
    <span>
     SKILL RENTAL
    </span>
   </a>
   <p class="sidebar-caption">
    QUẢN LÝ
   </p>
   <a class="side-item" th:href="@{/marketplace}">
    <svg class="icon">
     <use href="#i-home">
     </use>
    </svg>
    <span>
     Trang chủ
    </span>
   </a>
   <a class="side-item" th:href="@{/providers}">
    <svg class="icon">
     <use href="#i-search">
     </use>
    </svg>
    <span>
     Tìm Provider
    </span>
   </a>
   <a class="side-item" th:href="@{/profile}">
    <svg class="icon">
     <use href="#i-user">
     </use>
    </svg>
    <span>
     Hồ sơ
    </span>
   </a>
   <a class="side-item" th:href="@{/requests}">
    <svg class="icon">
     <use href="#i-list">
     </use>
    </svg>
    <span>
     Request của tôi
    </span>
   </a>
   <a class="side-item" th:href="@{/orders}">
    <svg class="icon">
     <use href="#i-bag">
     </use>
    </svg>
    <span>
     Đơn hàng
    </span>
   </a>
   <button class="side-item side-button" data-open-chat="" type="button">
    <svg class="icon">
     <use href="#i-chat">
     </use>
    </svg>
    <span>
     Tin nhắn
    </span>
   </button>
   <a class="side-item" th:href="@{/settings}">
    <svg class="icon">
     <use href="#i-settings">
     </use>
    </svg>
    <span>
     Cài đặt
    </span>
   </a>
   <a class="side-item" th:href="@{/change-password}">
    <svg class="icon">
     <use href="#i-lock">
     </use>
    </svg>
    <span>
     Đổi mật khẩu
    </span>
   </a>
   <form method="post" th:action="@{/logout}">
    <button class="side-item side-button" type="submit">
     <svg class="icon">
      <use href="#i-logout">
      </use>
     </svg>
     <span>
      Đăng xuất
     </span>
    </button>
   </form>
  </aside>
  <div th:fragment="flash">
   <div class="alert success" th:if="${success}" th:text="${success}">
   </div>
   <div class="alert danger" th:if="${error}" th:text="${error}">
   </div>
  </div>
  <footer class="footer" th:fragment="footer">
   <div class="container footer-inner">
    <span>
     © 2026 Skill Rental · Nền tảng kết nối kỹ năng
    </span>
    <a th:href="@{/marketplace}">
     Về trang chủ
    </a>
   </div>
  </footer>
  <div th:fragment="floatingChat" th:if="${currentUser!=null}" th:replace="~{interaction/chat :: widget}">
  </div>
 </body>
</html>
```

---

## `src/main/resources/templates/home.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Trang chủ · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="public-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <section class="market-hero">
     <div class="market-hero-copy">
      <span class="eyebrow">
       NỀN TẢNG KẾT NỐI KỸ NĂNG
      </span>
      <h1>
       Tìm đúng chuyên gia cho công việc của bạn.
      </h1>
      <p>
       Khám phá Provider theo kỹ năng, giá và đánh giá; hoặc tạo Request để nhận hồ sơ ứng tuyển.
      </p>
      <div class="actions">
       <a class="btn btn-outline" href="/providers">
        Tìm Provider
       </a>
       <a class="btn btn-primary" href="/requests/create">
        + Tạo Request
       </a>
      </div>
     </div>
     <div aria-hidden="true" class="hero-polygon">
     </div>
    </section>
    <form class="hero-search" method="get" th:action="@{/providers}">
     <label for="home-search">
      Tìm kiếm Provider và kỹ năng
     </label>
     <div class="search-field">
      <input class="input" id="home-search" name="q" placeholder="Tên hoặc chuyên môn…"/>
      <button aria-label="Tìm kiếm" class="btn btn-primary" type="submit">
       ⌕
      </button>
     </div>
    </form>
    <div aria-label="Kỹ năng gợi ý" class="skill-chips">
     <a class="skill-chip" th:each="s : ${skills}" th:href="@{/providers(skill=${s.name})}" th:text="${s.name}">
     </a>
    </div>
    <div class="section-top">
     <div>
      <h2>
       Provider nổi bật
      </h2>
      <p class="muted">
       Khám phá những chuyên gia đang có trên nền tảng.
      </p>
     </div>
     <a class="more-link" th:href="@{/providers}">
      Xem tất cả →
     </a>
    </div>
    <div class="expert-grid">
     <article class="card expert-card" th:each="p : ${providers}">
      <div class="expert-person">
       <img alt="Ảnh Provider" th:src="${p.user.avatarUrl}"/>
       <div>
        <h3 th:text="${p.user.fullName}">
         Provider
        </h3>
        <small class="verified">
         <span aria-hidden="true">
          ●
         </span>
         Đã xác minh (demo)
        </small>
        <div class="expert-rating">
         <span class="rating-star">
          ★
         </span>
         <strong th:text="${p.rating}">
          4.9
         </strong>
         <span th:text="${'· '+p.reviewCount+' đánh giá'}">
         </span>
        </div>
       </div>
      </div>
      <p class="expert-headline" th:text="${p.headline}">
      </p>
      <div aria-label="Ảnh portfolio minh họa" class="expert-gallery">
       <span class="portfolio-thumb pt-1">
        UI
       </span>
       <span class="portfolio-thumb pt-2">
        API
       </span>
       <span class="portfolio-thumb pt-3">
        WEB
       </span>
      </div>
      <div class="tags expert-skills">
       <span class="tag" th:each="s : ${p.skills}" th:text="${s.name}">
       </span>
      </div>
      <p class="expert-hours">
       ◷
       <span th:text="${p.availability}">
       </span>
      </p>
      <div class="expert-price">
       Từ
       <strong th:text="${#numbers.formatDecimal(p.hourlyRate,0,'POINT',0,'COMMA')+'đ'}">
       </strong>
       / giờ
      </div>
      <div class="actions expert-actions">
       <a class="btn btn-outline btn-sm" th:href="@{/providers/{id}(id=${p.id})}">
        Xem hồ sơ
       </a>
       <a class="btn btn-primary btn-sm" th:href="@{/booking/provider/{id}(id=${p.id})}">
        Đặt Provider
       </a>
      </div>
     </article>
    </div>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/marketplace/provider-detail.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Chi tiết Provider · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="public-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <div class="provider-detail-layout">
     <div class="detail-main">
      <section class="card expert-overview">
       <div class="provider expert-profile">
        <img alt="Ảnh Provider" th:src="${provider.user.avatarUrl}"/>
        <div>
         <h1 class="title" th:text="${provider.user.fullName}">
         </h1>
         <p class="expert-rating">
          <span class="rating-star">
           ★
          </span>
          <strong th:text="${provider.rating}">
          </strong>
          ·
          <span th:text="${provider.reviewCount+' đánh giá (thống kê hồ sơ)'}">
          </span>
         </p>
         <strong th:text="${provider.headline}">
         </strong>
        </div>
       </div>
       <hr/>
       <h2>
        Giới thiệu
       </h2>
       <p class="bio" th:text="${provider.introduction}">
       </p>
       <h2>
        Kỹ năng
       </h2>
       <div class="tags colorful-skills">
        <span class="tag" th:classappend="${'skill-tint-'+(st.index%4)}" th:each="skill,st : ${provider.skills}" th:text="${skill.name}">
        </span>
       </div>
      </section>
      <section class="card">
       <h2>
        Portfolio dự án
       </h2>
       <p class="muted">
        Hình và tên dự án minh họa; hồ sơ chưa có dữ liệu portfolio được xác thực.
       </p>
       <div class="portfolio-grid">
        <div class="portfolio-project" th:each="name,st : ${portfolioNames}">
         <div class="portfolio-project-art" th:classappend="${'pt-'+((st.index%3)+1)}">
          <span th:text="${'0'+(st.index+1)}">
          </span>
         </div>
         <strong th:text="${name}">
         </strong>
        </div>
       </div>
      </section>
      <section class="card">
       <h2>
        Đánh giá khách hàng
       </h2>
       <div class="review-overview">
        <div class="review-score">
         <strong th:text="${provider.rating}">
         </strong>
         <span class="rating-star">
          ★★★★★
         </span>
         <small th:text="${provider.reviewCount+' đánh giá trên hồ sơ'}">
         </small>
        </div>
        <div class="rating-bars">
         <div class="rating-bar-row" th:each="n : ${#numbers.sequence(5,1)}">
          <span th:text="${n+' ★'}">
          </span>
          <div class="rating-track">
           <span th:style="|width: ${ratingPercent[n-1]}%|">
           </span>
          </div>
          <small th:text="${ratingCounts[n-1]}">
          </small>
         </div>
        </div>
       </div>
       <p class="muted" th:if="${#lists.isEmpty(reviews)}">
        Chưa có đánh giá chi tiết từ các đơn hàng trong hệ thống. Phân bố sao chỉ thống kê các đánh giá đã gửi trên website.
       </p>
       <div class="review-quote" th:each="r : ${reviews}">
        <img alt="Avatar khách hàng" th:src="${r.customer.avatarUrl}"/>
        <div>
         <strong th:text="${r.customer.fullName}">
         </strong>
         <div class="rating-star" th:text="${r.rating+' / 5 sao'}">
         </div>
         <p th:text="${r.comment}">
         </p>
        </div>
       </div>
      </section>
     </div>
     <aside class="card detail-price-card">
      <p class="muted">
       Giá / giờ
      </p>
      <div class="detail-price">
       <strong th:text="${#numbers.formatDecimal(provider.hourlyRate,0,'POINT',0,'COMMA')+'đ'}">
       </strong>
       <span>
        / giờ
       </span>
      </div>
      <p class="muted">
       Thời gian:
       <span th:text="${provider.availability}">
       </span>
      </p>
      <a class="btn btn-primary" th:href="@{/booking/provider/{id}(id=${provider.id})}">
       Đặt lịch ngay
      </a>
      <form method="post" th:action="@{/chat/start/{id}(id=${provider.id})}">
       <button class="btn btn-outline" type="submit">
        Nhắn tin
       </button>
      </form>
      <small class="muted">
       Lịch sẽ được xác nhận ở bước đặt Provider. Chức năng khóa học chưa được triển khai.
      </small>
     </aside>
    </div>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/marketplace/search.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width,initial-scale=1" name="viewport"/>
  <title>
   Tìm Provider · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link href="/css/app.css" rel="stylesheet"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body>
  <div th:replace="~{fragments :: icons}">
  </div>
  <div th:replace="~{fragments :: header}">
  </div>
  <main class="page">
   <div class="container public-content">
    <div class="section-top">
     <div>
      <h1 class="title">
       Tìm Provider
      </h1>
      <p class="muted">
       Lọc theo kỹ năng và khoảng giá phù hợp.
      </p>
     </div>
     <a class="btn btn-primary" href="/requests/create">
      + Tạo Request
     </a>
    </div>
    <form class="card filter-card" method="get" th:action="@{/providers}">
     <div class="filter-grid">
      <div class="form-group">
       <label for="provider-q">
        Từ khóa
       </label>
       <input class="input" id="provider-q" name="q" placeholder="Tên hoặc chuyên môn" th:value="${q}"/>
      </div>
      <div class="form-group">
       <label for="skill-filter">
        Tìm trong danh sách kỹ năng
       </label>
       <input aria-controls="skill-select" autocomplete="off" class="input" id="skill-filter" placeholder="Gõ để lọc lựa chọn" type="search"/>
       <select aria-label="Chọn kỹ năng" class="select" id="skill-select" name="skill">
        <option value="">
         Tất cả kỹ năng
        </option>
        <option th:each="s:${skills}" th:selected="${skill==s.name}" th:text="${s.name}" th:value="${s.name}">
         Kỹ năng
        </option>
       </select>
       <small class="muted">
        Chỉ chọn kỹ năng có trong danh sách.
       </small>
      </div>
      <div class="form-group">
       <label for="sort-providers">
        Sắp xếp
       </label>
       <select class="select" id="sort-providers" name="sort">
        <option th:selected="${sort=='rating'}" value="rating">
         Đánh giá cao
        </option>
        <option th:selected="${sort=='priceAsc'}" value="priceAsc">
         Giá tăng dần
        </option>
        <option th:selected="${sort=='priceDesc'}" value="priceDesc">
         Giá giảm dần
        </option>
       </select>
      </div>
     </div>
     <fieldset class="price-field">
      <legend>
       Khoảng giá / giờ
      </legend>
      <div class="price-current">
       <output for="min-price" id="price-min-label">
        0đ
       </output>
       <span>
        —
       </span>
       <output for="max-price" id="price-max-label">
        2.000.000đ
       </output>
      </div>
      <div class="range-dual" id="range-dual">
       <div class="range-track">
       </div>
       <div class="range-fill" id="range-fill">
       </div>
       <input aria-label="Giá thấp nhất" id="min-price" max="2000000" min="0" name="minPrice" step="10000" th:value="${minPrice}" type="range"/>
       <input aria-label="Giá cao nhất" id="max-price" max="2000000" min="0" name="maxPrice" step="10000" th:value="${maxPrice}" type="range"/>
      </div>
     </fieldset>
     <div class="actions">
      <button class="btn btn-primary" type="submit">
       Tìm kiếm
      </button>
      <a class="btn btn-secondary" th:href="@{/providers}">
       Xóa bộ lọc
      </a>
     </div>
    </form>
    <div class="filter-selected" th:if="${skill != null and !skill.isBlank()}">Đang lọc: <span th:text="${skill}"></span> <a th:href="@{/providers(q=${q},minPrice=${minPrice},maxPrice=${maxPrice},sort=${sort})}" aria-label="Bỏ lọc kỹ năng">×</a></div>
    <p class="muted" th:text="${#lists.size(providers)+' Provider phù hợp'}">
    </p>
    <div class="expert-grid">
<article class="card expert-card" th:each="p : ${providers}">
 <div class="expert-person"><img th:src="${p.user.avatarUrl}" alt="Ảnh Provider"><div><h3 th:text="${p.user.fullName}">Provider</h3>
  <small class="verified"><span aria-hidden="true">●</span> Đã xác minh (demo)</small>
  <div class="expert-rating"><span class="rating-star">★</span> <strong th:text="${p.rating}">4.9</strong> <span th:text="${'· '+p.reviewCount+' đánh giá'}"></span></div></div></div>
 <p class="expert-headline" th:text="${p.headline}"></p>
 <div class="expert-gallery" aria-label="Ảnh portfolio minh họa"><span class="portfolio-thumb pt-1">UI</span><span class="portfolio-thumb pt-2">API</span><span class="portfolio-thumb pt-3">WEB</span></div>
 <div class="tags expert-skills"><span class="tag" th:each="s : ${p.skills}" th:text="${s.name}"></span></div>
 <p class="expert-hours">◷ <span th:text="${p.availability}"></span></p>
 <div class="expert-price">Từ <strong th:text="${#numbers.formatDecimal(p.hourlyRate,0,'POINT',0,'COMMA')+'đ'}"></strong> / giờ</div>
 <div class="actions expert-actions"><a class="btn btn-outline btn-sm" th:href="@{/providers/{id}(id=${p.id})}">Xem hồ sơ</a><a class="btn btn-primary btn-sm" th:href="@{/booking/provider/{id}(id=${p.id})}">Đặt Provider</a></div>
</article>
    </div>
    <p class="card muted" th:if="${#lists.isEmpty(providers)}">
     Không tìm thấy Provider trong khoảng giá và kỹ năng đã chọn.
    </p>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" src="/js/app.js">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/requests/edit.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Chỉnh sửa Request · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="management-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <main class="page">
   <div class="container layout management-layout">
    <div th:replace="~{fragments :: sidebar}">
    </div>
    <section class="page-content">
     <h1 class="title">
      Chỉnh sửa Request
     </h1>
     <form class="card" method="post" th:action="@{/requests/{id}/edit(id=${request.id})}" th:object="${form}">
      <div class="form-group">
       <label>
        Tiêu đề
       </label>
       <input class="input" required="" th:field="*{title}"/>
       <small class="error" th:errors="*{title}">
       </small>
      </div>
      <div class="form-group">
       <label>
        Kỹ năng
       </label>
       <input class="input" required="" th:field="*{requiredSkills}"/>
      </div>
      <div class="form-group">
       <label>
        Mô tả
       </label>
       <textarea class="textarea" required="" th:field="*{description}"></textarea>
      </div>
      <div class="row">
       <div class="form-group">
        <label>
         Ngân sách (đ)
        </label>
        <input class="input" min="1" required="" th:field="*{budget}" type="number"/>
       </div>
       <div class="form-group">
        <label>
         Hạn hoàn thành
        </label>
        <input class="input" required="" th:field="*{deadline}" type="date"/>
       </div>
      </div>
      <button class="btn btn-primary" type="submit">
       Lưu Request
      </button>
     </form>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```

---

## `src/main/resources/templates/requests/list.html`

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
 <head>
  <meta charset="utf-8"/>
  <meta content="width=device-width, initial-scale=1" name="viewport"/>
  <title>
   Quản lý Request · Skill Rental
  </title>
  <link href="https://fonts.googleapis.com" rel="preconnect"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
  <link rel="stylesheet" th:href="@{/css/app.css}"/>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
 </head>
 <body class="management-page">
  <div th:replace="~{fragments :: icons}">
  </div>
  <main class="page">
   <div class="container layout management-layout">
    <div th:replace="~{fragments :: sidebar}">
    </div>
    <section class="page-content">
     <div class="section-top">
      <div>
       <h1 class="title">
        Quản lý Request
       </h1>
       <p class="muted">
        Quản lý các yêu cầu kỹ năng và theo dõi tiến độ.
       </p>
      </div>
      <a class="btn btn-primary" th:href="@{/requests/create}">
       + Tạo Request
      </a>
     </div>
     <div th:replace="~{fragments :: flash}">
     </div>
     <div class="request-stats">
      <article class="card" th:each="stat : ${requestStats}">
       <small th:text="${stat.label}">
       </small>
       <div class="stat-number" th:text="${stat.value}">
       </div>
       <span aria-hidden="true" class="stat-symbol">
        ⌁
       </span>
      </article>
     </div>
     <div class="card request-table-card">
      <div class="tablewrap">
       <table class="request-table">
        <thead>
         <tr>
          <th>
           ID
          </th>
          <th>
           Tên Request
          </th>
          <th>
           Ngân sách
          </th>
          <th>
           Hạn hoàn thành
          </th>
          <th>
           Trạng thái
          </th>
          <th>
           Provider ứng tuyển
          </th>
          <th>
           Hành động
          </th>
         </tr>
        </thead>
        <tbody>
         <tr th:each="r : ${requests}">
          <td th:text="${'SR'+#numbers.formatInteger(r.id,3)}">
          </td>
          <td>
           <strong th:text="${r.title}">
           </strong>
           <div class="muted" th:text="${r.requiredSkills}">
           </div>
          </td>
          <td th:text="${#numbers.formatDecimal(r.budget,0,'POINT',0,'COMMA')+'đ'}">
          </td>
          <td th:text="${#temporals.format(r.deadline,'dd/MM/yyyy')}">
          </td>
          <td>
           <span class="badge" th:classappend="${r.status.name()=='OPEN'?'pending':(r.status.name()=='IN_PROGRESS'?'info':(r.status.name()=='COMPLETED'?'done':'bad'))}" th:text="${r.status.name()=='OPEN'?'Đang mở':(r.status.name()=='IN_PROGRESS'?'Đang thực hiện':(r.status.name()=='COMPLETED'?'Hoàn thành':'Đã hủy'))}">
           </span>
          </td>
          <td>
           <div class="avatar-stack" th:if="${!#lists.isEmpty(applyMap[r.id])}">
            <img th:alt="${a.provider.fullName}" th:each="a,iter : ${applyMap[r.id]}" th:if="${iter.index<3}" th:src="${a.provider.avatarUrl}"/>
            <span th:if="${#lists.size(applyMap[r.id])>3}" th:text="${'+'+(#lists.size(applyMap[r.id])-3)}">
            </span>
           </div>
           <span class="muted" th:if="${#lists.isEmpty(applyMap[r.id])}">
            Chưa có ứng viên
           </span>
          </td>
          <td>
           <div class="request-actions">
            <a th:href="@{/requests/{id}/applies(id=${r.id})}">
             ◉ Xem chi tiết
            </a>
            <a th:href="@{/requests/{id}/applies(id=${r.id})}" th:if="${r.status.name()=='OPEN' and !#lists.isEmpty(applyMap[r.id])}">
             ✓ Chọn ứng tuyển
            </a>
            <a th:href="@{/requests/{id}/edit(id=${r.id})}" th:if="${r.status.name()=='OPEN' and #lists.isEmpty(applyMap[r.id])}">
             ✎ Chỉnh sửa
            </a>
            <a th:if="${r.status.name()=='COMPLETED'}" th:href="@{/orders}">
             ★ Đánh giá qua đơn hàng
            </a>
           </div>
          </td>
         </tr>
         <tr th:if="${#lists.isEmpty(requests)}">
          <td class="muted" colspan="7">
           Bạn chưa có Request nào.
          </td>
         </tr>
        </tbody>
       </table>
      </div>
     </div>
    </section>
   </div>
  </main>
  <div th:replace="~{fragments :: footer}">
  </div>
  <div th:replace="~{fragments :: floatingChat}">
  </div>
  <script defer="" th:src="@{/js/app.js}">
  </script>
  <script defer="" th:src="@{/js/figma-ui.js}">
  </script>
 </body>
</html>
```
