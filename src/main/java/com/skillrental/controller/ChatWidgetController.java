package com.skillrental.controller;

import com.skillrental.dto.MessageForm;
import com.skillrental.entity.Conversation;
import com.skillrental.entity.Role;
import com.skillrental.repository.ConversationRepository;
import com.skillrental.service.ChatService;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.MarketplaceService;
import com.skillrental.service.ProviderEligibilityService;
import com.skillrental.repository.MessageRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.SupportAssignmentHistoryRepository;
import com.skillrental.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Controller @RequiredArgsConstructor
public class ChatWidgetController {
    private final CurrentUserService current;
    private final ChatService chat;
    private final com.skillrental.service.SupportManagementService support;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MarketplaceService marketplace;
    private final ProviderEligibilityService eligibility;
    private final NotificationService notifications;
    private final UserRepository users;
    private final SupportAssignmentHistoryRepository assignmentHistory;
    @GetMapping("/chat")
    public String legacyChat() {
        return "redirect:/marketplace?chat=open";
    }
    @GetMapping("/messages")
    public String inbox(Authentication auth) {
        current.require(auth);
        return "interaction/inbox";
    }
    @GetMapping("/chat/conversations") @ResponseBody
    public List<Map<String,Object>> conversations(Authentication auth) {
        var user=current.require(auth);
        return chat.list(user).stream().map(c->{
            var latest=messages.findTopByConversationOrderBySentAtDesc(c).orElse(null);
            return Map.<String,Object>of("id",c.getId(),"participantName",participantName(c,user),
                    "participantAvatar",participantAvatar(c,user),
                    "support",Boolean.TRUE.equals(c.getSupportConversation()),
                    "orderId",c.getOrder()==null?0L:c.getOrder().getId(),
                    "unreadCount",chat.unreadCount(c,user),
                    "lastContent",latest==null?"":latest.getContent(),
                    "lastSentAt",latest==null?"":latest.getSentAt().toString());
        }).toList();
    }
    @PostMapping("/chat/start/{providerId}")
    @ResponseBody
    public Map<String,Object> start(@PathVariable Long providerId,Authentication auth) {
        var u=current.require(auth);
        if(u.getRole()!=Role.CUSTOMER)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ Customer mới được liên hệ Provider.");
        var profile=marketplace.get(providerId);
        if(!eligibility.isEligible(profile))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Provider hiện không hoạt động.");
        var provider=profile.getUser();
        if(u.getId().equals(provider.getId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Không thể tự nhắn tin");
        Conversation c=support.openPrivate(u,provider);
        return Map.of("conversationId",c.getId(),"participantName",provider.getFullName());
    }

    @PostMapping("/chat/support/start")
    @ResponseBody
    public Map<String,Object> startSupport(Authentication auth) {
        var user=current.require(auth);
        if(user.getRole()!=Role.CUSTOMER&&user.getRole()!=Role.PROVIDER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản này không thể mở yêu cầu hỗ trợ.");
        Conversation c=support.openSupport(user,user);
        return Map.of("conversationId",c.getId(),"participantName","Hỗ trợ SkillShare");
    }

    @GetMapping("/chat/admin/users")
    @ResponseBody
    public List<Map<String,Object>> searchUsers(@RequestParam(defaultValue = "") String q,
                                                Authentication auth) {
        var admin=current.require(auth);
        if(admin.getRole()!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String query=q.strip().toLowerCase(java.util.Locale.ROOT);
        if(query.length()<2&&!query.matches("[0-9]+"))return List.of();
        return users.findAll().stream()
                .filter(user->user.getRole()==Role.CUSTOMER||user.getRole()==Role.PROVIDER)
                .filter(user->user.getAccountStatus()==com.skillrental.entity.AccountStatus.ACTIVE)
                .filter(user->user.getEmail().toLowerCase(java.util.Locale.ROOT).contains(query)
                        ||user.getFullName().toLowerCase(java.util.Locale.ROOT).contains(query)
                        ||String.valueOf(user.getId()).equals(query))
                .limit(25)
                .map(user->Map.<String,Object>of("id",user.getId(),"name",user.getFullName(),
                        "email",user.getEmail(),"role",user.getRole().name()))
                .toList();
    }

    @PostMapping("/chat/admin/users/{userId}/start")
    @ResponseBody
    public Map<String,Object> startAdminConversation(@PathVariable Long userId,Authentication auth) {
        var admin=current.require(auth);
        if(admin.getRole()!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var requester=users.findById(userId)
                .filter(user->user.getRole()==Role.CUSTOMER||user.getRole()==Role.PROVIDER)
                .filter(user->user.getAccountStatus()==com.skillrental.entity.AccountStatus.ACTIVE)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy người dùng đang hoạt động."));
        Conversation conversation=support.openSupport(requester,admin);
        return Map.of("conversationId",conversation.getId(),"participantName",requester.getFullName());
    }

    @PostMapping("/chat/support/{id}/claim")
    @ResponseBody
    public Map<String,Object> claimSupport(@PathVariable Long id,Authentication auth) {
        var admin=current.require(auth);
        try {
            chat.claimSupport(id,admin);
            return Map.of("success",true);
        } catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,ex.getMessage(),ex);
        } catch(IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage(),ex);
        }
    }

    @PostMapping("/chat/support/{id}/release")
    @ResponseBody
    public Map<String,Object> releaseSupport(@PathVariable Long id,Authentication auth) {
        var admin=current.require(auth);
        try {
            chat.releaseSupport(id,admin);
            return Map.of("success",true);
        } catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,ex.getMessage(),ex);
        }
    }

    @GetMapping("/chat/support/{id}/assignment-history")
    @ResponseBody
    public List<Map<String,Object>> supportAssignmentHistory(@PathVariable Long id,Authentication auth) {
        var admin=current.require(auth);
        if(admin.getRole()!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            chat.requireParticipant(id,admin);
        } catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,ex.getMessage(),ex);
        }
        return assignmentHistory.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .map(item->Map.<String,Object>of("admin",item.getChangedBy().getFullName(),
                        "action",item.getAction(),"time",item.getCreatedAt().toString()))
                .toList();
    }

    private String participantName(Conversation conversation,com.skillrental.entity.User viewer) {
        if(Boolean.TRUE.equals(conversation.getSupportConversation())) {
            if(viewer.getRole()==Role.ADMIN) {
                var requester=conversation.getCustomer()!=null?conversation.getCustomer():conversation.getProvider();
                return requester==null?"Yêu cầu hỗ trợ":requester.getFullName();
            }
            return "Hỗ trợ SkillShare";
        }
        return conversation.getCustomer().getId().equals(viewer.getId())
                ? conversation.getProvider().getFullName():conversation.getCustomer().getFullName();
    }

    private String participantAvatar(Conversation conversation,com.skillrental.entity.User viewer) {
        if(Boolean.TRUE.equals(conversation.getSupportConversation())) return "";
        var other=conversation.getCustomer().getId().equals(viewer.getId())
                ? conversation.getProvider():conversation.getCustomer();
        return other.getAvatarUrl()==null?"":other.getAvatarUrl();
    }
    @PostMapping("/chat/{id}/send-ajax") @ResponseBody
    public Map<String,Object> send(@PathVariable Long id,@Valid @RequestBody MessageForm form,Authentication auth) {
        var u=current.require(auth);
        try {
            var sent=chat.send(chat.requireParticipant(id,u),u,form.getContent(),form.getClientKey());
            return Map.of("success",true,"id",sent.getId());
        } catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,ex.getMessage(),ex);
        }
    }
}
