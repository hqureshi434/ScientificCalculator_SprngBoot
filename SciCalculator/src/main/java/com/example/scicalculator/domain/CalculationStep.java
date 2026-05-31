package com.example.scicalculator.domain;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Audited
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

    @Column(nullable = false)
    private BigDecimal operand;

    @Column(nullable = false)
    private BigDecimal resultAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CalculationStep() {
        // Required by JPA — Hibernate needs a no-arg constructor to instantiate entities
    }

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

    public Long getId() {
        return id;
    }

    public Calculation getCalculation() {
        return calculation;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public Operation getOperation() {
        return operation;
    }

    public BigDecimal getOperand() {
        return operand;
    }

    public BigDecimal getResultAfter() {
        return resultAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
