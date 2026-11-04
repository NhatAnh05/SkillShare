package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class ComplaintForm {
    @NotBlank private String subject;
    @NotBlank @Size(max=2500) private String description;
}
