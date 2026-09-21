package com.kissanvoice.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Block 3 adapter. Same MediaStoragePort contract as LocalFsMediaStorage, so
 * neither RecordingService nor RecordingController changed to pick this up -
 * only application.yml's {@code kissanvoice.media.provider} did.
 */
@Component
@ConditionalOnProperty(name = "kissanvoice.media.provider", havingValue = "s3")
public class S3MediaStorage implements MediaStoragePort {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration presignTtl;

    public S3MediaStorage(S3Client s3, S3Presigner presigner,
                          @Value("${kissanvoice.media.bucket}") String bucket,
                          @Value("${kissanvoice.media.presign-ttl-minutes:15}") long presignTtlMinutes) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.presignTtl = Duration.ofMinutes(presignTtlMinutes);
    }

    @Override
    public String store(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        try {
            s3.putObject(request, RequestBody.fromInputStream(content, sizeBytes));
        } catch (SdkException ex) {
            throw new IOException("Failed to upload " + key + " to s3://" + bucket, ex);
        }
        return key;
    }

    @Override
    public String presignedUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignTtl)
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
