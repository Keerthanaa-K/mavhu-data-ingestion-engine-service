package com.mavhu.ingestion.files;

import com.mavhu.ingestion.error.NotFoundException;

public class UploadedFileNotFoundException extends NotFoundException {

    public UploadedFileNotFoundException(String message) {
        super(message);
    }
}
