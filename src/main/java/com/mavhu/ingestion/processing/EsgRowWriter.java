package com.mavhu.ingestion.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes parsed rows into the table of their {@link FileType}.
 */
@Component
@RequiredArgsConstructor
public class EsgRowWriter {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Replaces the file's rows, so re-processing never duplicates data. Rows start unapproved
     * (only PENDING files are processed). Delete and insert run in one transaction: either the whole
     * file lands or nothing changes.
     */
    @Transactional
    public int replaceRows(IngestionJob job, List<ParsedRow> rows) {
        FileType fileType = job.fileType();
        jdbcTemplate.update("DELETE FROM " + fileType.tableName() + " WHERE file_id = :fileId",
                new MapSqlParameterSource("fileId", job.fileId()));

        Set<String> dataColumns = new LinkedHashSet<>(rows.getFirst().values().keySet());
        String sql = insertSql(fileType, dataColumns);

        List<MapSqlParameterSource> batch = new ArrayList<>(rows.size());
        for (ParsedRow row : rows) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("ingestion_job_id", job.jobId())
                    .addValue("file_id", job.fileId())
                    .addValue("is_approved", false)
                    .addValue("customer_id", job.customerId())
                    .addValue("source_row_number", row.rowNumber());
            row.values().forEach(params::addValue);
            batch.add(params);
        }
        jdbcTemplate.batchUpdate(sql, batch.toArray(MapSqlParameterSource[]::new));
        return rows.size();
    }

    /** Mirrors the file's approval decision onto its ingested rows; returns the number of rows updated. */
    public int setApproved(FileType fileType, UUID fileId, boolean approved) {
        return jdbcTemplate.update("UPDATE " + fileType.tableName() + " SET is_approved = :approved WHERE file_id = :fileId",
                new MapSqlParameterSource().addValue("approved", approved).addValue("fileId", fileId));
    }

    // Table and column names come from the FileType enum, never from user input.
    private static String insertSql(FileType fileType, Set<String> dataColumns) {
        List<String> columns = new ArrayList<>(List.of(
                "ingestion_job_id", "file_id", "is_approved", "customer_id", "source_row_number"));
        columns.addAll(dataColumns);
        return "INSERT INTO " + fileType.tableName()
                + " (" + String.join(", ", columns) + ") VALUES ("
                + columns.stream().map(c -> ":" + c).collect(Collectors.joining(", ")) + ")";
    }
}
