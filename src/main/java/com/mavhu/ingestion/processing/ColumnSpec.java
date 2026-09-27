package com.mavhu.ingestion.processing;

/**
 * Maps one spreadsheet header to one database column.
 */
public record ColumnSpec(String header, String column, ValueType type) {

    public static ColumnSpec text(String header, String column) {
        return new ColumnSpec(header, column, ValueType.TEXT);
    }

    public static ColumnSpec decimal(String header, String column) {
        return new ColumnSpec(header, column, ValueType.DECIMAL);
    }

    public static ColumnSpec integer(String header, String column) {
        return new ColumnSpec(header, column, ValueType.INTEGER);
    }

    public static ColumnSpec bool(String header, String column) {
        return new ColumnSpec(header, column, ValueType.BOOLEAN);
    }

    public static ColumnSpec date(String header, String column) {
        return new ColumnSpec(header, column, ValueType.DATE);
    }

    public static ColumnSpec timestamp(String header, String column) {
        return new ColumnSpec(header, column, ValueType.TIMESTAMP);
    }

    public static ColumnSpec coordinates(String header, String column) {
        return new ColumnSpec(header, column, ValueType.COORDINATES);
    }
}
