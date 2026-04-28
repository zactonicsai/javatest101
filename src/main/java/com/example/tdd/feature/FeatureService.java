package com.example.tdd.feature;

import com.example.tdd.domain.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    @Value("${feature.enableArchive}")
    private boolean enableArchive;

    @Value("${feature.enableNotifications}")
    private boolean enableNotifications;

    @Value("${feature.maxRetries:3}")
    private int maxRetries;

    private final ArchiveClient archiveClient;
    private final NotificationClient notifications;

    public FeatureService(ArchiveClient archiveClient, NotificationClient notifications) {
        this.archiveClient = archiveClient;
        this.notifications = notifications;
    }

    public ProcessResult process(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new ValidationException("orderId cannot be blank");
        }

        if (enableArchive) {
            archiveClient.store(orderId);
        }

        if (!enableNotifications) {
            return new ProcessResult(orderId, 0, true, "notifications_disabled");
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                notifications.send(orderId);
                return new ProcessResult(orderId, attempt, true, null);
            } catch (TransientFailure e) {
                log.warn("Notification attempt {} failed for {}: {}", attempt, orderId, e.getMessage());
                if (attempt == maxRetries) {
                    return new ProcessResult(orderId, attempt, false, e.getMessage());
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** Private method intentionally — exercised in tests via ReflectionTestUtils.invokeMethod. */
    @SuppressWarnings("unused")
    private boolean isEligible(String userId) {
        return userId != null && userId.startsWith("USR");
    }

    public record ProcessResult(String orderId, int attempts, boolean success, String failureReason) {}

    public static class TransientFailure extends RuntimeException {
        public TransientFailure(String message) { super(message); }
    }

    public interface ArchiveClient { void store(String orderId); }
    public interface NotificationClient { void send(String orderId); }
}
