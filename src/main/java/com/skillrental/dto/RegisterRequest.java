package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class RegisterRequest {
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @NotBlank @Size(min=6,max=72) private String password;
    @NotBlank private String confirmPassword;
    @AssertTrue(message="Bạn phải đồng ý điều khoản") private boolean acceptedTerms;
}
