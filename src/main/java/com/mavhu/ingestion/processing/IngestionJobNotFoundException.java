package com.mavhu.ingestion.processing;

import com.mavhu.ingestion.error.NotFoundException;

import java.util.UUID;

public class IngestionJobNotFoundException extends NotFoundException {

    public IngestionJobNotFoundException(UUID jobId) {
        super("Ingestion job not found: " + jobId);
    }
}
