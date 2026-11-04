package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class ProfileForm {
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @Size(max=20) private String phone;
    @Size(max=500) private String avatarUrl;
    @Size(max=1200) private String bio;
}
