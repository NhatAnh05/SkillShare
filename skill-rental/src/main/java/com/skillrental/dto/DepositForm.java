package com.skillrental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data public class DepositForm {
    @NotNull @DecimalMin(value = "1000", message = "Số tiền nạp tối thiểu là 1.000đ")
    private BigDecimal amount;
    @NotBlank(message = "Vui lòng chọn phương thức nạp")
    private String method;
}
