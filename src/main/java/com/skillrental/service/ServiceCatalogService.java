package com.skillrental.service;

import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.entity.User;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {
    private final OfferedServiceRepository services;
    private final ProviderProfileRepository providers;
    private final ProviderEligibilityService eligibility;

    @Transactional(readOnly = true)
    public List<ServiceCatalog> availablePublishedServices() {
        return services.findByStatusOrderByRatingDesc(ServiceStatus.PUBLISHED).stream()
                .filter(service -> providerIsEligible(service.getProvider()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalog> availablePublishedServices(User provider) {
        if (!providerIsEligible(provider)) {
            return List.of();
        }
        return services.findByProviderAndStatusOrderByCreatedAtDesc(provider, ServiceStatus.PUBLISHED);
    }

    @Transactional(readOnly = true, noRollbackFor = ServiceUnavailableException.class)
    public ServiceCatalog requireAvailablePublishedService(Long serviceId) {
        ServiceCatalog service = services.findById(serviceId)
                .orElseThrow(() -> new ServiceUnavailableException("Không tìm thấy dịch vụ."));
        if (service.getStatus() != ServiceStatus.PUBLISHED || !providerIsEligible(service.getProvider())) {
            throw new ServiceUnavailableException("Dịch vụ hiện không được mở bán.");
        }
        return service;
    }

    @Transactional(readOnly = true)
    public ServiceCatalog requireAvailablePublishedService(Long serviceId, ProviderProfile expectedProvider) {
        ServiceCatalog service = requireAvailablePublishedService(serviceId);
        if (!service.getProvider().getId().equals(expectedProvider.getUser().getId())) {
            throw new ServiceUnavailableException("Dịch vụ không thuộc Provider đã chọn.");
        }
        return service;
    }

    @Transactional(readOnly = true)
    public boolean providerIsEligible(User provider) {
        return provider != null
                && providers.findByUser(provider).filter(eligibility::isEligible).isPresent();
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
