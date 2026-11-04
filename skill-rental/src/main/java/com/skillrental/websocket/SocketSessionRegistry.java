package com.skillrental.websocket;
import org.springframework.stereotype.Component;import org.springframework.web.socket.*;import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import java.util.*;import java.util.concurrent.ConcurrentHashMap;import java.io.IOException;
@Component public class SocketSessionRegistry {
 private final ConcurrentHashMap<String,Set<WebSocketSession>> sessions=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,Long> invalidated=new ConcurrentHashMap<>();
 public void register(WebSocketSession session){Object id=session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);if(id instanceof String key){if(invalidated.containsKey(key)){reject(session);return;}sessions.computeIfAbsent(key,k->ConcurrentHashMap.newKeySet()).add(session);if(invalidated.containsKey(key)){remove(session);reject(session);}}}
 private void reject(WebSocketSession session){try{session.close(CloseStatus.POLICY_VIOLATION);}catch(IOException|IllegalStateException ignored){}}

 public void remove(WebSocketSession session){Object id=session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);if(id instanceof String key){var set=sessions.get(key);if(set!=null){set.remove(session);if(set.isEmpty())sessions.remove(key,set);}}}
 public void close(String id){long now=System.currentTimeMillis();invalidated.put(id,now);if(invalidated.size()>1000)invalidated.entrySet().removeIf(e->now-e.getValue()>300000);var set=sessions.remove(id);if(set!=null)for(var session:set)try{session.close(CloseStatus.POLICY_VIOLATION);}catch(IOException|IllegalStateException ignored){}}
}
