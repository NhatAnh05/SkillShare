package com.skillrental.service;

import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.Role;
import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminCatalogManagementService {
    private final OfferedServiceRepository services;
    private final ProviderProfileRepository providers;
    private final ProviderEligibilityService eligibility;
    private final NotificationService notifications;

    @Transactional
    public void approveService(Long serviceId) {
        ServiceCatalog service = services.findById(serviceId)
                .orElseThrow(() -> new CatalogManagementException("Không tìm thấy dịch vụ."));
        if (service.getStatus() != ServiceStatus.DRAFT) {
            throw new CatalogManagementException("Chỉ có thể duyệt dịch vụ đang ở trạng thái nháp.");
        }
        ProviderProfile provider = providers.findByUser(service.getProvider())
                .orElseThrow(() -> new CatalogManagementException("Provider của dịch vụ chưa có hồ sơ."));
        if (!eligibility.isEligible(provider)) {
            throw new CatalogManagementException("Chỉ có thể duyệt dịch vụ của Provider đã được duyệt và đang hoạt động.");
        }
        service.setStatus(ServiceStatus.PUBLISHED);
        service.setPublishedAt(LocalDateTime.now());
        services.save(service);
        notifications.create(service.getProvider(), "SERVICE_APPROVED", "Dịch vụ đã được duyệt",
                "Dịch vụ \"" + service.getName() + "\" đã được Admin phê duyệt và xuất bản.",
                "SERVICE", service.getId(), "/provider/services");
    }

    @Transactional
    public void approveProvider(Long providerId) {
        ProviderProfile provider = findProvider(providerId);
        if (provider.getUser().getRole() != Role.PROVIDER) {
            throw new CatalogManagementException("Hồ sơ không thuộc tài khoản Provider.");
        }
        if (Boolean.TRUE.equals(provider.getApproved())) {
            throw new CatalogManagementException("Provider này đã được duyệt.");
        }
        if (!"ACTIVE".equals(provider.getProfileStatus())
                || provider.getUser().getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(provider.getUser().getEmailVerified())) {
            throw new CatalogManagementException("Không thể duyệt hồ sơ Provider hiện không hoạt động.");
        }
        provider.setApproved(true);
        providers.save(provider);
        notifications.create(provider.getUser(), "PROVIDER_APPROVED", "Hồ sơ Provider đã được duyệt",
                "Admin đã phê duyệt hồ sơ Provider của bạn.",
                "PROVIDER", provider.getId(), "/provider/dashboard");
    }

    @Transactional
    public void suspendProvider(Long providerId) {
        ProviderProfile provider = findProvider(providerId);
        if (!Boolean.TRUE.equals(provider.getApproved())
                || !"ACTIVE".equals(provider.getProfileStatus())) {
            throw new CatalogManagementException("Chỉ có thể tạm ngưng Provider đã duyệt và đang hoạt động.");
        }
        provider.setProfileStatus("SUSPENDED");
        providers.save(provider);
        notifications.create(provider.getUser(), "PROVIDER_SUSPENDED", "Hồ sơ Provider tạm ngưng",
                "Admin đã tạm ngưng hồ sơ Provider của bạn.",
                "PROVIDER", provider.getId(), "/provider/dashboard");
    }

    @Transactional
    public void reactivateProvider(Long providerId) {
        ProviderProfile provider = findProvider(providerId);
        if (!Boolean.TRUE.equals(provider.getApproved())
                || !"SUSPENDED".equals(provider.getProfileStatus())) {
            throw new CatalogManagementException("Chỉ có thể kích hoạt lại Provider đã bị tạm ngưng.");
        }
        provider.setProfileStatus("ACTIVE");
        providers.save(provider);
        notifications.create(provider.getUser(), "PROVIDER_REACTIVATED", "Hồ sơ Provider được kích hoạt",
                "Admin đã kích hoạt lại hồ sơ Provider của bạn.",
                "PROVIDER", provider.getId(), "/provider/dashboard");
    }

    private ProviderProfile findProvider(Long providerId) {
        return providers.findById(providerId)
                .orElseThrow(() -> new CatalogManagementException("Không tìm thấy hồ sơ Provider."));
    }

    public static class CatalogManagementException extends RuntimeException {
        public CatalogManagementException(String message) {
            super(message);
        }
    }
}
