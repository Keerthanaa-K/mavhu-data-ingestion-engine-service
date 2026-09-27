package com.mavhu.ingestion.processing;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.mavhu.ingestion.processing.ColumnSpec.bool;
import static com.mavhu.ingestion.processing.ColumnSpec.coordinates;
import static com.mavhu.ingestion.processing.ColumnSpec.date;
import static com.mavhu.ingestion.processing.ColumnSpec.decimal;
import static com.mavhu.ingestion.processing.ColumnSpec.integer;
import static com.mavhu.ingestion.processing.ColumnSpec.text;
import static com.mavhu.ingestion.processing.ColumnSpec.timestamp;

/**
 * The fixed set of ESG file types a user can upload (see resources/sample), each with the
 * table it is ingested into and the header -> column mapping. DDL: db/ddl/esg_file_ingestion.sql.
 */
public enum FileType {

    ENTITY_REFERENCE("Entity Reference", "mavhu.esg_entity_reference", List.of(
            text("Entity Code", "entity_code"),
            text("Subsidiary", "subsidiary"),
            text("Business Segment", "business_segment"),
            text("Regulator", "regulator"),
            text("PCAF Part(s) Applicable", "pcaf_parts_applicable"),
            text("In This Workbook", "in_this_workbook"),
            text("Notes", "notes"))),

    SCOPE_1_DIRECT("Scope 1 - Direct Emissions", "mavhu.esg_scope1_direct_emissions", List.of(
            text("Record ID", "record_id"),
            text("Subsidiary", "subsidiary"),
            text("Site/Branch", "site"),
            text("Period", "period"),
            text("Dataset Type", "dataset_type"),
            text("Fuel/Refrigerant Type", "fuel_or_refrigerant_type"),
            decimal("Activity Data", "activity_data"),
            text("Unit", "unit"),
            decimal("Emission Factor (kgCO2e/unit)", "emission_factor_kgco2e_per_unit"),
            decimal("Emissions (kgCO2e)", "emissions_kgco2e"),
            decimal("Emissions (tCO2e)", "emissions_tco2e"),
            text("Source File", "source_file"))),

    SCOPE_2_ENERGY("Scope 2 - Purchased Energy", "mavhu.esg_scope2_energy_emissions", List.of(
            text("Record ID", "record_id"),
            text("Subsidiary", "subsidiary"),
            text("Site", "site"),
            text("Period", "period"),
            decimal("Grid Electricity (kWh)", "grid_electricity_kwh"),
            decimal("Location-based Factor (kgCO2e/kWh)", "location_based_factor_kgco2e_per_kwh"),
            decimal("Location-based Emissions (tCO2e)", "location_based_emissions_tco2e"),
            decimal("On-site Solar Generated (kWh)", "onsite_solar_generated_kwh"),
            decimal("Market-based Residual Mix Factor (kgCO2e/kWh)", "market_based_residual_mix_factor_kgco2e_per_kwh"),
            decimal("Market-based Emissions (tCO2e)", "market_based_emissions_tco2e"),
            text("Source File", "source_file"))),

    SCOPE_3_OPERATIONAL("Scope 3 - Operational", "mavhu.esg_scope3_operational_emissions", List.of(
            text("Record ID", "record_id"),
            text("Subsidiary", "subsidiary"),
            text("Category", "category"),
            text("Description", "description"),
            text("Period", "period"),
            text("Method", "method"),
            decimal("Activity/Spend Data", "activity_or_spend_data"),
            text("Unit", "unit"),
            decimal("Emission Factor", "emission_factor_kgco2e_per_unit"),
            decimal("Emissions (tCO2e)", "emissions_tco2e"),
            text("Source File", "source_file"))),

