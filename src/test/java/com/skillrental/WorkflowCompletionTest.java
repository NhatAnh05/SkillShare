package com.skillrental;
import com.skillrental.entity.*;import com.skillrental.repository.*;import com.skillrental.service.*;
import com.skillrental.websocket.ChatWebSocketHandler;import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;import org.springframework.web.socket.*;
import java.time.*;import java.util.*;import java.math.BigDecimal;import java.nio.file.*;import java.io.*;import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"skillshare.support.review-authorizers=review-authorizer@workflow.test","skillshare.delivery.storage-dir=./target/workflow-test-files"})
@AutoConfigureMockMvc
class WorkflowCompletionTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired UserRepository users;@Autowired RentalOrderRepository orders;
 @Autowired ConversationRepository conversations;@Autowired MessageRepository messages;@Autowired ChatService chat;@Autowired ChatWebSocketHandler socket;
 @Autowired WorkDocumentService documents;@Autowired WorkDocumentRepository documentRows;@Autowired DeliverySubmissionRepository deliveries;
 @Autowired SupportManagementService support;@Autowired SupportNoteRepository notes;@Autowired SupportAssignmentHistoryRepository assignments;
 @Autowired ConversationReviewService reviews;@Autowired ConversationReviewGrantRepository grants;@Autowired ConversationAccessAuditRepository accesses;
 @Autowired ComplaintRepository complaints;@Autowired PolicyConsentService consent;@Autowired TermsAcceptanceRepository acceptances;
 @Autowired NotificationRepository notifications;
 @Autowired PrivateFileStorage privateFiles;
 @Autowired OrderWorkService work;
 @Autowired EscrowService escrow;
 @Autowired DeliveryService deliveryService;
 @Autowired ProviderProfileRepository providerProfiles;
 @Autowired OfferedServiceRepository services;
 @Autowired UserWalletRepository wallets;
 List<WebSocketSession> sockets=new ArrayList<>();
 @AfterEach void close(){for(var s:sockets)socket.afterConnectionClosed(s,CloseStatus.NORMAL);}
 User createUser(Role role){return users.saveAndFlush(User.builder().fullName("Workflow "+role+" "+UUID.randomUUID()).email(UUID.randomUUID()+"@workflow.test").role(role).password("unused").emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());}
 RentalOrder order(User c,User p){return orders.saveAndFlush(RentalOrder.builder().customer(c).provider(p).subtotal(new BigDecimal("100000")).discount(BigDecimal.ZERO).total(new BigDecimal("100000")).status(OrderStatus.PROCESSING).createdAt(LocalDateTime.now()).customerStarted(true).providerStarted(true).providerAccepted(true).build());}
 MockMultipartFile file(String name,String text){return new MockMultipartFile("files",name,"text/plain",text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 WebSocketSession online(User u)throws Exception {var s=mock(WebSocketSession.class);when(s.getAttributes()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());when(s.getPrincipal()).thenReturn(u::getEmail);when(s.isOpen()).thenReturn(true);socket.afterConnectionEstablished(s);sockets.add(s);return s;}
 @Test void newConversationPushesBothWaysWithoutSubscribingAndNotToAdmin()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var admin=createUser(Role.ADMIN);var ps=online(p);var ps2=online(p);var cs=online(c);var as=online(admin);
  var conversation=support.openPrivate(c,p);chat.send(conversation,c,"first message to unknown conversation");
  verify(ps).sendMessage(argThat(m->m.getPayload().toString().contains("first message to unknown conversation")));
  verify(ps2).sendMessage(argThat(m->m.getPayload().toString().contains("first message to unknown conversation")));
  verify(as,never()).sendMessage(any());chat.send(conversation,p,"reply without subscription");
  verify(cs).sendMessage(argThat(m->m.getPayload().toString().contains("reply without subscription")));
  assertTrue(notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(p).stream().anyMatch(n->n.getContent().equals("first message to unknown conversation")));
 }
 @Test void sendingSameClientKeyIsIdempotentAndReadDoesNotAlterNotifications()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var cv=support.openPrivate(c,p);String key=UUID.randomUUID().toString();
  var first=chat.send(cv,c,"saved once",key);assertEquals(first.getId(),chat.send(cv,c,"saved once",key).getId());
  assertThrows(IllegalArgumentException.class,()->chat.send(cv,c,"different",key));
  assertEquals(1,messages.findByConversationOrderBySentAtAsc(cv).size());
  mvc.perform(get("/chat/{id}/messages",cv.getId()).with(user(p.getEmail()).roles("PROVIDER"))).andExpect(status().isOk());
  assertEquals(1,chat.unreadCount(cv,p));
  mvc.perform(post("/notifications/read-all").with(csrf()).with(user(p.getEmail()).roles("PROVIDER"))).andExpect(status().isOk());
  assertEquals(1,chat.unreadCount(cv,p));
  mvc.perform(post("/chat/{id}/read",cv.getId()).with(csrf()).with(user(p.getEmail()).roles("PROVIDER"))).andExpect(status().isOk());assertEquals(0,chat.unreadCount(cv,p));
 }
 @Test void messagesPageByIdWithoutDroppingHistory()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var cv=support.openPrivate(c,p);for(int i=0;i<60;i++)chat.send(cv,c,"History "+i);
  var latest=chat.page(cv,null);assertEquals(50,latest.size());var older=chat.page(cv,latest.get(0).getId());assertEquals(10,older.size());assertTrue(older.get(9).getId()<latest.get(0).getId());
 }
 @Test void materialExchangeIsTwoWayPrivateAndDoesNotCompleteOrder()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var outsider=createUser(Role.CUSTOMER);var o=order(c,p);
  var sent=documents.upload(o.getId(),null,c,"Customer brief",List.of("https://example.test/brief"),List.of(file("brief.txt","customer brief")));
  documents.upload(o.getId(),null,p,"Provider draft",List.of(),List.of(file("draft.txt","provider draft")));
  assertEquals(3,documents.list(o.getId(),null,c,0).size());assertEquals(OrderStatus.PROCESSING,orders.findById(o.getId()).orElseThrow().getStatus());assertTrue(deliveries.findByOrderIdOrderByVersionDesc(o.getId()).isEmpty());
  var attachment=sent.stream().filter(d->d.downloadUrl()!=null).findFirst().orElseThrow();
  mvc.perform(get(attachment.downloadUrl()).with(user(p.getEmail()).roles("PROVIDER"))).andExpect(status().isOk()).andExpect(content().bytes("customer brief".getBytes()));
  mvc.perform(get(attachment.downloadUrl()).with(user(outsider.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(get("/orders/{id}/documents",o.getId()).with(user(outsider.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
  assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,outsider,"not mine",List.of(),List.of(file("a.txt","test"))));
 }
 @Test void dangerousFilesAndLinksAreRejectedAndRollbackCleansCreatedFiles()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var o=order(c,p);long before=fileCount();
  assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"Two files",List.of(),List.of(file("valid.txt","ok"),file("bad.exe","MZbad"))));
  assertEquals(before,fileCount());assertTrue(documents.list(o.getId(),null,c,0).isEmpty());
  assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"Bad picture",List.of(),List.of(file("fake.png","not a png"))));
  assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"Bad URL",List.of("javascript:alert(1)"),List.of()));
  byte[] zip;try(var out=new ByteArrayOutputStream();var z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("../escape.txt"));z.write("bad".getBytes());z.closeEntry();z.finish();zip=out.toByteArray();}
  byte[] unsafe=zip;assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"Bad zip",List.of(),List.of(new MockMultipartFile("files","bad.zip","application/zip",unsafe))));
 }
 long fileCount()throws Exception {var p=Path.of("target/workflow-test-files");if(!Files.exists(p))return 0;try(var files=Files.list(p)){return files.count();}}
 @Test void supportNotesAssignmentAndLinkedRecordsRemainPrivate()throws Exception {
  var c=createUser(Role.CUSTOMER);var a=createUser(Role.ADMIN);var b=createUser(Role.ADMIN);var p=createUser(Role.PROVIDER);var o=order(c,p);
  var cv=support.openSupport(c,c);assertEquals(cv.getId(),support.openSupport(c,c).getId());chat.claimSupport(cv.getId(),a);
  support.note(cv.getId(),a,"Internal review only");support.update(cv.getId(),a,"OPEN",o.getId(),null);
  mvc.perform(get("/admin/support/{id}/notes",cv.getId()).with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(get("/chat/conversations").with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Internal review only"))));
  var oldSocket=online(a);var newSocket=online(b);chat.transferSupport(cv.getId(),a,b.getId(),"New shift");clearInvocations(oldSocket,newSocket);
  chat.send(conversations.findById(cv.getId()).orElseThrow(),c,"Message after transfer");verify(oldSocket,never()).sendMessage(any());verify(newSocket).sendMessage(argThat(m->m.getPayload().toString().contains("Message after transfer")));
  assertThrows(IllegalArgumentException.class,()->chat.requireParticipant(cv.getId(),a));assertEquals(2,support.notes(cv.getId(),b).size());assertTrue(assignments.findByConversationIdOrderByCreatedAtAsc(cv.getId()).stream().anyMatch(h->h.getAction().equals("TRANSFERRED")));
 }
 User approver(){return users.findByEmailIgnoreCase("review-authorizer@workflow.test").orElseGet(()->users.saveAndFlush(User.builder().fullName("Review Authorizer").email("review-authorizer@workflow.test").password("unused").role(Role.ADMIN).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build()));}
 @Test void privateChatRequiresIndependentApprovalAndEveryReadIsAudited()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var a=createUser(Role.ADMIN);var b=approver();var o=order(c,p);var cv=support.openPrivate(c,p);chat.send(cv,c,"private evidence");
  var complaint=complaints.saveAndFlush(Complaint.builder().customer(c).reporter(c).order(o).handledBy(a).subject("Review").description("Evidence review").status(ComplaintStatus.PROCESSING).createdAt(LocalDateTime.now()).build());
  assertThrows(IllegalArgumentException.class,()->chat.requireParticipant(cv.getId(),a));
  var g=reviews.request(a,cv.getId(),complaint.getId(),"Investigate case",LocalDateTime.now().minusDays(1),LocalDateTime.now().plusSeconds(10));
  assertThrows(RuntimeException.class,()->reviews.read(g.getId(),a,0,false,null));assertThrows(RuntimeException.class,()->reviews.decide(g.getId(),a,true,true,"self approve"));
  reviews.decide(g.getId(),b,true,false,"Approved scope");var rows=reviews.read(g.getId(),a,0,false,null);assertEquals("private evidence",rows.get(0).get("content"));
  assertThrows(RuntimeException.class,()->reviews.read(g.getId(),a,0,true,"export"));assertThrows(RuntimeException.class,()->reviews.read(g.getId(),b,0,false,null));
  assertTrue(accesses.findByGrantIdOrderByIdAsc(g.getId()).stream().anyMatch(log->log.getAction().equals("VIEW")&&log.getDataScope().contains("count=1")));
  var expired=grants.findById(g.getId()).orElseThrow();expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));grants.saveAndFlush(expired);assertThrows(RuntimeException.class,()->reviews.read(g.getId(),a,0,false,null));
  mvc.perform(get("/chat/{id}/messages",cv.getId()).with(user(a.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
 }
 @Test void explicitConsentIsRequiredOnceAndArchivedTextIsOwned()throws Exception {
  var c=createUser(Role.CUSTOMER);var outsider=createUser(Role.CUSTOMER);assertTrue(consent.needed(c,"BOOKING_POLICY"));assertThrows(RuntimeException.class,()->consent.accept(c,"BOOKING_POLICY","BOOKING",false));
  consent.accept(c,"BOOKING_POLICY","BOOKING",true);consent.accept(c,"BOOKING_POLICY","BOOKING",false);
  var rows=acceptances.findByUserOrderByAcceptedAtDesc(c);assertEquals(1,rows.size());assertFalse(consent.needed(c,"BOOKING_POLICY"));assertTrue(consent.accepted(rows.get(0).getId(),c).getContent().contains("Customer"));
  mvc.perform(get("/terms/accepted/{id}",rows.get(0).getId()).with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk());
  mvc.perform(get("/terms/accepted/{id}",rows.get(0).getId()).with(user(outsider.getEmail()).roles("CUSTOMER"))).andExpect(status().isNotFound());
 }
 @Test void providerPolicyCannotBeBypassedAtCreateEndpoint()throws Exception {
  var p=createUser(Role.PROVIDER);
  mvc.perform(post("/provider/services/new").with(csrf()).with(user(p.getEmail()).roles("PROVIDER")).param("name","New service").param("description","Actual service").param("price","100000").param("pricingType","FIXED"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/provider/services/new").with(csrf()).with(user(p.getEmail()).roles("PROVIDER")).param("acceptedPolicy","true").param("name","New service").param("description","Actual service").param("price","100000").param("pricingType","FIXED"))
   .andExpect(status().is3xxRedirection());assertFalse(consent.needed(p,"PROVIDER_POLICY"));
 }
 @Autowired BookingService bookingService;@Autowired ComplaintHandlingService complaintHandling;@Autowired PrivateFileStorage storage;
 @Test void bookingCannotBypassPolicyBeforeCreatingFinancialRecords(){
  var c=createUser(Role.CUSTOMER);var form=new com.skillrental.dto.BookingForm();form.setIdempotencyKey(UUID.randomUUID().toString());
  long before=orders.count();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->bookingService.create(c,1L,form));assertEquals(before,orders.count());assertTrue(consent.needed(c,"BOOKING_POLICY"));
 }
 @Test void materialUploadRequiresCsrfAndLimitsFileCount()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var o=order(c,p);
  mvc.perform(multipart("/orders/{id}/documents",o.getId()).file(file("brief.txt","ok")).param("description","actual brief").with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(multipart("/orders/{id}/documents",o.getId()).file(file("brief.txt","ok")).param("description","actual brief").with(csrf()).with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk());
  assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"too many",List.of(),java.util.stream.IntStream.range(0,6).<org.springframework.web.multipart.MultipartFile>mapToObj(i->file("a"+i+".txt","test")).toList()));
 }
 @Test void onlyOneAdminCanClaimASupportConversationConcurrently()throws Exception {
  var c=createUser(Role.CUSTOMER);var a=createUser(Role.ADMIN);var b=createUser(Role.ADMIN);var cv=support.openSupport(c,c);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try {var tasks=List.of(a,b).stream().map(admin->pool.submit(()->{start.await();try{chat.claimSupport(cv.getId(),admin);return true;}catch(IllegalStateException ex){return false;}})).toList();start.countDown();assertEquals(1,(tasks.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(tasks.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0));assertEquals(1,assignments.findByConversationIdOrderByCreatedAtAsc(cv.getId()).size());}finally{pool.shutdownNow();}
 }
 @Test void adminChatRateLimitDoesNotLoseTheSavedMessages(){
  var c=createUser(Role.CUSTOMER);var admin=createUser(Role.ADMIN);var cv=support.openSupport(c,c);chat.claimSupport(cv.getId(),admin);for(int i=0;i<40;i++)chat.send(conversations.findById(cv.getId()).orElseThrow(),admin,"Support reply "+i);
  assertThrows(IllegalArgumentException.class,()->chat.send(cv,admin,"too many"));assertEquals(40,messages.findByConversationOrderBySentAtAsc(cv).size());
 }
 @Test void supportSearchFiltersAndLinkedComplaintAreScoped(){
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var a=createUser(Role.ADMIN);var b=createUser(Role.ADMIN);var o=order(c,p);var cv=support.openSupport(c,c);chat.claimSupport(cv.getId(),a);
  var complaint=complaints.saveAndFlush(Complaint.builder().customer(c).reporter(c).order(o).handledBy(a).subject("Support case").description("Document issue").status(ComplaintStatus.PROCESSING).createdAt(LocalDateTime.now()).build());
  support.update(cv.getId(),a,"CLOSED",o.getId(),complaint.getId());assertEquals(1,support.search(a,c.getEmail(),"CLOSED",a.getId(),null,null).size());assertTrue(support.search(b,c.getEmail(),null,null,null,null).isEmpty());assertEquals(complaint.getId(),conversations.findById(cv.getId()).orElseThrow().getLinkedComplaint().getId());
 }
 @Test void independentApprovalAllowsAuditedFileExportButNotNormalPrivateChatAccess()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var a=createUser(Role.ADMIN);var b=approver();var o=order(c,p);var cv=support.openPrivate(c,p);
  var stored=privateFiles.store(file("evidence.txt","private evidence file"));
  var doc=documentRows.saveAndFlush(WorkDocument.builder().conversation(cv).sender(c).description("Evidence file").originalName(stored.name()).storageKey(stored.key()).fileSize(stored.size()).contentType(stored.type()).build());
  var complaint=complaints.saveAndFlush(Complaint.builder().customer(c).reporter(c).order(o).handledBy(a).subject("File evidence").description("Review document").status(ComplaintStatus.PROCESSING).createdAt(LocalDateTime.now()).build());
  var g=reviews.request(a,cv.getId(),complaint.getId(),"Inspect attachment",LocalDateTime.now().minusDays(1),LocalDateTime.now().plusSeconds(20));reviews.decide(g.getId(),b,true,true,"Allow evidence export");
  assertEquals(1,reviews.documents(g.getId(),a).size());
  mvc.perform(get("/documents/"+doc.getId()+"/download").with(user(a.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(get("/admin/support-reviews/{id}/documents/{doc}/download",g.getId(),doc.getId()).with(user(a.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().bytes("private evidence file".getBytes()));
  assertTrue(accesses.findByGrantIdOrderByIdAsc(g.getId()).stream().anyMatch(x->x.getAction().equals("DOWNLOAD")));
 }
 @Test void complaintResponseIsPersistedAndNotifiesOnlyReporter()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var a=createUser(Role.ADMIN);var o=order(c,p);var report=complaints.saveAndFlush(Complaint.builder().customer(c).reporter(c).order(o).subject("Question").description("Investigate this issue").status(ComplaintStatus.OPEN).createdAt(LocalDateTime.now()).build());
  complaintHandling.claim(report.getId(),a);complaintHandling.decide(report.getId(),a,ComplaintStatus.RESOLVED,"Reviewed the submitted evidence");
  assertEquals(a.getId(),complaints.findById(report.getId()).orElseThrow().getHandledBy().getId());
  var inbox=notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(c);assertEquals(1,inbox.size());assertEquals("/complaints/"+report.getId(),inbox.get(0).getTargetUrl());assertTrue(notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(p).isEmpty());
  mvc.perform(get("/complaints/{id}",report.getId()).with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Reviewed the submitted evidence")));
 }
 @Test void configuredVirusScannerMustSucceedBeforeSavingFiles()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var o=order(c,p);long before=fileCount();
  org.springframework.test.util.ReflectionTestUtils.setField(storage,"scannerRequired",true);org.springframework.test.util.ReflectionTestUtils.setField(storage,"scannerCommand","/nonexistent-workflow-scanner");
  try{assertThrows(RuntimeException.class,()->documents.upload(o.getId(),null,c,"Scan required",List.of(),List.of(file("safe.txt","text"))));assertEquals(before,fileCount());assertTrue(documents.list(o.getId(),null,c,0).isEmpty());}finally{org.springframework.test.util.ReflectionTestUtils.setField(storage,"scannerRequired",false);org.springframework.test.util.ReflectionTestUtils.setField(storage,"scannerCommand","clamdscan");}
 }

 @Test void longMessageKeepsFullHistoryAndStoresSafeNotificationPreview(){var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var cv=support.openPrivate(c,p);String text="a".repeat(2500);var saved=chat.send(cv,c,text);assertEquals(2500,messages.findById(saved.getId()).orElseThrow().getContent().length());assertEquals(1200,notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(p).get(0).getContent().length());}

 @Test void reviewDocumentHistoryHasAContinuationPage(){
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var a=createUser(Role.ADMIN);var b=approver();var o=order(c,p);var cv=support.openPrivate(c,p);
  for(int i=0;i<201;i++)documentRows.save(WorkDocument.builder().conversation(cv).sender(c).description("Evidence metadata "+i).build());
  var complaint=complaints.saveAndFlush(Complaint.builder().customer(c).reporter(c).order(o).handledBy(a).subject("Many documents").description("Review all history").status(ComplaintStatus.PROCESSING).createdAt(LocalDateTime.now()).build());
  var g=reviews.request(a,cv.getId(),complaint.getId(),"Full attachment history",LocalDateTime.now().minusDays(1),LocalDateTime.now().plusSeconds(20));reviews.decide(g.getId(),b,true,false,"Scope approved");var page=reviews.documents(g.getId(),a,0);assertEquals(200,page.size());assertEquals(1,reviews.documents(g.getId(),a,(Long)page.get(199).get("id")).size());
 }

 @Test void chatUploadsAreRejectedButTextMessagesRemainAvailable()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var cv=support.openPrivate(c,p);
  assertThrows(RuntimeException.class,()->documents.upload(null,cv.getId(),p,"Product through chat",List.of("https://example.test/product"),List.of()));
  assertTrue(documents.list(null,cv.getId(),c,0).isEmpty());
  chat.send(cv,p,"Please open your order to inspect the product.");
  assertEquals(1,messages.findByConversationOrderBySentAtAsc(cv).size());
  mvc.perform(multipart("/chat/{id}/documents",cv.getId()).file(file("product.txt","result")).with(csrf()).with(user(p.getEmail()).roles("PROVIDER")).param("description","Product"))
   .andExpect(status().isConflict());
 }
 @Test void progressIsAuthorizedDurableAndNotifiesCustomer()throws Exception {
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var outsider=createUser(Role.PROVIDER);var o=order(c,p);var eta=LocalDateTime.now().plusDays(2).withNano(0);
  assertThrows(IllegalArgumentException.class,()->work.update(o.getId(),c,10,"Customer cannot report provider work",eta));
  assertThrows(IllegalArgumentException.class,()->work.update(o.getId(),outsider,10,"Not my order",eta));
  assertThrows(IllegalArgumentException.class,()->work.update(o.getId(),p,100,"No product",eta));
  assertThrows(IllegalArgumentException.class,()->work.update(o.getId(),p,10,"Done",LocalDateTime.now().minusDays(1)));
  work.update(o.getId(),p,40,"Completed database and API implementation",eta);
  assertEquals(40,orders.findById(o.getId()).orElseThrow().getProgressPercent());
  assertEquals(eta,work.history(o.getId(),c).get(0).getEstimatedCompletionAt());
  assertTrue(notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(c).stream().anyMatch(n->"WORK_PROGRESS".equals(n.getType())));
  mvc.perform(get("/orders/{id}/details",o.getId()).with(user(c.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Completed database and API implementation")));
  var waiting=orders.findById(o.getId()).orElseThrow();waiting.setStatus(OrderStatus.PENDING_START_CONFIRMATION);orders.saveAndFlush(waiting);
  final Long id=waiting.getId();assertThrows(IllegalStateException.class,()->work.update(id,p,50,"Too soon",eta));
 }
 @Test void completionNeedsProductAndRevisionNeedsAnotherVersion(){
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);var o=order(c,p);
  assertThrows(IllegalStateException.class,()->escrow.providerComplete(o.getId(),p));
  assertThrows(IllegalArgumentException.class,()->deliveryService.submit(o.getId(),p,"Description alone",null,List.of(),List.of()));
  assertThrows(IllegalArgumentException.class,()->deliveryService.submit(o.getId(),p,"Unsafe result",null,List.of("javascript:alert(1)"),List.of()));
  assertThrows(IllegalArgumentException.class,()->deliveryService.submit(o.getId(),c,"Not the provider",null,List.of("https://example.test/result"),List.of()));
  var d=deliveryService.submit(o.getId(),p,"First product",null,List.of("https://example.test/product-v1"),List.of());
  var delivered=orders.findById(o.getId()).orElseThrow();assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,delivered.getStatus());assertEquals(100,delivered.getProgressPercent());assertNotNull(delivered.getDeliveredAt());assertNull(delivered.getCompletedAt());
  deliveryService.requestRevision(o.getId(),c,d.getId(),"Please revise the product");
  assertThrows(IllegalStateException.class,()->escrow.providerComplete(o.getId(),p));
  assertEquals(99,orders.findById(o.getId()).orElseThrow().getProgressPercent());
  var next=deliveryService.submit(o.getId(),p,"Revised product",null,List.of(),List.of(file("final.txt","final result")));
  assertEquals(2,next.getVersion());assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,orders.findById(o.getId()).orElseThrow().getStatus());
 }
 private ServiceCatalog scheduleService(User p,ServicePricingType pricing){
  if(providerProfiles.findByUser(p).isEmpty())providerProfiles.saveAndFlush(ProviderProfile.builder().user(p).headline("Provider").hourlyRate(new BigDecimal("10000")).rating(0.0).reviewCount(0).premium(false).approved(true).profileStatus("ACTIVE").build());
  return services.saveAndFlush(ServiceCatalog.builder().provider(p).name("Schedule test").description("Schedule test service").price(new BigDecimal("10000")).pricingType(pricing).status(ServiceStatus.PUBLISHED).build());
 }
 private com.skillrental.dto.BookingForm bookingForm(ServiceCatalog service){
  var f=new com.skillrental.dto.BookingForm();f.setServiceId(service.getId());f.setIdempotencyKey(UUID.randomUUID().toString());f.setAcceptedPolicy(true);return f;
 }
 private void fund(User c){var w=escrow.wallet(c);w.setBalance(new BigDecimal("1000000"));wallets.saveAndFlush(w);}
 @Test void deadlineBookingHasDueDateAndIdempotencyChecksSchedule(){
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);fund(c);var service=scheduleService(p,ServicePricingType.FIXED);var f=bookingForm(service);f.setScheduleMode(ScheduleMode.DEADLINE);f.setDeliveryDueAt(LocalDateTime.now().plusDays(3).withNano(0));
  var o=bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),f);var saved=orders.findById(o.getId()).orElseThrow();
  assertEquals(ScheduleMode.DEADLINE,saved.getBooking().getScheduleMode());assertEquals(f.getDeliveryDueAt(),saved.getBooking().getDeliveryDueAt());assertEquals(f.getDeliveryDueAt(),saved.getEstimatedCompletionAt());
  assertEquals(o.getId(),bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),f).getId());
  f.setDeliveryDueAt(f.getDeliveryDueAt().plusHours(1));assertThrows(BookingService.BookingUnavailableException.class,()->bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),f));
 }
 @Test void appointmentUsesActualDurationAndHourlyServiceRejectsDeadline(){
  var c=createUser(Role.CUSTOMER);var p=createUser(Role.PROVIDER);fund(c);var service=scheduleService(p,ServicePricingType.HOURLY);var f=bookingForm(service);f.setBookingDate(LocalDate.now().plusDays(2));f.setTimeSlot(LocalTime.of(10,0));f.setDurationHours(3);
  var o=bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),f);
  assertEquals(LocalDateTime.of(f.getBookingDate(),LocalTime.of(13,0)),o.getBooking().getDeliveryDueAt());assertEquals(0,new BigDecimal("30000").compareTo(o.getTotal()));
  var invalid=bookingForm(service);invalid.setScheduleMode(ScheduleMode.DEADLINE);invalid.setDeliveryDueAt(LocalDateTime.now().plusDays(3));assertThrows(IllegalArgumentException.class,()->bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),invalid));
  invalid.setServiceId(scheduleService(p,ServicePricingType.FIXED).getId());invalid.setDeliveryDueAt(LocalDateTime.now().minusDays(1));assertThrows(IllegalArgumentException.class,()->bookingService.create(c,providerProfiles.findByUser(p).orElseThrow().getId(),invalid));
 }

}
