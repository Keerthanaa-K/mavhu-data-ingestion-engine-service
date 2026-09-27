package com.mavhu.ingestion.files;

import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.processing.IngestionJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC access to mavhu.uploaded_files (DDL: db/ddl/esg_file_approval.sql).
 */
@Repository
@RequiredArgsConstructor
public class UploadedFileRepository {

    private static final String DETAILS_SELECT = """
            SELECT f.*, up.name AS uploaded_by_name, rv.name AS reviewed_by_name,
                   j.job_id, j.status AS ingestion_status, j.rows_ingested, j.error_message AS ingestion_error
            FROM mavhu.uploaded_files f
            LEFT JOIN mavhu.users up ON up.id = f.uploaded_by_user_id
            LEFT JOIN mavhu.users rv ON rv.id = f.reviewed_by_user_id
            LEFT JOIN mavhu.ingestion_jobs j ON j.file_id = f.file_id
            """;

    private static final RowMapper<UploadedFile> FILE_MAPPER = (rs, rowNum) -> mapFile(rs);

    private static final RowMapper<UploadedFileDetails> DETAILS_MAPPER = (rs, rowNum) -> {
        String ingestionStatus = rs.getString("ingestion_status");
        return new UploadedFileDetails(
                mapFile(rs),
                rs.getString("uploaded_by_name"),
                rs.getString("reviewed_by_name"),
                rs.getObject("job_id", UUID.class),
                ingestionStatus == null ? null : IngestionJobStatus.valueOf(ingestionStatus),
                rs.getObject("rows_ingested", Integer.class),
                rs.getString("ingestion_error"));
    };

    private final JdbcTemplate jdbcTemplate;

    public UploadedFile create(Integer customerId, Integer uploadedByUserId, FileType fileType,
                               String originalFileName, String contentType, long sizeBytes, String s3Path) {
        UUID fileId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO mavhu.uploaded_files (file_id, customer_id, uploaded_by_user_id, file_type,
                            original_file_name, content_type, size_bytes, s3_path, approval_status, is_approved)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)""",
                fileId, customerId, uploadedByUserId, fileType.name(), originalFileName, contentType, sizeBytes,
                s3Path, ApprovalStatus.PENDING.name());
        return findById(fileId).orElseThrow();
    }

    public Optional<UploadedFile> findById(UUID fileId) {
        return jdbcTemplate.query("SELECT * FROM mavhu.uploaded_files WHERE file_id = ?", FILE_MAPPER, fileId)
                .stream().findFirst();
    }

    public Optional<UploadedFile> findByS3Path(String s3Path) {
        return jdbcTemplate.query("SELECT * FROM mavhu.uploaded_files WHERE s3_path = ?", FILE_MAPPER, s3Path)
                .stream().findFirst();
    }

    public Optional<UploadedFileDetails> findDetails(Integer customerId, UUID fileId) {
        return jdbcTemplate.query(DETAILS_SELECT + "WHERE f.customer_id = ? AND f.file_id = ?",
                DETAILS_MAPPER, customerId, fileId).stream().findFirst();
    }

    /** Newest first. */
    public List<UploadedFileDetails> findDetailsByStatus(Integer customerId, ApprovalStatus status) {
        return jdbcTemplate.query(DETAILS_SELECT + "WHERE f.customer_id = ? AND f.approval_status = ? ORDER BY f.uploaded_at DESC",
                DETAILS_MAPPER, customerId, status.name());
    }

    /**
     * Approves a PENDING file whose ingestion has COMPLETED. Returns false if either condition no longer
     * holds, so a concurrent review or re-process cannot slip in between check and update.
     */
    public boolean approve(UUID fileId, Integer reviewerUserId, String comment) {
        return jdbcTemplate.update("""
                        UPDATE mavhu.uploaded_files f
                        SET approval_status = 'APPROVED', is_approved = TRUE, reviewed_by_user_id = ?,
                            reviewed_at = CURRENT_TIMESTAMP, review_comment = ?
                        WHERE f.file_id = ? AND f.approval_status = 'PENDING'
                          AND EXISTS (SELECT 1 FROM mavhu.ingestion_jobs j WHERE j.file_id = f.file_id AND j.status = 'COMPLETED')""",
                reviewerUserId, comment, fileId) == 1;
    }

    /** Rejects a PENDING file. Returns false if it is no longer PENDING. */
    public boolean reject(UUID fileId, Integer reviewerUserId, String comment) {
        return jdbcTemplate.update("""
                        UPDATE mavhu.uploaded_files
                        SET approval_status = 'REJECTED', is_approved = FALSE, reviewed_by_user_id = ?,
                            reviewed_at = CURRENT_TIMESTAMP, review_comment = ?
                        WHERE file_id = ? AND approval_status = 'PENDING'""",
                reviewerUserId, comment, fileId) == 1;
    }

    private static UploadedFile mapFile(ResultSet rs) throws SQLException {
        return new UploadedFile(
                rs.getObject("file_id", UUID.class),
                rs.getInt("customer_id"),
                rs.getObject("uploaded_by_user_id", Integer.class),
                FileType.valueOf(rs.getString("file_type")),
                rs.getString("original_file_name"),
                rs.getString("content_type"),
                rs.getObject("size_bytes", Long.class),
                rs.getString("s3_path"),
                ApprovalStatus.valueOf(rs.getString("approval_status")),
                rs.getBoolean("is_approved"),
                rs.getObject("reviewed_by_user_id", Integer.class),
                rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getString("review_comment"),
                rs.getObject("uploaded_at", OffsetDateTime.class));
    }
}
