package com.mavhu.ingestion.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "emission_factors", schema = "mavhu")
public class EmissionFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "component", nullable = false, length = 255)
    private String component;

    @Column(name = "factor", nullable = false, precision = 10, scale = 4)
    private BigDecimal factor;

    @Column(name = "gwp", nullable = false, precision = 10, scale = 4)
    private BigDecimal gwp;

    // tooltip/comment text: the emissions factor together with its units
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "unit", nullable = false, length = 255)
    private String unit;
}
