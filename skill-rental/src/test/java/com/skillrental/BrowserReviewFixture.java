package com.skillrental;
/** Optional local browser-QA fixture only. Never included in the application runtime classpath. */
public class BrowserReviewFixture {
 public static void main(String[] args)throws Exception {
  var app=new org.springframework.boot.SpringApplication(SkillRentalApplication.class);
  var context=app.run(args);
  var users=context.getBean(com.skillrental.repository.UserRepository.class);
  var encoder=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
  var customer=users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();var provider=users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();var admin=users.findByEmailIgnoreCase("admin@skillrental.vn").orElseThrow();
  var reviewer=users.saveAndFlush(com.skillrental.entity.User.builder().fullName("Browser QA Reviewer").email("reviewer@skillrental.test").password(encoder.encode("123456")).role(com.skillrental.entity.Role.ADMIN).emailVerified(true).accountStatus(com.skillrental.entity.AccountStatus.ACTIVE).build());
  var cv=context.getBean(com.skillrental.service.SupportManagementService.class).openPrivate(customer,provider);
  context.getBean(com.skillrental.service.ChatService.class).send(cv,customer,"Browser review evidence: private message");
  context.getBean(com.skillrental.service.WorkDocumentService.class).upload(null,cv.getId(),customer,"Browser review evidence document",java.util.List.of(),java.util.List.of(new org.springframework.mock.web.MockMultipartFile("files","evidence.txt","text/plain","Browser private evidence file".getBytes())));
  var order=context.getBean(com.skillrental.repository.RentalOrderRepository.class).findById(1L).orElseThrow();
  var complaint=context.getBean(com.skillrental.repository.ComplaintRepository.class).saveAndFlush(com.skillrental.entity.Complaint.builder().customer(customer).reporter(customer).reportedUser(provider).order(order).handledBy(admin).subject("Browser review complaint").description("Explicitly scoped QA evidence review").status(com.skillrental.entity.ComplaintStatus.PROCESSING).createdAt(java.time.LocalDateTime.now()).build());
  var fmt=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  var metadata=java.util.Map.of("conversationId",cv.getId(),"complaintId",complaint.getId(),"from",java.time.LocalDateTime.now().minusDays(1).format(fmt),"to",java.time.LocalDateTime.now().plusMinutes(1).format(fmt));
  java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("SKILLSHARE_QA_REVIEW_FIXTURE","/tmp/skillshare-review-fixture.json")),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata));
  System.out.println("BROWSER_REVIEW_FIXTURE_READY");
 }
}
