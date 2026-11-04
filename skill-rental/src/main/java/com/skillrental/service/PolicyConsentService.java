package com.skillrental.service;
import com.skillrental.entity.*;import com.skillrental.repository.*;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@Service @RequiredArgsConstructor
public class PolicyConsentService {
 private final TermsAcceptanceRepository acceptances;private final PolicySnapshotRepository snapshots;private final UserRepository users;
 public static final Map<String,String> VERSIONS=Map.of("TERMS_OF_USE","2026-11-01-v2","BOOKING_POLICY","2026-11-01-v2","PROVIDER_POLICY","2026-11-01-v2");
 @Transactional(readOnly=true) public boolean needed(User user,String type){String version=version(type);return acceptances.findFirstByUserAndPolicyTypeOrderByAcceptedAtDesc(user,type).filter(a->version.equals(a.getPolicyVersion())).isEmpty();}
 @Transactional public void accept(User user,String type,String source,boolean agreed){
  users.findByIdForChatUpdate(user.getId()).orElseThrow();
  if(!needed(user,type))return;
  if(!agreed)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bạn cần chấp nhận điều khoản liên quan trước khi tiếp tục.");
  snapshot(type);
  acceptances.save(TermsAcceptance.builder().user(user).policyType(type).policyVersion(version(type)).source(source).build());
 }
 @Transactional public void snapshot(String type){
  if(snapshots.findByPolicyTypeAndPolicyVersion(type,version(type)).isPresent())return;
  // Current immutable snapshots are initialized at startup; the database enforces one row per type/version.
  String slug=switch(type){case "PROVIDER_POLICY"->"provider";case "BOOKING_POLICY"->"booking";default->"terms";};
  var page=PolicyContent.PAGES.get(slug);String text=String.join("\n\n",page.paragraphs());
  if(type.equals("TERMS_OF_USE"))text+="\n\nChính sách quyền riêng tư\n"+String.join("\n\n",PolicyContent.PAGES.get("privacy").paragraphs());
  snapshots.saveAndFlush(PolicySnapshot.builder().policyType(type).policyVersion(version(type)).title(page.title()).content(text).build());
 }
 private String version(String type){var v=VERSIONS.get(type);if(v==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return v;}
 @Transactional(readOnly=true) public PolicySnapshot accepted(Long acceptanceId,User user){var a=acceptances.findById(acceptanceId).filter(x->x.getUser().getId().equals(user.getId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return snapshots.findByPolicyTypeAndPolicyVersion(a.getPolicyType(),a.getPolicyVersion()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Chưa có bản lưu nội dung của điều khoản cũ."));}
}
