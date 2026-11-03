package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data public class MessageForm {
    @Size(max=36) private String clientKey;
    @NotBlank @Size(max=2500) private String content;
}
