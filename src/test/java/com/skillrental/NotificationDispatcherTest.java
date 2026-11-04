package com.skillrental;

import com.skillrental.entity.NotificationCampaignRecipient;
import com.skillrental.notification.NotificationCampaignQueuedEvent;
import com.skillrental.repository.NotificationCampaignRecipientRepository;
import com.skillrental.service.NotificationCampaignDeliveryService;
import com.skillrental.service.NotificationCampaignDispatcher;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class NotificationDispatcherTest {
    @Test
    void aRecipientAndFailureLogErrorDoNotPreventNextRecipient() {
        var recipients = mock(NotificationCampaignRecipientRepository.class);
        var delivery = mock(NotificationCampaignDeliveryService.class);
        var first = NotificationCampaignRecipient.builder().id(1L).build();
        var second = NotificationCampaignRecipient.builder().id(2L).build();
        when(recipients.findTop100ByCampaignIdAndDeliveryStatusAndIdGreaterThanOrderByIdAsc(7L, "QUEUED", 0L))
                .thenReturn(List.of(first, second));
        when(recipients.findTop100ByCampaignIdAndDeliveryStatusAndIdGreaterThanOrderByIdAsc(7L, "QUEUED", 2L))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("database error")).when(delivery).deliverQueued(1L);
        doThrow(new IllegalStateException("database still unavailable")).when(delivery).recordCreationFailure(eq(1L), anyString());
        new NotificationCampaignDispatcher(recipients, delivery).dispatch(new NotificationCampaignQueuedEvent(7L));
        verify(delivery).deliverQueued(2L);
        verify(recipients).findTop100ByCampaignIdAndDeliveryStatusAndIdGreaterThanOrderByIdAsc(7L, "QUEUED", 2L);
    }
}
