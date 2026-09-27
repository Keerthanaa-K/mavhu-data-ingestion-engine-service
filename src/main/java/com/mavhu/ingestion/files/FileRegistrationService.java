package com.mavhu.ingestion.files;

import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.processing.IngestionJob;
import com.mavhu.ingestion.processing.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an upload: the uploaded_files row and its ingestion job, atomically.
 */
@Service
@RequiredArgsConstructor
public class FileRegistrationService {

    private final UploadedFileRepository uploadedFileRepository;
    private final IngestionJobRepository jobRepository;

    public record Registration(UploadedFile file, IngestionJob job) {
    }

    @Transactional
    public Registration register(Integer customerId, Integer uploadedByUserId, FileType fileType,
                                 String originalFileName, String contentType, long sizeBytes, String s3Path) {
        UploadedFile file = uploadedFileRepository.create(customerId, uploadedByUserId, fileType,
                originalFileName, contentType, sizeBytes, s3Path);
        return new Registration(file, jobRepository.create(file));
    }
}
