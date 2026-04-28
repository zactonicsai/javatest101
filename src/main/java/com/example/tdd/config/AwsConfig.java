package com.example.tdd.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")          private String region;
    @Value("${aws.endpoint}")        private String endpoint;
    @Value("${aws.access-key}")      private String accessKey;
    @Value("${aws.secret-key}")      private String secretKey;

    private StaticCredentialsProvider creds() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(region))
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(creds())
            .forcePathStyle(true)
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .region(Region.of(region))
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(creds())
            .serviceConfiguration(S3Configuration.builder().pathStyle(true).build())
            .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
            .region(Region.of(region))
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(creds())
            .build();
    }
}
