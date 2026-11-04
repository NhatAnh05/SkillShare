package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProviderRecommendationService {
    private static final Set<OrderStatus> VALID_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PAID,
            OrderStatus.PENDING_PROVIDER_ACCEPTANCE,
            OrderStatus.PENDING_START_CONFIRMATION,
            OrderStatus.PROCESSING,
            OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,
            OrderStatus.PENDING_PAYMENT_OTP,
            OrderStatus.COMPLETED);
    private static final double NEW_PROVIDER_NEUTRAL_SCORE = 50.0;
    private static final int BOOKING_COUNT_FOR_FULL_VOLUME_SCORE = 10;

    private final ProviderProfileRepository providers;
    private final RentalOrderRepository orders;
    private final ReviewRepository reviews;
    private final ProviderEligibilityService eligibility;

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Collection<String> preferredSkills, int limit) {
        Set<String> normalizedSkills = preferredSkills == null ? Set.of() : preferredSkills.stream()
                .filter(Objects::nonNull).map(String::strip).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());

        Map<Long, List<RentalOrder>> validOrders = new HashMap<>();
        for (RentalOrder order : orders.findAll()) {
            if (order.getProvider() != null && VALID_ORDER_STATUSES.contains(order.getStatus())) {
                validOrders.computeIfAbsent(order.getProvider().getId(), ignored -> new ArrayList<>()).add(order);
            }
        }
        Map<Long, List<Review>> completedReviews = new HashMap<>();
        for (Review review : reviews.findAll()) {
            if (review.getProvider() != null && review.getOrder() != null
                    && review.getOrder().getStatus() == OrderStatus.COMPLETED
                    && review.getOrder().getProvider() != null
                    && review.getOrder().getProvider().getId().equals(review.getProvider().getId())
                    && review.getRating() != null && review.getRating() >= 1 && review.getRating() <= 5) {
                completedReviews.computeIfAbsent(review.getProvider().getId(), ignored -> new ArrayList<>()).add(review);
            }
        }

        return providers.findAll().stream()
                .filter(eligibility::isEligible)
                .map(profile -> score(profile, validOrders.getOrDefault(profile.getUser().getId(), List.of()),
                        completedReviews.getOrDefault(profile.getUser().getId(), List.of()), normalizedSkills))
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed()
                        .thenComparing(Comparator.comparingInt(Recommendation::matchedSkills).reversed())
                        .thenComparing(row -> row.provider().getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(0, limit))
                .toList();
    }

    private Recommendation score(ProviderProfile profile, List<RentalOrder> providerOrders,
                                 List<Review> providerReviews, Set<String> preferredSkills) {
        long completed = providerOrders.stream().filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(RentalOrder::getId).filter(Objects::nonNull).distinct().count();
        long validBookingCount = providerOrders.stream().map(RentalOrder::getId)
                .filter(Objects::nonNull).distinct().count();

        double ratingScore = providerReviews.isEmpty() ? NEW_PROVIDER_NEUTRAL_SCORE
                : providerReviews.stream().mapToInt(Review::getRating).average().orElse(0.0) * 20.0;
        double bookingScore = Math.min(100.0,
                validBookingCount * 100.0 / BOOKING_COUNT_FOR_FULL_VOLUME_SCORE);
        double completionRate = validBookingCount == 0 ? NEW_PROVIDER_NEUTRAL_SCORE
                : completed * 100.0 / validBookingCount;
        double score = ratingScore * 0.45 + bookingScore * 0.25 + completionRate * 0.30;
        int matchedSkills = (int) profile.getSkills().stream()
                .filter(skill -> preferredSkills.contains(skill.getName().toLowerCase(Locale.ROOT))).count();
        return new Recommendation(profile, Math.round(score * 10.0) / 10.0, validBookingCount,
                Math.round(completionRate * 10.0) / 10.0, matchedSkills);
    }

    public record Recommendation(ProviderProfile provider, double score, long validBookingCount,
                                 double completionRate, int matchedSkills) {}
}
