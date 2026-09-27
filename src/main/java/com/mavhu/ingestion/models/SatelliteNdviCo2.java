package com.mavhu.ingestion.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "satellite_ndvi_co2", schema = "mavhu")
public class SatelliteNdviCo2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estate_id", nullable = false)
    private Estate estate;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "ndvi_mean", precision = 16, scale = 15)
    private BigDecimal ndviMean;

    @Column(name = "ndvi_min", precision = 16, scale = 15)
    private BigDecimal ndviMin;

    @Column(name = "ndvi_max", precision = 16, scale = 15)
    private BigDecimal ndviMax;

    @Column(name = "ndvi_std", precision = 16, scale = 15)
    private BigDecimal ndviStd;

    @Column(name = "ndwi_mean", precision = 16, scale = 15)
    private BigDecimal ndwiMean;

    @Column(name = "ndwi_min", precision = 16, scale = 15)
    private BigDecimal ndwiMin;

    @Column(name = "ndwi_max", precision = 16, scale = 15)
    private BigDecimal ndwiMax;

    @Column(name = "ndwi_std", precision = 16, scale = 15)
    private BigDecimal ndwiStd;

    @Column(name = "ndbi_mean", precision = 16, scale = 15)
    private BigDecimal ndbiMean;

    @Column(name = "ndbi_min", precision = 16, scale = 15)
    private BigDecimal ndbiMin;

    @Column(name = "ndbi_max", precision = 16, scale = 15)
    private BigDecimal ndbiMax;

    @Column(name = "ndbi_std", precision = 16, scale = 15)
    private BigDecimal ndbiStd;

    // 45.8 * (ndvi_max ^ 2.1)
    @Column(name = "above_ground_biomass", precision = 7, scale = 4)
    private BigDecimal aboveGroundBiomass;

    // 0.24 * above_ground_biomass
    @Column(name = "below_ground_biomass", precision = 7, scale = 4)
    private BigDecimal belowGroundBiomass;

    // (above_ground_biomass + below_ground_biomass) * 0.47
    @Column(name = "biomass_carbon", precision = 7, scale = 4)
    private BigDecimal biomassCarbon;

    // biomass_carbon * 3.67
    @Column(name = "biomass_co2", precision = 7, scale = 4)
    private BigDecimal biomassCo2;

    // biomass_co2 * estates.hectares
    @Column(name = "biomass_co2_total", precision = 10, scale = 4)
    private BigDecimal biomassCo2Total;

    // (82.77 * ndvi_max) - 1.39
    @Column(name = "soil_organic_carbon", precision = 7, scale = 4)
    private BigDecimal soilOrganicCarbon;

    // soil_organic_carbon * 3.67
    @Column(name = "soil_organic_carbon_co2", precision = 7, scale = 4)
    private BigDecimal soilOrganicCarbonCo2;

    // soil_organic_carbon_co2 * estates.hectares
    @Column(name = "soil_organic_carbon_co2_total", precision = 10, scale = 4)
    private BigDecimal soilOrganicCarbonCo2Total;

    // biomass_co2_total + soil_organic_carbon_co2_total
    @Column(name = "net_co2_stock", precision = 10, scale = 4)
    private BigDecimal netCo2Stock;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
