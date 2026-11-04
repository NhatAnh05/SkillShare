package com.skillrental.config;
import com.skillrental.websocket.SocketSessionRegistry;import org.springframework.context.annotation.*;import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;import jakarta.servlet.http.*;
@Configuration public class SocketSessionLifecycleConfig {
 @Bean ServletListenerRegistrationBean<HttpSessionListener> websocketLogoutCleanup(SocketSessionRegistry registry){return new ServletListenerRegistrationBean<>(new HttpSessionListener(){public void sessionDestroyed(HttpSessionEvent event){registry.close(event.getSession().getId());}});}
}
