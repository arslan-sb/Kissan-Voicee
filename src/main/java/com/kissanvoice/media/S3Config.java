package com.kissanvoice.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3Client/S3Presigner beans for the {@code s3} media provider. Only created
 * when that provider is active, so a plain local-fs run needs no AWS
 * configuration at all - see LocalFsMediaStorage.
 */
@Configuration
@ConditionalOnProperty(name = "kissanvoice.media.provider", havingValue = "s3")
public class S3Config {

    @Bean
    S3Client s3Client(@Value("${kissanvoice.media.s3-endpoint:}") String endpoint,
                      @Value("${kissanvoice.media.region}") String region) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (isOverridden(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(pathStyleConfig());
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${kissanvoice.media.s3-endpoint:}") String endpoint,
                            @Value("${kissanvoice.media.region}") String region) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (isOverridden(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(pathStyleConfig());
        }
        return builder.build();
    }

    private static boolean isOverridden(String endpoint) {
        return endpoint != null && !endpoint.isBlank();
    }

    /**
     * LocalStack needs path-style URLs ({@code http://localhost:4566/bucket/key}
     * rather than {@code http://bucket.s3.amazonaws.com/key}, which does not
     * resolve on a dev machine).
     */
    private static S3Configuration pathStyleConfig() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    /**
     * Any non-empty static credentials satisfy LocalStack; this class only
     * ever wires the local-dev "s3" profile. Pointing it at real AWS is a
     * matter of leaving {@code kissanvoice.media.s3-endpoint} unset and
     * swapping this for {@code DefaultCredentialsProvider} - nothing else in
     * this adapter changes.
     */
    private static AwsCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
    }
}
