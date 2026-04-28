package com.example.tdd.storage;

import com.example.tdd.config.AwsConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {AwsConfig.class, InvoiceStorageService.class}
)
@ActiveProfiles("test")
@Testcontainers
class InvoiceStorageServiceTest {

    @Container
    static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
        .withServices(Service.S3);

    @DynamicPropertySource
    static void awsProps(DynamicPropertyRegistry r) {
        r.add("aws.endpoint",   () -> localStack.getEndpoint().toString());
        r.add("aws.region",     localStack::getRegion);
        r.add("aws.access-key", localStack::getAccessKey);
        r.add("aws.secret-key", localStack::getSecretKey);
        r.add("aws.s3.bucket-uploads", () -> "app-uploads");
    }

    @BeforeAll
    static void setupBucket() throws Exception {
        localStack.execInContainer("awslocal", "s3", "mb", "s3://app-uploads");
    }

    @Autowired InvoiceStorageService service;
    @Autowired S3Client s3;

    @Test
    void store_uploadsBytes_setsMetadata() {
        UUID orderId = UUID.randomUUID();
        byte[] pdf = "FAKE PDF CONTENT".getBytes(UTF_8);

        InvoiceStorageService.StoredFile result = service.store(orderId, pdf);

        assertThat(result.size()).isEqualTo(pdf.length);
        assertThat(result.key()).contains(orderId.toString());

        byte[] downloaded = service.download(result.key());
        assertThat(downloaded).isEqualTo(pdf);

        var head = s3.headObject(b -> b.bucket("app-uploads").key(result.key()));
        assertThat(head.metadata()).containsEntry("orderid", orderId.toString());
    }

    @Test
    void presignedUrl_canBeFetchedExternally() throws Exception {
        UUID orderId = UUID.randomUUID();
        var stored = service.store(orderId, "TEST".getBytes(UTF_8));

        URL url = service.presignedDownloadUrl(stored.key(), Duration.ofMinutes(5));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(url.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("TEST".getBytes(UTF_8));
        }
    }

    @Test
    void download_missingKey_throws() {
        assertThatThrownBy(() -> service.download("does/not/exist"))
            .isInstanceOf(software.amazon.awssdk.services.s3.model.NoSuchKeyException.class);
    }
}
