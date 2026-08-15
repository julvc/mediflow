package com.mediflow.api.repository;

import com.mediflow.api.domain.Profesional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfesionalRepository extends JpaRepository<Profesional, Long> {
    boolean existsByRut(String rut);
}
