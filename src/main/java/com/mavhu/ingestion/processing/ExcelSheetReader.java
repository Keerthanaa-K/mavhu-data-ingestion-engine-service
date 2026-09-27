package com.mavhu.ingestion.processing;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the first sheet of an ESG workbook into rows for a {@link FileType}.
 * <p>
 * Layout of the sample files: a title row, a header row, the data rows, then optionally a blank
 * row followed by free-text notes. The header row is located by matching the file type's headers
 * (so extra title rows or column re-ordering are tolerated); data ends at the first blank row.
 * Aggregate rows whose first cell starts with "TOTAL" are skipped.
 */
@Component
public class ExcelSheetReader {

    private static final int HEADER_SEARCH_ROWS = 10;

    public List<ParsedRow> read(InputStream inputStream, FileType fileType) throws IOException {
        try (Workbook workbook = open(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new FileValidationException("Workbook has no sheets");
            }
            return read(workbook.getSheetAt(0), fileType);
        }
    }

    // POI reports non-Excel or corrupt content as IOException or assorted runtime exceptions.
    private static Workbook open(InputStream inputStream) {
        try {
            return WorkbookFactory.create(inputStream);
        } catch (IOException | RuntimeException e) {
            throw new FileValidationException("File is not a readable Excel workbook: " + e.getMessage());
        }
    }

    private List<ParsedRow> read(Sheet sheet, FileType fileType) {
        HeaderRow header = locateHeaders(sheet, fileType);
        ColumnSpec firstColumn = fileType.columns().getFirst();

        List<ParsedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int r = header.rowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Map<String, Object> raw = new HashMap<>();
            boolean blank = true;
            for (ColumnSpec spec : fileType.columns()) {
                Object value = row == null ? null : readCell(row.getCell(header.columnIndex(spec)));
                raw.put(spec.header(), value);
                blank &= value == null;
            }
            if (blank) {
                break;
            }
            String firstValue = CellValueConverter.toText(raw.get(firstColumn.header()));
            if (firstValue != null && firstValue.toUpperCase(Locale.ROOT).startsWith("TOTAL")) {
                continue;
            }

            int excelRowNumber = r + 1;
            Map<String, Object> values = new LinkedHashMap<>();
            for (ColumnSpec spec : fileType.columns()) {
                try {
                    CellValueConverter.convert(spec, raw.get(spec.header()), values);
                } catch (IllegalArgumentException e) {
                    errors.add("Row " + excelRowNumber + ", '" + spec.header() + "': " + e.getMessage());
                }
            }
            rows.add(new ParsedRow(excelRowNumber, values));
        }

        if (!errors.isEmpty()) {
            throw new FileValidationException(errors);
        }
        if (rows.isEmpty()) {
            throw new FileValidationException("No data rows found below the header row");
        }
        return rows;
    }

    private record HeaderRow(int rowIndex, Map<String, Integer> columnIndexByHeader) {
        int columnIndex(ColumnSpec spec) {
            return columnIndexByHeader.get(normalize(spec.header()));
        }
    }

    private HeaderRow locateHeaders(Sheet sheet, FileType fileType) {
        List<String> fewestMissing = fileType.columns().stream().map(ColumnSpec::header).toList();

        int lastRow = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS - 1);
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> found = new HashMap<>();
            for (Cell cell : row) {
                String text = CellValueConverter.toText(readCell(cell));
                if (text != null) {
                    found.putIfAbsent(normalize(text), cell.getColumnIndex());
                }
            }
            List<String> missing = fileType.columns().stream()
                    .map(ColumnSpec::header)
                    .filter(h -> !found.containsKey(normalize(h)))
                    .toList();
            if (missing.isEmpty()) {
                return new HeaderRow(r, found);
            }
            if (missing.size() < fewestMissing.size()) {
                fewestMissing = missing;
            }
        }

        throw new FileValidationException("File does not match file type " + fileType.name()
                + "; missing header(s): " + String.join(", ", fewestMissing));
    }

    private static Object readCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue()
                    // NumberToTextConverter mirrors Excel's display, avoiding binary noise like 0.30000000000000004
                    : new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> null;
        };
    }

    private static String normalize(String header) {
        return header.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
