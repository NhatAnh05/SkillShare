package com.skillrental;
import com.skillrental.websocket.SocketSessionRegistry;import org.junit.jupiter.api.Test;import org.springframework.web.socket.*;import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;import static org.mockito.Mockito.*;
class SocketSessionRegistryTest {
 @Test void logoutClosesOnlySocketsBelongingToThatHttpSession()throws Exception{var registry=new SocketSessionRegistry();var a=mock(WebSocketSession.class);var b=mock(WebSocketSession.class);var other=mock(WebSocketSession.class);
 when(a.getAttributes()).thenReturn(java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,"session-a"));when(b.getAttributes()).thenReturn(java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,"session-a"));when(other.getAttributes()).thenReturn(java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,"session-b"));registry.register(a);registry.register(b);registry.register(other);registry.close("session-a");verify(a).close(CloseStatus.POLICY_VIOLATION);verify(b).close(CloseStatus.POLICY_VIOLATION);verify(other,never()).close(any());}
 @Test void staleHandshakeCannotRegisterAfterSessionWasInvalidated()throws Exception{var registry=new SocketSessionRegistry();registry.close("old-session");var socket=mock(WebSocketSession.class);when(socket.getAttributes()).thenReturn(java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,"old-session"));registry.register(socket);verify(socket).close(CloseStatus.POLICY_VIOLATION);}

}
