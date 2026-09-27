package com.mavhu.ingestion.processing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a raw cell value (String, BigDecimal, Boolean or LocalDateTime as read by
 * {@link ExcelSheetReader}) into the Java value stored for a {@link ColumnSpec}.
 */
final class CellValueConverter {

    static final String LATITUDE_COLUMN = "latitude";
    static final String LONGITUDE_COLUMN = "longitude";

    // Matches NUMERIC(38, 18) in the DDL.
    private static final int DECIMAL_SCALE = 18;
    private static final DateTimeFormatter SPACE_SEPARATED_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

    private CellValueConverter() {
    }

    /**
     * Writes the converted value(s) for spec into out.
     *
     * @throws IllegalArgumentException if the raw value cannot be converted to the spec's type
     */
    static void convert(ColumnSpec spec, Object raw, Map<String, Object> out) {
        switch (spec.type()) {
            case TEXT -> out.put(spec.column(), toText(raw));
            case DECIMAL -> out.put(spec.column(), toDecimal(raw));
            case INTEGER -> out.put(spec.column(), toInteger(raw));
            case BOOLEAN -> out.put(spec.column(), toBoolean(raw));
            case DATE -> out.put(spec.column(), toDate(raw));
            case TIMESTAMP -> out.put(spec.column(), toTimestamp(raw));
            case COORDINATES -> {
                String text = toText(raw);
                out.put(spec.column(), text);
                BigDecimal[] latLon = toLatLon(text);
                out.put(LATITUDE_COLUMN, latLon == null ? null : latLon[0]);
                out.put(LONGITUDE_COLUMN, latLon == null ? null : latLon[1]);
            }
        }
    }

    static String toText(Object raw) {
        return switch (raw) {
            case null -> null;
            case String s -> s.isBlank() ? null : s;
            case BigDecimal d -> d.stripTrailingZeros().toPlainString();
            case LocalDateTime t -> t.toLocalTime().equals(LocalTime.MIDNIGHT) ? t.toLocalDate().toString() : t.toString();
            default -> raw.toString();
        };
    }

    private static BigDecimal toDecimal(Object raw) {
        BigDecimal value = switch (raw) {
            case null -> null;
            case BigDecimal d -> d;
            case String s when isNotApplicable(s) -> null;
            case String s -> parseDecimal(s);
            default -> throw invalid(raw, "a number");
        };
        return value != null && value.scale() > DECIMAL_SCALE ? value.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP) : value;
    }

    private static Integer toInteger(Object raw) {
        BigDecimal value = toDecimal(raw);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw invalid(raw, "a whole number");
        }
    }

    private static Boolean toBoolean(Object raw) {
        return switch (raw) {
            case null -> null;
            case Boolean b -> b;
            case String s when isNotApplicable(s) -> null;
            case String s -> switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "yes", "y", "true" -> true;
                case "no", "n", "false" -> false;
                default -> throw invalid(raw, "Yes/No");
            };
            default -> throw invalid(raw, "Yes/No");
        };
    }

    private static LocalDate toDate(Object raw) {
        return switch (raw) {
            case null -> null;
            case LocalDateTime t -> t.toLocalDate();
            case String s when isNotApplicable(s) -> null;
            case String s -> {
                try {
                    yield LocalDate.parse(s.trim());
                } catch (DateTimeParseException e) {
                    throw invalid(raw, "a date (yyyy-MM-dd)");
                }
            }
            default -> throw invalid(raw, "a date (yyyy-MM-dd)");
        };
    }

    private static LocalDateTime toTimestamp(Object raw) {
        return switch (raw) {
            case null -> null;
            case LocalDateTime t -> t;
            case String s when isNotApplicable(s) -> null;
            case String s -> {
                String trimmed = s.trim();
                try {
                    yield trimmed.contains("T") ? LocalDateTime.parse(trimmed)
                            : LocalDateTime.parse(trimmed, SPACE_SEPARATED_TIMESTAMP);
                } catch (DateTimeParseException e) {
                    throw invalid(raw, "a timestamp (yyyy-MM-dd HH:mm)");
                }
            }
            default -> throw invalid(raw, "a timestamp (yyyy-MM-dd HH:mm)");
        };
    }

    private static BigDecimal[] toLatLon(String text) {
        if (text == null || isNotApplicable(text)) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != 2) {
            throw invalid(text, "coordinates as 'latitude, longitude'");
        }
        try {
            BigDecimal lat = new BigDecimal(parts[0].trim());
            BigDecimal lon = new BigDecimal(parts[1].trim());
            if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lon.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                throw invalid(text, "coordinates within latitude ±90 and longitude ±180");
            }
            return new BigDecimal[]{lat, lon};
        } catch (NumberFormatException e) {
            throw invalid(text, "coordinates as 'latitude, longitude'");
        }
    }

    private static BigDecimal parseDecimal(String s) {
        String cleaned = s.trim().replace(",", "").replace("$", "");
        boolean percent = cleaned.endsWith("%");
        if (percent) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            return percent ? value.movePointLeft(2) : value;
        } catch (NumberFormatException e) {
            throw invalid(s, "a number");
        }
    }

    /** Blank, or "n/a"-style markers the sample sheets use for "no value" (e.g. "n/a - open"). */
    private static boolean isNotApplicable(String s) {
        String lower = s.trim().toLowerCase(Locale.ROOT);
        return lower.isEmpty() || lower.startsWith("n/a") || lower.equals("na") || lower.equals("-");
    }

    private static IllegalArgumentException invalid(Object raw, String expected) {
        return new IllegalArgumentException("expected " + expected + " but found '" + raw + "'");
    }
}
