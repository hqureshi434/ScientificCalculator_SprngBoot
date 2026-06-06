package com.example.scicalculator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Required by JPA — Hibernate needs a no-arg constructor to instantiate entities
public class Calculation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private User owner;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal currentValue;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    public Calculation(User owner) {
        this.owner = owner;
        this.currentValue = BigDecimal.ZERO;
    }

    public Calculation(User owner, String name) {
        this(owner);
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
