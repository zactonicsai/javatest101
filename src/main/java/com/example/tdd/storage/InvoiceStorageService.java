package com.example.tdd.storage;

import com.example.tdd.domain.order.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceStorageService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket-uploads}")
    private String bucket;

    public InvoiceStorageService(S3Client s3, S3Presigner presigner) {
        this.s3 = s3;
        this.presigner = presigner;
    }

    /** Called by OrderService at order creation. Generates a placeholder invoice. */
    public StoredFile storeInvoiceFor(Order order) {
        String content = """
            INVOICE
            =======
            Order:  %s
            User:   %s
            Total:  %s
            Date:   %s
            Status: %s
            """.formatted(
                order.getId(), order.getUserId(), order.total(),
                order.getCreatedAt(), order.getStatus());
        return store(order.getId(), content.getBytes(StandardCharsets.UTF_8));
    }

    public StoredFile store(UUID orderId, byte[] content) {
        String key = "invoices/%s/%s.pdf".formatted(LocalDate.now(), orderId);

        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .metadata(Map.of("orderId", orderId.toString()))
                .build(),
            RequestBody.fromBytes(content));

        log.debug("Stored invoice s3://{}/{}", bucket, key);
        return new StoredFile(bucket, key, content.length);
    }

    public byte[] download(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket).key(key).build())
            .asByteArray();
    }

    public URL presignedDownloadUrl(String key, Duration ttl) {
        var req = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(b -> b.bucket(bucket).key(key))
            .build();
        return presigner.presignGetObject(req).url();
    }

    public record StoredFile(String bucket, String key, long size) {}
}
