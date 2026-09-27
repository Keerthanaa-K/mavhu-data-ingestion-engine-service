package com.mavhu.ingestion.service;

import com.mavhu.ingestion.dto.FileUploadResponse;
import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.error.ForbiddenException;
import com.mavhu.ingestion.files.FileRegistrationService;
import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.users.AppUser;
import com.mavhu.ingestion.users.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Upload flow: check the uploading user belongs to the customer, store the file in S3, record it
 * (uploaded_files + ingestion job, PENDING approval), then hand off to the file processor asynchronously.
 */
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileStorageService fileStorageService;
    private final FileRegistrationService fileRegistrationService;
    private final UserDirectory userDirectory;
    private final FileProcessorClient fileProcessorClient;

    public FileUploadResponse upload(Integer customerId, Integer userId, String fileTypeValue, MultipartFile file)
            throws IOException {
        FileType fileType = FileType.fromValue(fileTypeValue);
        AppUser user = userDirectory.findActiveUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown userId " + userId));
        if (!user.customerId().equals(customerId)) {
            throw new ForbiddenException("User " + userId + " does not belong to customer " + customerId);
        }

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
