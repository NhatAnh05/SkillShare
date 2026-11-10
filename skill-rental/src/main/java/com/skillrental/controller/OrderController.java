package com.skillrental.controller;

import com.skillrental.entity.Escrow;
import com.skillrental.entity.OrderStatus;
import com.skillrental.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;
import java.util.Optional;

@Controller @RequiredArgsConstructor public class OrderController {
    private final CurrentUserService current;
    private final OrderService orders;
    private final EscrowService escrows;
    private final WalletService wallet;

    @GetMapping("/orders") String list(Authentication a,Model m) {
        var u=current.require(a);
        m.addAttribute("orders",orders.list(u));
        m.addAttribute("completedCount",orders.completed(u));
        m.addAttribute("processingCount",orders.processingCount(u));
        m.addAttribute("walletBalance",wallet.available(u));
        return "orders/orders";
    }
    @GetMapping("/booking/complete/{id}")
    String bookingComplete(@PathVariable Long id, Authentication authentication, Model model) {
        var order = orders.owned(id, current.require(authentication));
        model.addAttribute("order", order);
        model.addAttribute("escrow", escrows.find(order).orElse(null));
        model.addAttribute("walletBalance", wallet.available(current.require(authentication)));
        return "booking/complete";
    }

    @GetMapping("/orders/table") String table(Authentication auth,Model model) {
        var u=current.require(auth);
        model.addAttribute("orders",orders.list(u));
        return "orders/orders :: orderTable";
    }
    @GetMapping("/orders/{id}/details") String details(@PathVariable Long id,Authentication auth,Model model) {
        com.skillrental.entity.RentalOrder order;
        try {
            order=orders.owned(id,current.require(auth));
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            // AJAX fragment: return HTTP error so app.js shows "Không thể tải chi tiết đơn hàng." instead of a 500 page.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
        addOrderContext(model, order);
        return "orders/progress :: details";
    }
    @PostMapping("/orders/{id}/state") String state(@PathVariable Long id,@RequestParam String action,Authentication auth,Model model,RedirectAttributes ra) {
        var order=orders.owned(id,current.require(auth));
        var escrow = escrows.find(order).orElse(null);
        try {
            if("processing".equals(action)) {
                if (escrow != null) {
                    escrows.confirmStart(order, current.require(auth));
                } else if (order.getStatus()==OrderStatus.PAID) {
                    orders.processing(order);
                } else {
                    throw new IllegalArgumentException("Trạng thái không cho phép");
                }
            }
            else if("complete".equals(action)&&escrow==null&&order.getStatus()==OrderStatus.PROCESSING) {
                orders.complete(order);
            }
            else {
                throw new IllegalArgumentException("Vui lòng dùng nút nghiệm thu và nhập OTP để tất toán đơn escrow.");
            }
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        addOrderContext(model, model.getAttribute("order") instanceof com.skillrental.entity.RentalOrder ro ? ro : order);
        return "orders/progress :: details";
    }
    @GetMapping("/orders/{id}/progress") String progress(@PathVariable Long id,Authentication a,Model m) {
        var order=orders.owned(id,current.require(a));
        addOrderContext(m, order);
        return "orders/progress";
    }
    @PostMapping("/orders/{id}/processing") String processing(@PathVariable Long id,Authentication a,RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        var escrow = escrows.find(order).orElse(null);
        try {
            if (escrow != null) {
                escrows.confirmStart(order, current.require(a));
                ra.addFlashAttribute("success","Đã ghi nhận xác nhận bắt đầu từ phía bạn.");
            } else {
                orders.processing(order);
            }
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/"+id+"/progress";
    }
    @PostMapping("/orders/{id}/complete") String complete(@PathVariable Long id,Authentication a,RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        if (escrows.find(order).isPresent()) {
            ra.addFlashAttribute("error","Đơn escrow cần provider báo hoàn thành và khách nhập OTP xác nhận.");
            return "redirect:/orders/"+id+"/progress";
        }
        orders.complete(order);
        ra.addFlashAttribute("success","Đã xác nhận hoàn thành.");
        return "redirect:/orders";
    }

    // ---- Escrow flow -----------------------------------------------------

    @PostMapping("/orders/{id}/start-confirm")
    String startConfirm(@PathVariable Long id, Authentication a, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        try {
            escrows.confirmStart(order, current.require(a));
            ra.addFlashAttribute("success","Đã xác nhận bắt đầu. Nếu cả hai bên xác nhận, hệ thống giải ngân 1/3 cho Provider.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/"+id+"/progress";
    }

    @PostMapping("/orders/{id}/accept")
    String acceptCompletion(@PathVariable Long id, Authentication a, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        try {
            String code = escrows.requestFinalOtp(order, current.require(a));
            if (code != null) ra.addFlashAttribute("devOtp", code);
            ra.addFlashAttribute("success","Đã gửi mã OTP xác nhận thanh toán tới email của bạn.");
            return "redirect:/orders/"+id+"/confirm-payment";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/orders/"+id+"/progress";
        }
    }

    @GetMapping("/orders/{id}/confirm-payment")
    String confirmPaymentPage(@PathVariable Long id, Authentication a, Model m, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        var escrow = escrows.find(order).orElse(null);
        if (escrow == null) {
            ra.addFlashAttribute("error","Đơn hàng không sử dụng escrow.");
            return "redirect:/orders/"+id+"/progress";
        }
        addOrderContext(m, order);
        m.addAttribute("escrow", escrow);
        m.addAttribute("remaining", escrows.remaining(order));
        return "orders/confirm-payment";
    }

    @PostMapping("/orders/{id}/confirm-payment")
    String confirmPayment(@PathVariable Long id, @RequestParam String code, Authentication a,
                          Model m, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        try {
            escrows.finalizePayment(order, current.require(a), code);
            ra.addFlashAttribute("success","Xác nhận OTP thành công. Hệ thống đã tất toán escrow cho Provider.");
            return "redirect:/orders";
        } catch (IllegalArgumentException ex) {
            m.addAttribute("error", ex.getMessage());
            addOrderContext(m, order);
            m.addAttribute("escrow", escrows.find(order).orElse(null));
            m.addAttribute("remaining", escrows.remaining(order));
            return "orders/confirm-payment";
        }
    }

    @PostMapping("/orders/{id}/resend-otp")
    String resendOtp(@PathVariable Long id, Authentication a, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        try {
            String code = escrows.requestFinalOtp(order, current.require(a));
            if (code != null) ra.addFlashAttribute("devOtp", code);
            ra.addFlashAttribute("success","Đã gửi lại mã OTP.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/"+id+"/confirm-payment";
    }

    @PostMapping("/orders/{id}/cancel")
    String cancel(@PathVariable Long id, @RequestParam(required=false) String reason,
                  Authentication a, RedirectAttributes ra) {
        var order=orders.owned(id,current.require(a));
        var user = current.require(a);
        String safeReason = (reason == null || reason.isBlank()) ? "Khách yêu cầu hủy đơn." : reason.trim();
        try {
            Optional<Escrow> escrow = escrows.find(order);
            if (escrow.isPresent()) {
                if (escrow.get().getStatus() == com.skillrental.entity.EscrowStatus.HELD) {
                    escrows.cancelBeforeStart(order, user, safeReason);
                    ra.addFlashAttribute("success","Đã hủy đơn trước khi bắt đầu. Toàn bộ tiền khóa đã được hoàn về ví.");
                } else {
                    escrows.requestCancellationAfterStart(order, user, safeReason);
                    ra.addFlashAttribute("success","Đã gửi yêu cầu hủy tới Provider. Provider sẽ mô tả công việc đã làm và đề xuất chi trả.");
                }
            } else {
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancellationReason(safeReason);
                order.setCancelledBy(user);
                order.setCancelledAt(java.time.LocalDateTime.now());
                orders.saveLegacyCancellation(order);
                ra.addFlashAttribute("success","Đã hủy đơn hàng.");
            }
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders";
    }

    private void addOrderContext(Model model, com.skillrental.entity.RentalOrder order) {
        model.addAttribute("order", order);
        var escrow = escrows.find(order).orElse(null);
        model.addAttribute("escrow", escrow);
        model.addAttribute("remaining", escrows.remaining(order));
        if (order.getCustomer() != null) {
            model.addAttribute("customerWallet", wallet.available(order.getCustomer()));
            model.addAttribute("customerHeld", wallet.held(order.getCustomer()));
        }
    }

    /** Turn ownership/validation failures into a friendly redirect instead of a 500 error page. */
    @ExceptionHandler(IllegalArgumentException.class)
    String handleIllegalArgument(IllegalArgumentException ex, Authentication a, RedirectAttributes ra) {
        return accessDenied(ex.getMessage(), a, ra);
    }

    @ExceptionHandler(NoSuchElementException.class)
    String handleNotFound(Authentication a, RedirectAttributes ra) {
        return accessDenied("Không tìm thấy đơn hàng.", a, ra);
    }

    private String accessDenied(String message, Authentication a, RedirectAttributes ra) {
        var u = current.require(a);
        ra.addFlashAttribute("error",
                (message == null ? "Truy cập bị từ chối." : message)
                        + " Trang tiến độ này chỉ dành cho khách hàng của đơn hàng đó.");
        return switch (u.getRole()) {
            case PROVIDER -> "redirect:/provider/bookings";
            case ADMIN -> "redirect:/admin/orders";
            default -> "redirect:/orders";
        };
    }
}