    FINANCED_EMISSIONS("Financed Emissions (PCAF Part A)", "mavhu.esg_financed_emissions", List.of(
            text("Loan/Investment ID", "position_id"),
            text("Asset Class", "asset_class"),
            text("Subsidiary", "subsidiary"),
            text("Borrower / Issuer (fictional)", "borrower_or_issuer"),
            text("Sector", "sector"),
            text("Listed Status", "listed_status"),
            decimal("Outstanding Amount (US$)", "outstanding_amount_usd"),
            text("Denominator Type", "denominator_type"),
            decimal("Denominator Value (US$)", "denominator_value_usd"),
            decimal("Attribution Factor", "attribution_factor"),
            decimal("Borrower/Issuer Total Emissions (tCO2e)", "borrower_total_emissions_tco2e"),
            decimal("Financed Emissions (tCO2e)", "financed_emissions_tco2e"),
            integer("Data Quality Score (1-5)", "data_quality_score"),
            bool("MAvHU MRV Enhanced", "mavhu_mrv_enhanced"),
            text("Source File", "source_file"))),

    DQ_PORTFOLIO_SUMMARY("Data Quality Portfolio Summary", "mavhu.esg_dq_portfolio_summary", List.of(
            text("Asset Class", "asset_class"),
            integer("Number of Positions", "number_of_positions"),
            decimal("Total Outstanding (US$)", "total_outstanding_usd"),
            decimal("Total Financed Emissions (tCO2e)", "total_financed_emissions_tco2e"),
            decimal("Emissions-Weighted Avg DQ Score", "emissions_weighted_avg_dq_score"))),

    INSURANCE_EMISSIONS("Insurance-Associated Emissions (PCAF Part C)", "mavhu.esg_insurance_emissions", List.of(
            text("Policy ID", "policy_id"),
            text("Segment", "segment"),
            text("Subsidiary", "subsidiary"),
            text("Client (fictional)", "client"),
            text("Sector", "sector"),
            decimal("Gross Written Premium (US$)", "gross_written_premium_usd"),
            text("Denominator Type", "denominator_type"),
            decimal("Denominator Value (US$)", "denominator_value_usd"),
            decimal("Attribution Factor", "attribution_factor"),
            decimal("Client Total Emissions (tCO2e)", "client_total_emissions_tco2e"),
            decimal("Insurance-Associated Emissions (tCO2e)", "insurance_associated_emissions_tco2e"),
            integer("Data Quality Score", "data_quality_score"),
            text("Source File", "source_file"))),

    FACILITATED_EMISSIONS("Facilitated Emissions (PCAF Part B)", "mavhu.esg_facilitated_emissions", List.of(
            text("Transaction ID", "transaction_id"),
            text("Issuer (fictional)", "issuer"),
            text("Listed Status", "listed_status"),
            text("Sector", "sector"),
            text("Transaction Type", "transaction_type"),
            decimal("Total Raised Amount (US$)", "total_raised_amount_usd"),
            decimal("League Table Credit", "league_table_credit"),
            decimal("Facilitated Amount (US$)", "facilitated_amount_usd"),
            decimal("Company Value (US$)", "company_value_usd"),
            decimal("Weighting Factor", "weighting_factor"),
            decimal("Issuer Annual Emissions (tCO2e)", "issuer_annual_emissions_tco2e"),
            decimal("Facilitated Emissions (tCO2e)", "facilitated_emissions_tco2e"),
            integer("Transaction Year", "transaction_year"),
            text("Source File", "source_file"))),

    SATELLITE_GEOSPATIAL("Satellite & Geospatial", "mavhu.esg_satellite_geospatial", List.of(
            text("Record ID", "record_id"),
            text("Linked Borrower/Site", "linked_borrower_or_site"),
            text("Subsidiary Portfolio", "subsidiary_portfolio"),
            text("District", "district"),
            coordinates("Approx. Coordinates", "approx_coordinates"),
            date("Satellite Pass Date", "satellite_pass_date"),
            text("Data Source", "data_source"),
            decimal("NDVI", "ndvi"),
            text("Land Use Classification", "land_use_classification"),
            decimal("Estimated Area (ha)", "estimated_area_ha"),
            text("Deforestation Flag", "deforestation_flag"),
            text("Flood Risk", "flood_risk"),
            text("Drought Stress", "drought_stress"),
            text("Ingestion File", "source_file"))),

