package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data public class ServiceRequestForm {
    @NotBlank private String title;
    @NotBlank private String requiredSkills;
    @NotBlank @Size(max=2500) private String description;
    @NotNull @DecimalMin("1") private BigDecimal budget;
    @NotNull @Future @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate deadline;
}
