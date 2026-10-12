package com.skillrental.service;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor public class BookingService {
    private final ProviderProfileRepository providers;
    private final BookingRepository bookings;
    private final RentalOrderRepository orders;
    @Transactional public RentalOrder create(User customer,Long providerId,BookingForm f) {
        var p=providers.findById(providerId).orElseThrow();
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            throw new IllegalArgumentException("Khung giờ đã qua");
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10,0), java.time.LocalTime.of(12,0),
                java.time.LocalTime.of(18,0), java.time.LocalTime.of(20,0)).contains(f.getTimeSlot())) {
            throw new IllegalArgumentException("Khung giờ không hợp lệ");
        }
        BigDecimal rate=p.getHourlyRate().multiply(BigDecimal.valueOf(f.getDurationHours()));
        if(f.isPremium()) rate=rate.multiply(new BigDecimal("1.25"));
        var b=bookings.save(Booking.builder().customer(customer).provider(p.getUser()).serviceName(f.getServiceName()).bookingDate(f.getBookingDate()).timeSlot(f.getTimeSlot()).note(f.getNote()).premium(f.isPremium()).directBooking(f.isDirectBooking()).amount(rate).status(BookingStatus.PENDING).build());
        return orders.save(RentalOrder.builder().booking(b).customer(customer).provider(p.getUser()).subtotal(rate).discount(BigDecimal.ZERO).total(rate).status(OrderStatus.PENDING_PAYMENT).createdAt(LocalDateTime.now()).build());
    }
}
