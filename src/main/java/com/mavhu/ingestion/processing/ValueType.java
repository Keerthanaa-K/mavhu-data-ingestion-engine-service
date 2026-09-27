package com.mavhu.ingestion.processing;

/**
 * How a spreadsheet cell is converted before it is written to its database column.
 */
public enum ValueType {
    TEXT,
    DECIMAL,
    INTEGER,
    BOOLEAN,
    DATE,
    TIMESTAMP,
    /** "lat, lon" text; kept as-is and also split into the table's latitude/longitude columns. */
    COORDINATES
}
