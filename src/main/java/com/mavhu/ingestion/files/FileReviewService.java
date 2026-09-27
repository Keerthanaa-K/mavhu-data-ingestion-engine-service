package com.mavhu.ingestion.files;

import com.mavhu.ingestion.error.ConflictException;
import com.mavhu.ingestion.error.ForbiddenException;
import com.mavhu.ingestion.processing.EsgRowWriter;
import com.mavhu.ingestion.processing.IngestionJobStatus;
import com.mavhu.ingestion.users.AppUser;
import com.mavhu.ingestion.users.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Listing files by approval state, and the manager approve/reject decision. A decision is recorded
 * on the file and mirrored to is_approved on every row ingested from it, in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileReviewService {

    private final UploadedFileRepository uploadedFileRepository;
    private final UserDirectory userDirectory;
    private final EsgRowWriter rowWriter;

    public List<UploadedFileDetails> list(Integer customerId, ApprovalStatus status) {
        return uploadedFileRepository.findDetailsByStatus(customerId, status);
    }

    public UploadedFileDetails get(Integer customerId, UUID fileId) {
        return uploadedFileRepository.findDetails(customerId, fileId)
                .orElseThrow(() -> notFound(customerId, fileId));
    }

    /** Only files whose ingestion COMPLETED can be approved: approval vouches for the ingested data. */
    @Transactional
    public UploadedFileDetails approve(Integer customerId, UUID fileId, Integer managerUserId, String comment) {
        AppUser manager = requireManager(customerId, managerUserId);
        UploadedFileDetails details = get(customerId, fileId);
        requirePending(details);
        if (details.ingestionStatus() != IngestionJobStatus.COMPLETED) {
            throw new ConflictException("File " + fileId + " cannot be approved while its ingestion is "
                    + details.ingestionStatus() + "; only successfully ingested files can be approved");
        }
        if (!uploadedFileRepository.approve(fileId, manager.id(), trimToNull(comment))) {
            throw changedConcurrently(fileId);
        }
        int rows = rowWriter.setApproved(details.file().fileType(), fileId, true);
        log.info("File {} approved by user {}; {} rows marked approved", fileId, manager.id(), rows);
        return get(customerId, fileId);
    }

    /** Any PENDING file can be rejected (including failed ingestions); a reason is required. */
    @Transactional
    public UploadedFileDetails reject(Integer customerId, UUID fileId, Integer managerUserId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new IllegalArgumentException("comment is required when rejecting a file");
        }
        AppUser manager = requireManager(customerId, managerUserId);
        UploadedFileDetails details = get(customerId, fileId);
        requirePending(details);
        if (!uploadedFileRepository.reject(fileId, manager.id(), comment.trim())) {
            throw changedConcurrently(fileId);
        }
        int rows = rowWriter.setApproved(details.file().fileType(), fileId, false);
        log.info("File {} rejected by user {}; {} rows remain unapproved", fileId, manager.id(), rows);
        return get(customerId, fileId);
    }

    private AppUser requireManager(Integer customerId, Integer managerUserId) {
        if (managerUserId == null) {
            throw new IllegalArgumentException("managerUserId is required");
        }
        AppUser user = userDirectory.findActiveUser(managerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown managerUserId " + managerUserId));
        if (!user.customerId().equals(customerId)) {
            throw new ForbiddenException("User " + managerUserId + " does not belong to customer " + customerId);
        }
        if (!userDirectory.hasRole(managerUserId, UserDirectory.MANAGER_ROLE)) {
            throw new ForbiddenException("User " + managerUserId + " is not a " + UserDirectory.MANAGER_ROLE);
        }
        return user;
    }

    private static void requirePending(UploadedFileDetails details) {
        if (details.file().approvalStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("File " + details.file().fileId() + " is already "
                    + details.file().approvalStatus());
        }
    }

    private static ConflictException changedConcurrently(UUID fileId) {
        return new ConflictException("File " + fileId + " changed while being reviewed; reload and try again");
    }

    private static UploadedFileNotFoundException notFound(Integer customerId, UUID fileId) {
        return new UploadedFileNotFoundException("File " + fileId + " not found for customer " + customerId);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
