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
