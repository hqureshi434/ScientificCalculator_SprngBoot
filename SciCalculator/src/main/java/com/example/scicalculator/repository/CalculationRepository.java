package com.example.scicalculator.repository;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalculationRepository extends JpaRepository<Calculation, Long> {
    List<Calculation> findByOwner(User owner);
}
