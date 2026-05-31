package com.example.scicalculator.repository;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalculationStepRepository extends JpaRepository<CalculationStep, Long> {
    List<CalculationStep> findByCalculationOrderBySequenceNumberAsc(Calculation calculation);

    Optional<CalculationStep> findTopByCalculationOrderBySequenceNumberDesc(Calculation calculation);
}
