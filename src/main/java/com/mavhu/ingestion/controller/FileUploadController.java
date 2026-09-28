package com.mavhu.ingestion.controller;

import com.mavhu.ingestion.dto.BatchUploadResponse;
import com.mavhu.ingestion.dto.FileUploadResponse;
import com.mavhu.ingestion.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ingestion/v1/customers/{customerId}/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * Stores the file in S3 and returns immediately; processing into the database happens
     * asynchronously and the file waits for a manager's approval. Poll
     * GET /ingestion/v1/customers/{customerId}/files/{fileId} for the outcome.
     *
     * @param userId   the uploading user; must belong to the customer
     * @param fileType one of GET /ingestion/v1/files/types, e.g. SCOPE_1_DIRECT
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(@PathVariable Integer customerId,
                                                     @RequestPart("file") MultipartFile file,
                                                     @RequestParam("userId") Integer userId,
                                                     @RequestParam("fileType") String fileType)
            throws IOException {
        log.info("Received {} upload for customer {} from user {}: {}", fileType, customerId, userId,
                file.getOriginalFilename());
        FileUploadResponse response = fileUploadService.upload(customerId, userId, fileType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Same as the single upload, for several files of one type: send each file as a repeated
     * "files" part. Every file gets its own fileId and ingestion job, processed and reviewed
     * independently. Nothing is stored if any file is empty or the batch is too large.
     */
    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchUploadResponse> uploadBatch(@PathVariable Integer customerId,
                                                           @RequestPart("files") List<MultipartFile> files,
                                                           @RequestParam("userId") Integer userId,
                                                           @RequestParam("fileType") String fileType)
            throws IOException {
        log.info("Received batch of {} {} files for customer {} from user {}", files.size(), fileType, customerId,
                userId);
        BatchUploadResponse response = fileUploadService.uploadBatch(customerId, userId, fileType, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
