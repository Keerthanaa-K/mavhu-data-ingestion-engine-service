package com.mavhu.ingestion.service;

import com.mavhu.ingestion.processing.FileType;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final S3Template s3Template;
    private final String bucket;
    private final Clock clock;

    public FileStorageService(S3Template s3Template,
                              @Value("${app.s3.bucket:mavhu-data-ingestion-engine-service}") String bucket) {
        this.s3Template = s3Template;
        this.bucket = bucket;
        this.clock = Clock.systemUTC();
    }

    public record StoredFile(S3Location location, String originalFileName, long size) {
    }

    /**
     * Uploads the file to s3://{bucket}/{customerId}/files/{fileType}/{yyyy-MM-dd}/{uuid}-{fileName}.
     */
    public StoredFile upload(Integer customerId, FileType fileType, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        S3Location location = new S3Location(bucket,
                buildKey(customerId, fileType, LocalDate.now(clock), originalFileName));

        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .metadata("original-file-name", originalFileName)
                .metadata("customer-id", String.valueOf(customerId))
                .metadata("file-type", fileType.name())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(location.bucket(), location.key(), inputStream, metadata);
        }

        log.info("Uploaded file {} for customer {} to {}", originalFileName, customerId, location);
        return new StoredFile(location, originalFileName, file.getSize());
    }

    /**
     * Opens the object for reading. Only objects in this service's bucket are readable.
     */
    public InputStream download(S3Location location) throws IOException {
        requireOwnBucket(location);
        return s3Template.download(location.bucket(), location.key()).getInputStream();
    }

    public void requireOwnBucket(S3Location location) {
        if (!bucket.equals(location.bucket())) {
            throw new IllegalArgumentException("s3Path must be in bucket " + bucket + " but was " + location.bucket());
        }
    }

    /** Prefix every object of a customer lives under, e.g. "42/files/". */
    public static String customerPrefix(Integer customerId) {
        return customerId + "/files/";
    }

    static String buildKey(Integer customerId, FileType fileType, LocalDate date, String originalFileName) {
        return "%s%s/%s/%s-%s".formatted(
                customerPrefix(customerId), fileType.pathSegment(), date, UUID.randomUUID(), sanitize(originalFileName));
    }

    // Keep S3 keys safe: drop path separators and collapse unusual characters.
    private static String sanitize(String value) {
        String name = StringUtils.getFilename(value);
        return (name == null ? value : name).replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
