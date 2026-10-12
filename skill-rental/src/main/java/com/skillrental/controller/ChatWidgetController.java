package com.skillrental.controller;

import com.skillrental.dto.MessageForm;
import com.skillrental.entity.Conversation;
import com.skillrental.repository.ConversationRepository;
import com.skillrental.service.ChatService;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.MarketplaceService;
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
    private final ConversationRepository conversations;
    private final MarketplaceService marketplace;
    @GetMapping("/chat")
    public String legacyChat() {
        return "redirect:/marketplace?chat=open";
    }
    @GetMapping("/chat/conversations") @ResponseBody
    public List<Map<String,Object>> conversations(Authentication auth) {
        return chat.list(current.require(auth)).stream().map(c->Map.<String,Object>of(
        "id",c.getId(),"providerName",c.getProvider().getFullName())).toList();
    }
    @PostMapping("/chat/start/{providerId}")
    public String start(@PathVariable Long providerId,Authentication auth) {
        var u=current.require(auth);
        var provider=marketplace.get(providerId).getUser();
        if(u.getId().equals(provider.getId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Không thể tự nhắn tin");
        Conversation c=conversations.findByCustomerAndProvider(u,provider)
        .orElseGet(()->conversations.save(Conversation.builder().customer(u).provider(provider).build()));
        return "redirect:/marketplace?chat=open&conversationId="+c.getId();
    }
    @PostMapping("/chat/{id}/send-ajax") @ResponseBody
    public Map<String,Object> send(@PathVariable Long id,@Valid @RequestBody MessageForm form,Authentication auth) {
        var u=current.require(auth);
        chat.send(chat.owned(id,u),u,form.getContent());
        return Map.of("success",true);
    }
}
