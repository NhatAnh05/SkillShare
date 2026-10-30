package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class RegisterRequest {
    @NotBlank @Size(max=120) private String fullName;
    @NotBlank @Email @Size(max=160) private String email;
    @NotBlank @Size(min=8,max=72) private String password;
    @NotBlank private String confirmPassword;
    @AssertTrue(message="Bạn phải đồng ý điều khoản") private boolean acceptedTerms;
}
