package com.mediflow.api.repository;

import com.mediflow.api.domain.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByTurnoId(Long turnoId);
}