    SOCIAL_FIN_INCLUSION("Social - Financial Inclusion", "mavhu.esg_social_financial_inclusion", List.of(
            text("Record ID", "record_id"),
            text("Subsidiary", "subsidiary"),
            text("Programme/Product", "programme_or_product"),
            integer("Beneficiary Count", "beneficiary_count"),
            decimal("Female Beneficiaries", "female_beneficiaries_ratio"),
            decimal("Total Disbursed (US$)", "total_disbursed_usd"),
            text("Geography", "geography"),
            text("SDG Alignment", "sdg_alignment"),
            decimal("Repayment Rate", "repayment_rate"),
            text("Period", "period"),
            text("Source File", "source_file"))),

    SOCIAL_WORKFORCE("Social - Workforce", "mavhu.esg_social_workforce", List.of(
            text("Record ID", "record_id"),
            text("Subsidiary", "subsidiary"),
            text("Period", "period"),
            integer("Total Headcount", "total_headcount"),
            decimal("Female %", "female_ratio"),
            decimal("Avg Training Hrs/Employee", "avg_training_hours_per_employee"),
            decimal("Lost Time Injury Rate", "lost_time_injury_rate"),
            decimal("Voluntary Turnover %", "voluntary_turnover_ratio"),
            decimal("Local Hire %", "local_hire_ratio"),
            text("Source File", "source_file"))),

    GOVERNANCE_BOARD("Governance - Board", "mavhu.esg_governance_board", List.of(
            text("Entity", "entity"),
            integer("Board Size", "board_size"),
            decimal("Independent Directors %", "independent_directors_ratio"),
            decimal("Female Directors %", "female_directors_ratio"),
            bool("ESG/Sustainability Committee", "has_esg_committee"),
            text("Key Policies in Place", "key_policies"),
            text("Last Policy Review", "last_policy_review"),
            text("Source File", "source_file"))),

    GOVERNANCE_INCIDENTS("Governance - Incidents", "mavhu.esg_governance_incidents", List.of(
            text("Incident ID", "incident_id"),
            text("Subsidiary", "subsidiary"),
            date("Date Reported", "date_reported"),
            text("Category", "category"),
            text("Description", "description"),
            text("Severity", "severity"),
            text("Status", "status"),
            text("Corrective Action", "corrective_action"),
            date("Closure Date", "closure_date"),
            text("Source File", "source_file"))),

    INGESTION_AUDIT_LOG("Ingestion Audit Log", "mavhu.esg_ingestion_audit_log", List.of(
            text("Batch ID", "batch_id"),
            text("Source File Name", "source_file_name"),
            text("Ingestion Channel", "ingestion_channel"),
            text("Subsidiary", "subsidiary"),
            timestamp("Upload Timestamp", "upload_timestamp"),
            integer("Records Processed", "records_processed"),
            text("Validation Status", "validation_status"),
            text("Error Details", "error_details"),
            bool("Notification Sent", "notification_sent")));

    private final String displayName;
    private final String tableName;
    private final List<ColumnSpec> columns;

    FileType(String displayName, String tableName, List<ColumnSpec> columns) {
        this.displayName = displayName;
        this.tableName = tableName;
        this.columns = columns;
    }

    public String displayName() {
        return displayName;
    }

    public String tableName() {
        return tableName;
    }

    public List<ColumnSpec> columns() {
        return columns;
    }

    /** S3 partition segment, e.g. SCOPE_1_DIRECT -> scope_1_direct. */
    public String pathSegment() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Accepts the enum name in any case, with '-' or ' ' in place of '_'. */
    public static FileType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fileType is required. Supported: " + supported());
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported fileType '" + value + "'. Supported: " + supported()));
    }

    private static String supported() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
