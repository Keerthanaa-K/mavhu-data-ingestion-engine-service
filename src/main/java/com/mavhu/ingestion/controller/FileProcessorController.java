package com.mavhu.ingestion.controller;

import com.mavhu.ingestion.dto.FileTypeResponse;
import com.mavhu.ingestion.dto.IngestionJobResponse;
import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.processing.FileProcessingService;
import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.processing.IngestionJob;
import com.mavhu.ingestion.processing.IngestionJobNotFoundException;
import com.mavhu.ingestion.processing.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ingestion/v1/files")
@RequiredArgsConstructor
public class FileProcessorController {

    private final FileProcessingService fileProcessingService;
    private final IngestionJobRepository jobRepository;

    /**
     * Accepts an S3 file for processing and returns 202 straight away; the file is parsed
     * according to its fileType and ingested into that type's table in the background.
     */
    @PostMapping("/process")
    public ResponseEntity<IngestionJobResponse> process(@RequestBody ProcessFileRequest request) {
        IngestionJob job = fileProcessingService.prepare(request);
        log.info("Accepted job {} for processing: {} {}", job.jobId(), job.fileType(), job.s3Path());
        fileProcessingService.processAsync(job.jobId());
        return ResponseEntity.accepted().body(IngestionJobResponse.from(job));
    }

    @GetMapping("/jobs/{jobId}")
    public IngestionJobResponse getJob(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(IngestionJobResponse::from)
                .orElseThrow(() -> new IngestionJobNotFoundException(jobId));
    }

    /** The fixed list of file types a user can pick from when uploading. */
    @GetMapping("/types")
    public List<FileTypeResponse> fileTypes() {
        return Arrays.stream(FileType.values()).map(FileTypeResponse::from).toList();
    }
}
