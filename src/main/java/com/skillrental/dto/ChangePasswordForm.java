package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class ChangePasswordForm {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min=6,max=72) private String newPassword;
    @NotBlank private String confirmPassword;
}
