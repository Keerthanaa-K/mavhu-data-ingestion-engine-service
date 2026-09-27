package com.mavhu.ingestion.processing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelSheetReaderTest {

    private final ExcelSheetReader reader = new ExcelSheetReader();

    /** Every sample file with its file type and number of data rows (title, notes and TOTAL rows excluded). */
    static Stream<Arguments> sampleFiles() {
        return Stream.of(
                Arguments.of("02_Entity_Reference.xlsx", FileType.ENTITY_REFERENCE, 9),
                Arguments.of("03_Scope_1_Direct.xlsx", FileType.SCOPE_1_DIRECT, 10),
                Arguments.of("04_Scope_2_Energy.xlsx", FileType.SCOPE_2_ENERGY, 7),
                Arguments.of("05_Scope_3_Operational.xlsx", FileType.SCOPE_3_OPERATIONAL, 9),
                Arguments.of("06_Financed_Emissions.xlsx", FileType.FINANCED_EMISSIONS, 19),
                Arguments.of("07_DQ_Portfolio_Summary.xlsx", FileType.DQ_PORTFOLIO_SUMMARY, 10),
                Arguments.of("08_Insurance_Emissions.xlsx", FileType.INSURANCE_EMISSIONS, 7),
                Arguments.of("09_Facilitated_Emissions.xlsx", FileType.FACILITATED_EMISSIONS, 3),
                Arguments.of("10_Satellite_Geospatial.xlsx", FileType.SATELLITE_GEOSPATIAL, 8),
                Arguments.of("11_Social_Fin_Inclusion.xlsx", FileType.SOCIAL_FIN_INCLUSION, 5),
                Arguments.of("12_Social_Workforce.xlsx", FileType.SOCIAL_WORKFORCE, 9),
                Arguments.of("13_Governance_Board.xlsx", FileType.GOVERNANCE_BOARD, 5),
                Arguments.of("14_Governance_Incidents.xlsx", FileType.GOVERNANCE_INCIDENTS, 5),
                Arguments.of("15_Ingestion_Audit_Log.xlsx", FileType.INGESTION_AUDIT_LOG, 10));
    }

    @Test
    void everyFileTypeHasASampleFile() {
        assertThat(sampleFiles().map(args -> args.get()[1])).containsExactlyInAnyOrder((Object[]) FileType.values());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sampleFiles")
    void readsEverySampleFile(String fileName, FileType fileType, int expectedRows) throws IOException {
        List<ParsedRow> rows = read(fileName, fileType);

        assertThat(rows).hasSize(expectedRows);
        assertThat(rows.getFirst().rowNumber()).isEqualTo(3);
        // The first column is the record key in every sample and is always populated.
        String firstColumn = fileType.columns().getFirst().column();
        assertThat(rows).allSatisfy(row -> assertThat(row.values().get(firstColumn)).isNotNull());
    }

    @Test
    void convertsScope1Values() throws IOException {
        Map<String, Object> first = read("03_Scope_1_Direct.xlsx", FileType.SCOPE_1_DIRECT).getFirst().values();

        assertThat(first)
                .containsEntry("record_id", "S1-001")
                .containsEntry("period", "2026-08")
                .containsEntry("unit", "litres")
                .containsEntry("source_file", "CBZBANK_SCOPE1_FLEETFUEL_2026-08_v1.csv");
        assertThat((BigDecimal) first.get("activity_data")).isEqualByComparingTo("2450");
        assertThat((BigDecimal) first.get("emissions_tco2e")).isEqualByComparingTo("6.566");
    }

    @Test
    void convertsFinancedEmissionsBooleansAndScores() throws IOException {
        List<ParsedRow> rows = read("06_Financed_Emissions.xlsx", FileType.FINANCED_EMISSIONS);

        assertThat(rows.get(0).values()).containsEntry("mavhu_mrv_enhanced", true).containsEntry("data_quality_score", 3);
        assertThat(rows.get(1).values()).containsEntry("mavhu_mrv_enhanced", false);
        // FE-014 stores its attribution factor in scientific notation (5E-005).
        assertThat((BigDecimal) rows.get(13).values().get("attribution_factor")).isEqualByComparingTo("0.00005");
    }

    @Test
    void skipsTotalRowInDqSummary() throws IOException {
        List<ParsedRow> rows = read("07_DQ_Portfolio_Summary.xlsx", FileType.DQ_PORTFOLIO_SUMMARY);

        assertThat(rows).extracting(row -> row.values().get("asset_class"))
                .doesNotContain("TOTAL / Group")
                .contains("Sub-sovereign debt");
    }

    @Test
    void treatsNotApplicableNumbersAsNull() throws IOException {
        // IE-005 uses "n/a — flat published factor used" as its denominator value.
        Map<String, Object> ie005 = read("08_Insurance_Emissions.xlsx", FileType.INSURANCE_EMISSIONS).get(4).values();

        assertThat(ie005).containsEntry("policy_id", "IE-005").containsEntry("denominator_value_usd", null);
        assertThat((BigDecimal) ie005.get("attribution_factor")).isEqualByComparingTo("0.0699");
    }

    @Test
    void splitsCoordinatesAndParsesDates() throws IOException {
        List<ParsedRow> rows = read("10_Satellite_Geospatial.xlsx", FileType.SATELLITE_GEOSPATIAL);
        Map<String, Object> first = rows.getFirst().values();

        assertThat(first)
                .containsEntry("approx_coordinates", "-17.28, 30.97")
                .containsEntry("satellite_pass_date", LocalDate.of(2026, 7, 15));
        assertThat((BigDecimal) first.get("latitude")).isEqualByComparingTo("-17.28");
        assertThat((BigDecimal) first.get("longitude")).isEqualByComparingTo("30.97");
        // GEO-005: "n/a (non-vegetated site)"
        assertThat(rows.get(4).values()).containsEntry("ndvi", null);
    }

    @Test
    void openIncidentsHaveNoClosureDate() throws IOException {
        List<ParsedRow> rows = read("14_Governance_Incidents.xlsx", FileType.GOVERNANCE_INCIDENTS);

        assertThat(rows.get(0).values()).containsEntry("closure_date", LocalDate.of(2026, 6, 20));
        assertThat(rows.get(2).values()).containsEntry("status", "Open").containsEntry("closure_date", null);
    }

    @Test
    void parsesAuditLogTimestamps() throws IOException {
        Map<String, Object> first = read("15_Ingestion_Audit_Log.xlsx", FileType.INGESTION_AUDIT_LOG).getFirst().values();

        assertThat(first)
                .containsEntry("upload_timestamp", LocalDateTime.of(2026, 9, 2, 8, 14))
                .containsEntry("records_processed", 9)
                .containsEntry("notification_sent", false);
    }

    @Test
    void rejectsFileThatDoesNotMatchFileType() {
        assertThatThrownBy(() -> read("03_Scope_1_Direct.xlsx", FileType.SCOPE_2_ENERGY))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("does not match file type SCOPE_2_ENERGY")
                .hasMessageContaining("Grid Electricity (kWh)");
    }

    @Test
    void rejectsNonExcelContent() {
        InputStream csv = new ByteArrayInputStream("Record ID,Subsidiary\nS1-001,CBZBANK\n".getBytes());

        assertThatThrownBy(() -> reader.read(csv, FileType.SCOPE_1_DIRECT))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("not a readable Excel workbook");
    }

    private List<ParsedRow> read(String fileName, FileType fileType) throws IOException {
        try (InputStream in = new ClassPathResource("sample/" + fileName).getInputStream()) {
            return reader.read(in, fileType);
        }
    }
}
