package com.skillrental.service;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.Role;
import org.springframework.stereotype.Service;

@Service
public class ProviderEligibilityService {
    public boolean isEligible(ProviderProfile profile) {
        return profile != null
                && Boolean.TRUE.equals(profile.getApproved())
                && "ACTIVE".equals(profile.getProfileStatus())
                && profile.getUser() != null
                && profile.getUser().getRole() == Role.PROVIDER
                && profile.getUser().getAccountStatus() == AccountStatus.ACTIVE
                && Boolean.TRUE.equals(profile.getUser().getEmailVerified());
    }
}
