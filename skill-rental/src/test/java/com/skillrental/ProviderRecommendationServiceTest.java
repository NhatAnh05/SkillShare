package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.ReviewRepository;
import com.skillrental.service.ProviderEligibilityService;
import com.skillrental.service.ProviderRecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderRecommendationServiceTest {
    @Mock private ProviderProfileRepository providers;
    @Mock private RentalOrderRepository orders;
    @Mock private ReviewRepository reviews;
    @Mock private ProviderEligibilityService eligibility;
    @InjectMocks private ProviderRecommendationService recommendations;

    @Test
    void higherReviewAndCompletionScoreOutranksVolumeWhileCappingBookingContribution() {
        ProviderProfile strong = provider("strong", true, true);
        ProviderProfile weak = provider("weak", true, true);
        List<RentalOrder> persistedOrders = new ArrayList<>();
        List<Review> persistedReviews = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            OrderStatus status = id <= 8 ? OrderStatus.COMPLETED : OrderStatus.PROCESSING;
            RentalOrder order = order(id, strong.getUser(), status);
            persistedOrders.add(order);
            if (id <= 8) persistedReviews.add(review(strong.getUser(), order, 5));
        }
        persistedOrders.add(order(11, strong.getUser(), OrderStatus.CANCELLED));
        persistedOrders.add(order(12, strong.getUser(), OrderStatus.PENDING_PAYMENT));
        RentalOrder weakOrder = order(20, weak.getUser(), OrderStatus.COMPLETED);
        persistedOrders.add(weakOrder);
        persistedReviews.add(review(weak.getUser(), weakOrder, 4));
        when(providers.findAll()).thenReturn(List.of(weak, strong));
        when(orders.findAll()).thenReturn(persistedOrders);
        when(reviews.findAll()).thenReturn(persistedReviews);
        when(eligibility.isEligible(strong)).thenReturn(true);
        when(eligibility.isEligible(weak)).thenReturn(true);

        var result = recommendations.recommend(List.of(), 10);

        assertEquals("strong", result.get(0).provider().getUser().getEmail());
        assertEquals(94.0, result.get(0).score(), 0.001);
        assertEquals(80.0, result.get(0).completionRate(), 0.001);
    }

    @Test
    void excludesUnavailableProvidersAndGivesNewProviderNeutralRatingAndCompletionScores() {
        ProviderProfile fresh = provider("fresh", true, true);
        ProviderProfile locked = provider("locked", true, false);
        ProviderProfile pendingApproval = provider("pending", false, true);
        when(providers.findAll()).thenReturn(List.of(fresh, locked, pendingApproval));
        when(orders.findAll()).thenReturn(List.of(order(30, locked.getUser(), OrderStatus.COMPLETED)));
        when(reviews.findAll()).thenReturn(List.of());
        when(eligibility.isEligible(fresh)).thenReturn(true);
        when(eligibility.isEligible(locked)).thenReturn(false);
        when(eligibility.isEligible(pendingApproval)).thenReturn(false);

        var result = recommendations.recommend(List.of("Java"), 10);

        assertEquals(1, result.size());
        assertEquals("fresh", result.get(0).provider().getUser().getEmail());
        assertEquals(0, result.get(0).validBookingCount());
        assertEquals(50.0, result.get(0).completionRate(), 0.001);
        assertEquals(37.5, result.get(0).score(), 0.001);
    }

    private ProviderProfile provider(String name, boolean approved, boolean active) {
        User user = User.builder().id((long) name.hashCode()).fullName(name).email(name)
                .role(Role.PROVIDER).emailVerified(true)
                .accountStatus(active ? AccountStatus.ACTIVE : AccountStatus.LOCKED).build();
        return ProviderProfile.builder().id((long) name.hashCode()).user(user).approved(approved)
                .profileStatus("ACTIVE").skills(new LinkedHashSet<>()).build();
    }

    private RentalOrder order(long id, User provider, OrderStatus status) {
        return RentalOrder.builder().id(id).provider(provider).status(status).build();
    }

    private Review review(User provider, RentalOrder order, int rating) {
        return Review.builder().id(order.getId()).provider(provider).order(order).rating(rating).build();
    }
}
