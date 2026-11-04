package com.skillrental.config;
import com.skillrental.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class PromotedAdminSessionFilter extends OncePerRequestFilter {
 private final UserRepository users;
 public PromotedAdminSessionFilter(UserRepository users){this.users=users;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getServletPath();return path.equals("/login")||path.equals("/logout")||path.startsWith("/css/")||path.startsWith("/js/")||path.startsWith("/images/");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)
      &&users.findByEmailIgnoreCase(auth.getName()).map(u ->
          u.getAccountStatus() != com.skillrental.entity.AccountStatus.ACTIVE
              || !Boolean.TRUE.equals(u.getEmailVerified())
              || auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + u.getRole().name()).equals(a.getAuthority()))
      ).orElse(true)){
   new SecurityContextLogoutHandler().logout(request,response,auth);
   Cookie cookie=new Cookie("remember-me","");cookie.setMaxAge(0);cookie.setPath(request.getContextPath().isEmpty()?"/":request.getContextPath());cookie.setHttpOnly(true);response.addCookie(cookie);
   if("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))||String.valueOf(request.getHeader("Accept")).contains("application/json")){
    response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"Vai trò đã đổi. Vui lòng đăng nhập lại.\"}");
   }else response.sendRedirect(request.getContextPath()+"/login?roleChanged");
   return;
  }
  chain.doFilter(request,response);
 }
}
