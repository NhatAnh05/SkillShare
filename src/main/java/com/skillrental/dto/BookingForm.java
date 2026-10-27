package com.skillrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;

@Data public class BookingForm {
    @NotNull private Long serviceId;
    private Long cartItemId;
    @NotBlank @Size(max=36) private String idempotencyKey;
    @NotNull @FutureOrPresent @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate bookingDate;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.TIME) private LocalTime timeSlot;
    @Size(max=1200) private String note;
    @jakarta.validation.constraints.Min(2) @jakarta.validation.constraints.Max(2) private int durationHours = 2;
    private boolean premium;
    private boolean directBooking=true;
}
