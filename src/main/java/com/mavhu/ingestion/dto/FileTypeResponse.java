package com.mavhu.ingestion.dto;

import com.mavhu.ingestion.processing.ColumnSpec;
import com.mavhu.ingestion.processing.FileType;

import java.util.List;

public record FileTypeResponse(String fileType, String displayName, String targetTable, List<String> expectedHeaders) {

    public static FileTypeResponse from(FileType fileType) {
        return new FileTypeResponse(fileType.name(), fileType.displayName(), fileType.tableName(),
                fileType.columns().stream().map(ColumnSpec::header).toList());
    }
}
