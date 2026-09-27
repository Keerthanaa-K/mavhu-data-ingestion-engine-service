package com.mavhu.ingestion.processing;

import java.util.Map;

/**
 * One data row, converted and keyed by database column. rowNumber is 1-based, as shown in Excel.
 */
public record ParsedRow(int rowNumber, Map<String, Object> values) {
}
