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
    @GetMapping("/browse")
    String browse(Authentication authentication, Model model) {
        var user = current.require(authentication);
        var openRequests = service.browseOpenRequests();
        model.addAttribute("openRequests", openRequests);
        model.addAttribute("requestApplyCounts", service.applicationCounts(openRequests));
        model.addAttribute("currentRole", user.getRole().name());
        return "requests/browse";
    }

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
