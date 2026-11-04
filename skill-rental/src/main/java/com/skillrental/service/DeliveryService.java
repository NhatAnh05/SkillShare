package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 5;
    private static final Pattern MIME_TYPE = Pattern.compile("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");
    private final RentalOrderRepository orders;
    private final DeliverySubmissionRepository deliveries;
    private final DeliveryFileRepository files;
    private final DeliveryRevisionRequestRepository revisions;
    private final EscrowService escrow;
    private final OrderService orderAccess;
    private final NotificationService notifications;
    private final PrivateFileStorage privateStorage;
    @Value("${skillshare.delivery.storage-dir:./private-deliveries}")
    private String storageDirectory;

    @Transactional
    public DeliverySubmission submit(Long orderId, User provider, String description, String usageNotes,
                                     List<String> suppliedLinks, List<MultipartFile> suppliedFiles) {
        RentalOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (provider.getRole() != Role.PROVIDER || order.getProvider() == null
                || !order.getProvider().getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Chỉ Provider được giao đơn mới có thể gửi bàn giao.");
        }
        if (order.getStatus() != OrderStatus.PROCESSING
                || !Boolean.TRUE.equals(order.getProviderStarted())
                || !Boolean.TRUE.equals(order.getCustomerStarted())) {
            throw new IllegalStateException("Chỉ đơn đang thực hiện sau khi hai bên xác nhận bắt đầu mới được bàn giao.");
        }
        String cleanedDescription = requiredText(description, 4000, "Mô tả bàn giao");
        String cleanedNotes = optionalText(usageNotes, 4000);
        List<String> links = validateLinks(suppliedLinks);
        List<MultipartFile> uploadList = suppliedFiles == null ? List.of()
                : suppliedFiles.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (uploadList.isEmpty() && links.isEmpty()) throw new IllegalArgumentException("Cần tải ít nhất một tệp sản phẩm hoặc cung cấp liên kết kết quả trước khi hoàn thành.");
        if (uploadList.size() > MAX_FILE_COUNT) throw new IllegalArgumentException("Mỗi lần bàn giao tối đa 5 tệp.");
        int version = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId)
                .map(item -> item.getVersion() + 1).orElse(1);
        DeliverySubmission submission = deliveries.save(DeliverySubmission.builder()
                .order(order).provider(provider).version(version).description(cleanedDescription)
                .usageNotes(cleanedNotes).status("SUBMITTED").build());

        Path root = Path.of(storageDirectory).toAbsolutePath().normalize();
        List<Path> createdFiles = new ArrayList<>();
        try {
            if (!uploadList.isEmpty()) Files.createDirectories(root);
            for (MultipartFile upload : uploadList) {
                var stored=privateStorage.store(upload,storageDirectory);
                createdFiles.add(root.resolve(stored.key()));
                submission.getFiles().add(DeliveryFile.builder().delivery(submission).originalName(stored.name())
                        .storageKey(stored.key()).contentType(stored.type()).fileSize(stored.size()).build());
            }
            links.forEach(url -> submission.getLinks().add(DeliveryLink.builder()
                    .delivery(submission).url(url).label(null).build()));
            revisions.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                    .filter(revision -> "OPEN".equals(revision.getStatus()))
                    .forEach(revision -> revision.setStatus("RESOLVED"));
            deliveries.saveAndFlush(submission);
            escrow.providerComplete(orderId, provider);
            notifications.create(order.getCustomer(), "DELIVERY_SUBMITTED", "Provider đã gửi sản phẩm bàn giao",
                    "Đơn hàng #" + orderId + " có bản bàn giao phiên bản " + version + ".",
                    "ORDER", orderId, "/orders/" + orderId + "/progress");
            return submission;
        } catch (IOException | RuntimeException exception) {
            for (Path path : createdFiles) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Không thể lưu tệp bàn giao.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DeliverySubmission> history(Long orderId, User viewer) {
        orderAccess.involved(orderId, viewer);
        return deliveries.findByOrderIdOrderByVersionDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryRevisionRequest> revisionHistory(Long orderId, User viewer) {
        orderAccess.involved(orderId, viewer);
        return revisions.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional
    public DeliveryRevisionRequest requestRevision(Long orderId, User customer, Long deliveryId, String reason) {
        RentalOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (customer == null || customer.getRole() != Role.CUSTOMER || customer.getId() == null
                || !order.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Chỉ Customer của đơn mới được yêu cầu chỉnh sửa.");
        }
        if (order.getStatus() != OrderStatus.PENDING_CUSTOMER_ACCEPTANCE) {
            throw new IllegalStateException("Đơn hiện không chờ nghiệm thu.");
        }
        DeliverySubmission delivery = deliveries.findByIdAndOrderId(deliveryId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản bàn giao của đơn hàng."));
        DeliverySubmission latest = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId).orElseThrow();
        if (!latest.getId().equals(delivery.getId()) || !"SUBMITTED".equals(delivery.getStatus()))
            throw new IllegalStateException("Chỉ yêu cầu chỉnh sửa bản bàn giao mới nhất đang chờ nghiệm thu.");
        String cleaned = requiredText(reason, 2000, "Nội dung yêu cầu chỉnh sửa");
        escrow.requestDeliveryRevision(orderId, customer);
        delivery.setStatus("REVISION_REQUESTED");
        revisions.save(DeliveryRevisionRequest.builder().order(order).delivery(delivery)
                .customer(customer).reason(cleaned).status("OPEN").build());
        notifications.create(order.getProvider(), "DELIVERY_REVISION", "Customer yêu cầu chỉnh sửa",
                "Customer đã yêu cầu chỉnh sửa sản phẩm bàn giao cho đơn hàng #" + orderId + ".",
                "ORDER", orderId, "/provider/bookings");
        List<DeliveryRevisionRequest> orderRevisions = revisions.findByOrderIdOrderByCreatedAtAsc(orderId);
        return orderRevisions.get(orderRevisions.size() - 1);
    }

    @Transactional
    public EscrowService.OtpDispatchResult acceptLatest(Long orderId, User customer) {
        RentalOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (customer == null || customer.getRole() != Role.CUSTOMER || customer.getId() == null
                || !order.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Chỉ Customer của đơn mới được nghiệm thu.");
        }
        if (order.getStatus() != OrderStatus.PENDING_CUSTOMER_ACCEPTANCE
                || !Boolean.TRUE.equals(order.getProviderCompleted())) {
            throw new IllegalStateException("Provider chưa bàn giao hoặc đơn hiện không chờ nghiệm thu.");
        }
        DeliverySubmission latest = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Chưa có sản phẩm bàn giao để nghiệm thu."));
        if (!"SUBMITTED".equals(latest.getStatus())
                || (latest.getFiles().isEmpty() && latest.getLinks().isEmpty())) {
            throw new IllegalStateException("Bản bàn giao mới nhất không hợp lệ hoặc đã được xử lý.");
        }
        EscrowService.OtpDispatchResult result = escrow.customerAcceptCompletion(orderId, customer);
        latest.setStatus("ACCEPTED");
        deliveries.save(latest);
        return result;
    }

    @Transactional(readOnly = true)
    public DownloadableFile download(Long orderId, Long deliveryId, Long fileId, User viewer) {
        orderAccess.involved(orderId, viewer);
        DeliveryFile file = files.findByIdAndDeliveryId(fileId, deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tệp bàn giao."));
        if (!file.getDelivery().getOrder().getId().equals(orderId)) {
            throw new IllegalArgumentException("Tệp không thuộc đơn hàng này.");
        }
        Path path = Path.of(storageDirectory).toAbsolutePath().normalize().resolve(file.getStorageKey()).normalize();
        if (!path.getParent().equals(Path.of(storageDirectory).toAbsolutePath().normalize()) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("Tệp bàn giao không còn khả dụng.");
        }
        return new DownloadableFile(file, new FileSystemResource(path));
    }

    private List<String> validateLinks(List<String> supplied) {
        if (supplied == null) return List.of();
        List<String> links = supplied.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).toList();
        if (links.size() > 10) throw new IllegalArgumentException("Mỗi lần bàn giao tối đa 10 liên kết.");
        for (String link : links) {
            if (link.length() > 1000) throw new IllegalArgumentException("Liên kết quá dài.");
            try {
                URI uri = URI.create(link);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("Liên kết bàn giao phải là URL http/https hợp lệ.");
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Liên kết bàn giao phải là URL http/https hợp lệ.", ex);
            }
        }
        return links;
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || cleaned.length() > maxLength) {
            throw new IllegalArgumentException(field + " không được để trống và tối đa " + maxLength + " ký tự.");
        }
        return cleaned;
    }

    private String optionalText(String value, int maxLength) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > maxLength) throw new IllegalArgumentException("Ghi chú sử dụng quá dài.");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public record DownloadableFile(DeliveryFile metadata, Resource resource) {}
}
