package com.example.tdd.feature;

import com.example.tdd.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureService")
class FeatureServiceTest {

    @Mock FeatureService.ArchiveClient archive;
    @Mock FeatureService.NotificationClient notifications;
    @InjectMocks FeatureService service;

    @BeforeEach
    void injectFlags() {
        ReflectionTestUtils.setField(service, "enableArchive", true);
        ReflectionTestUtils.setField(service, "enableNotifications", true);
        ReflectionTestUtils.setField(service, "maxRetries", 3);
    }

    @Test
    void enableArchive_true_callsArchive() {
        service.process("ORD-1");
        verify(archive).store("ORD-1");
    }

    @Test
    void enableArchive_false_skipsArchive() {
        ReflectionTestUtils.setField(service, "enableArchive", false);

        service.process("ORD-2");

        verifyNoInteractions(archive);
        verify(notifications).send("ORD-2");
    }

    @Test
    void enableNotifications_false_returnsImmediately() {
        ReflectionTestUtils.setField(service, "enableNotifications", false);

        FeatureService.ProcessResult result = service.process("ORD-3");

        assertThat(result.success()).isTrue();
        assertThat(result.failureReason()).isEqualTo("notifications_disabled");
        verify(archive).store("ORD-3");
        verifyNoInteractions(notifications);
    }

    @Test
    void retry_succeedsOnSecondAttempt() {
        doThrow(new FeatureService.TransientFailure("timeout")).doNothing()
            .when(notifications).send("ORD-4");

        FeatureService.ProcessResult result = service.process("ORD-4");

        assertThat(result.success()).isTrue();
        assertThat(result.attempts()).isEqualTo(2);
        verify(notifications, times(2)).send("ORD-4");
    }

    @Test
    void retry_failsAfterMaxRetries() {
        ReflectionTestUtils.setField(service, "maxRetries", 2);
        doThrow(new FeatureService.TransientFailure("offline")).when(notifications).send(any());

        FeatureService.ProcessResult result = service.process("ORD-5");

        assertThat(result.success()).isFalse();
        assertThat(result.attempts()).isEqualTo(2);
        verify(notifications, times(2)).send("ORD-5");
    }

    @ParameterizedTest(name = "blank orderId [\"{0}\"] is rejected")
    @ValueSource(strings = {"", " ", "\t"})
    void blankOrderId_isRejected(String input) {
        assertThatThrownBy(() -> service.process(input))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("blank");
        verifyNoInteractions(archive, notifications);
    }

    @Test
    void nullOrderId_isRejected() {
        assertThatThrownBy(() -> service.process(null))
            .isInstanceOf(ValidationException.class);
    }

    @Nested
    @DisplayName("private isEligible() — invokeMethod")
    class IsEligible {

        @Test
        void prefixedUserId_returnsTrue() {
            Boolean ok = ReflectionTestUtils.invokeMethod(service, "isEligible", "USR-001");
            assertThat(ok).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"alice", "USER-1", " USR-1"})
        void notPrefixed_returnsFalse(String input) {
            Boolean ok = ReflectionTestUtils.invokeMethod(service, "isEligible", input);
            assertThat(ok).isFalse();
        }

        @Test
        void nullUserId_returnsFalse() {
            Boolean ok = ReflectionTestUtils.invokeMethod(service, "isEligible", new Object[]{null});
            assertThat(ok).isFalse();
        }
    }
}
