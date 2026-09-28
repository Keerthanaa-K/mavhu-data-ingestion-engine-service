package com.mavhu.ingestion.service;

import com.mavhu.ingestion.dto.BatchUploadResponse;
import com.mavhu.ingestion.dto.FileUploadResponse;
import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.error.ForbiddenException;
import com.mavhu.ingestion.files.FileRegistrationService;
import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.users.AppUser;
import com.mavhu.ingestion.users.UserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Upload flow: check the uploading user belongs to the customer, store the file in S3, record it
 * (uploaded_files + ingestion job, PENDING approval), then hand off to the file processor asynchronously.
 */
@Service
public class FileUploadService {

    private final FileStorageService fileStorageService;
    private final FileRegistrationService fileRegistrationService;
    private final UserDirectory userDirectory;
    private final FileProcessorClient fileProcessorClient;
    private final int maxBatchFiles;

    public FileUploadService(FileStorageService fileStorageService,
                             FileRegistrationService fileRegistrationService,
                             UserDirectory userDirectory,
                             FileProcessorClient fileProcessorClient,
                             @Value("${app.upload.max-batch-files:20}") int maxBatchFiles) {
        this.fileStorageService = fileStorageService;
        this.fileRegistrationService = fileRegistrationService;
        this.userDirectory = userDirectory;
        this.fileProcessorClient = fileProcessorClient;
        this.maxBatchFiles = maxBatchFiles;
    }

    public FileUploadResponse upload(Integer customerId, Integer userId, String fileTypeValue, MultipartFile file)
            throws IOException {
        FileType fileType = FileType.fromValue(fileTypeValue);
        requireUserOfCustomer(customerId, userId);
        return store(customerId, userId, fileType, file);
    }

    /**
     * Uploads several files of the same type. The whole request is validated (user, type, file
     * count, no empty files) before anything is stored, so a bad request stores nothing.
     */
    public BatchUploadResponse uploadBatch(Integer customerId, Integer userId, String fileTypeValue,
                                           List<MultipartFile> files) throws IOException {
        FileType fileType = FileType.fromValue(fileTypeValue);
        requireUserOfCustomer(customerId, userId);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        if (files.size() > maxBatchFiles) {
            throw new IllegalArgumentException(
                    "Too many files: " + files.size() + " (at most " + maxBatchFiles + " per batch)");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                String name = file == null ? null : file.getOriginalFilename();
                throw new IllegalArgumentException("File must not be empty" + (name == null ? "" : ": " + name));
            }
        }

        List<FileUploadResponse> uploaded = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            uploaded.add(store(customerId, userId, fileType, file));
        }
        return new BatchUploadResponse(customerId, fileType.name(), uploaded.size(), uploaded);
    }

    private void requireUserOfCustomer(Integer customerId, Integer userId) {
        AppUser user = userDirectory.findActiveUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown userId " + userId));
        if (!user.customerId().equals(customerId)) {
            throw new ForbiddenException("User " + userId + " does not belong to customer " + customerId);
        }
    }

    private FileUploadResponse store(Integer customerId, Integer userId, FileType fileType, MultipartFile file)
            throws IOException {
        FileStorageService.StoredFile stored = fileStorageService.upload(customerId, fileType, file);
        S3Location location = stored.location();
        FileRegistrationService.Registration registration = fileRegistrationService.register(customerId, userId,
                fileType, stored.originalFileName(), file.getContentType(), stored.size(), location.toString());

        fileProcessorClient.triggerProcessing(new ProcessFileRequest(
                registration.job().jobId(), customerId, location.toString(), fileType.name()));

        return new FileUploadResponse(registration.file().fileId(), customerId, userId, fileType.name(),
                stored.originalFileName(), location.bucket(), location.key(), location.toString(), stored.size(),
                registration.file().approvalStatus(), registration.job().jobId(), registration.job().status());
    }
}
