package com.example.scicalculator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Required by JPA — Hibernate needs a no-arg constructor to instantiate entities
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_calculation_step_seq",
                columnNames = {"calculation_id", "sequence_number"}
        )
)
public class CalculationStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculation_id", nullable = false)
    private Calculation calculation;

    @Column(nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operation operation;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal operand;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal resultAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public CalculationStep(Calculation calculation,
                           int sequenceNumber,
                           Operation operation,
                           BigDecimal operand,
                           BigDecimal resultAfter) {
        this.calculation = calculation;
        this.sequenceNumber = sequenceNumber;
        this.operation = operation;
        this.operand = operand;
        this.resultAfter = resultAfter;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
