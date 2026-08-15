package com.mediflow.api.repository;

import com.mediflow.api.domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    Optional<Paciente> findByRut(String rut);
    boolean existsByRut(String rut);
}
