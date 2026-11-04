package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class ReviewForm {
    @NotNull @Min(1) @Max(5) private Integer rating;
    @NotBlank @Size(max=2000) private String comment;
}
