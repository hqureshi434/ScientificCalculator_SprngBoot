package com.example.scicalculator.repository;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalculationRepository extends JpaRepository<Calculation, Long> {
    List<Calculation> findByOwner(User owner);

    Optional<Calculation> findByIdAndOwnerUsername(Long id, String username);
}
