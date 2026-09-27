package com.mavhu.ingestion.controller;

import com.mavhu.ingestion.dto.ReviewRequest;
import com.mavhu.ingestion.dto.UploadedFileResponse;
import com.mavhu.ingestion.files.ApprovalStatus;
import com.mavhu.ingestion.files.FileReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A customer's uploaded files by approval state, and the manager approve/reject decision.
 */
@RestController
@RequestMapping("/ingestion/v1/customers/{customerId}/files")
@RequiredArgsConstructor
public class FileReviewController {

    private final FileReviewService fileReviewService;

    /** @param status PENDING (awaiting a manager), APPROVED or REJECTED; newest first. */
    @GetMapping
    public List<UploadedFileResponse> list(@PathVariable Integer customerId, @RequestParam("status") String status) {
        return fileReviewService.list(customerId, ApprovalStatus.fromValue(status)).stream()
                .map(UploadedFileResponse::from)
                .toList();
    }

    @GetMapping("/{fileId}")
    public UploadedFileResponse get(@PathVariable Integer customerId, @PathVariable UUID fileId) {
        return UploadedFileResponse.from(fileReviewService.get(customerId, fileId));
    }

    @PostMapping("/{fileId}/approve")
    public UploadedFileResponse approve(@PathVariable Integer customerId, @PathVariable UUID fileId,
                                        @RequestBody ReviewRequest request) {
        return UploadedFileResponse.from(
                fileReviewService.approve(customerId, fileId, request.managerUserId(), request.comment()));
    }

    @PostMapping("/{fileId}/reject")
    public UploadedFileResponse reject(@PathVariable Integer customerId, @PathVariable UUID fileId,
                                       @RequestBody ReviewRequest request) {
        return UploadedFileResponse.from(
                fileReviewService.reject(customerId, fileId, request.managerUserId(), request.comment()));
    }
}
