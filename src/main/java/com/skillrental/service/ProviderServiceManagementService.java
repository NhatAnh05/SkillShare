package com.skillrental.service;

import com.skillrental.entity.Role;
import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServicePricingType;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.entity.User;
import com.skillrental.repository.OfferedServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProviderServiceManagementService {
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    private final OfferedServiceRepository services;

    @Transactional
    public ServiceCatalog create(User provider, ServiceDetails details) {
        requireProvider(provider);
        ServiceCatalog service = ServiceCatalog.builder()
                .provider(provider)
                .status(ServiceStatus.DRAFT)
                .build();
        applyDetails(service, details);
        return services.saveAndFlush(service);
    }

    @Transactional
    public ServiceCatalog update(Long serviceId, User provider, ServiceDetails details) {
        requireProvider(provider);
        ServiceCatalog service = requireOwnedService(serviceId, provider);
        if (service.getStatus() == ServiceStatus.ARCHIVED) {
            throw new ServiceManagementException("Không thể chỉnh sửa dịch vụ đã lưu trữ.");
        }
        applyDetails(service, details);
        return services.saveAndFlush(service);
    }

    @Transactional
    public void pause(Long serviceId, User provider) {
        ServiceCatalog service = requireOwnedService(serviceId, provider);
        if (service.getStatus() != ServiceStatus.PUBLISHED) {
            throw new ServiceManagementException("Chỉ có thể tạm ngừng dịch vụ đang được xuất bản.");
        }
        service.setStatus(ServiceStatus.PAUSED);
        services.saveAndFlush(service);
    }

    @Transactional
    public void resume(Long serviceId, User provider) {
        ServiceCatalog service = requireOwnedService(serviceId, provider);
        if (service.getStatus() != ServiceStatus.PAUSED) {
            throw new ServiceManagementException("Chỉ có thể xuất bản lại dịch vụ đang tạm ngừng.");
        }
        service.setStatus(ServiceStatus.PUBLISHED);
        services.saveAndFlush(service);
    }

    @Transactional
    public void archive(Long serviceId, User provider) {
        ServiceCatalog service = requireOwnedService(serviceId, provider);
        if (service.getStatus() == ServiceStatus.ARCHIVED) {
            throw new ServiceManagementException("Dịch vụ đã được lưu trữ.");
        }
        service.setStatus(ServiceStatus.ARCHIVED);
        services.saveAndFlush(service);
    }

    private ServiceCatalog requireOwnedService(Long serviceId, User provider) {
        ServiceCatalog service = services.findById(serviceId)
                .orElseThrow(() -> new ServiceManagementException("Không tìm thấy dịch vụ."));
        if (!service.getProvider().getId().equals(provider.getId())) {
            throw new ServiceManagementException("Bạn không có quyền quản lý dịch vụ này.");
        }
        return service;
    }

    private void requireProvider(User provider) {
        if (provider == null || provider.getRole() != Role.PROVIDER) {
            throw new ServiceManagementException("Chỉ Provider mới được quản lý dịch vụ.");
        }
    }

    private void applyDetails(ServiceCatalog service, ServiceDetails details) {
        if (details == null) {
            throw new ServiceManagementException("Thông tin dịch vụ không hợp lệ.");
        }
        String name = details.name() == null ? "" : details.name().trim();
        String category = details.category() == null ? "" : details.category().trim();
        String description = details.description() == null ? "" : details.description().trim();
        String coverImageUrl = details.coverImageUrl() == null ? "" : details.coverImageUrl().trim();

        if (name.isEmpty() || name.length() > 160) {
            throw new ServiceManagementException("Tên dịch vụ là bắt buộc và tối đa 160 ký tự.");
        }
        if (category.length() > 80) {
            throw new ServiceManagementException("Danh mục không được vượt quá 80 ký tự.");
        }
        if (description.isEmpty() || description.length() > 2500) {
            throw new ServiceManagementException("Mô tả là bắt buộc và tối đa 2500 ký tự.");
        }
        if (details.price() == null || details.price().signum() <= 0
                || details.price().compareTo(MAX_PRICE) > 0
                || details.price().stripTrailingZeros().scale() > 2) {
            throw new ServiceManagementException("Giá phải lớn hơn 0 và có tối đa 2 chữ số thập phân.");
        }
        if (details.durationMinutes() != null && details.durationMinutes() <= 0) {
            throw new ServiceManagementException("Thời lượng phải lớn hơn 0 phút.");
        }
        if (coverImageUrl.length() > 500) {
            throw new ServiceManagementException("Đường dẫn ảnh không được vượt quá 500 ký tự.");
        }

        ServicePricingType pricingType;
        try {
            pricingType = ServicePricingType.valueOf(
                    details.pricingType() == null ? "" : details.pricingType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ServiceManagementException("Loại tính giá không hợp lệ.");
        }

        service.setName(name);
        service.setCategory(category.isEmpty() ? null : category);
        service.setDescription(description);
        service.setPricingType(pricingType);
        service.setPrice(details.price());
        service.setDurationMinutes(details.durationMinutes());
        service.setCoverImageUrl(coverImageUrl.isEmpty() ? null : coverImageUrl);
    }

    public record ServiceDetails(String name, String category, String description, String pricingType,
                                 BigDecimal price, Integer durationMinutes, String coverImageUrl) {
    }

    public static class ServiceManagementException extends RuntimeException {
        public ServiceManagementException(String message) {
            super(message);
        }
    }
}
