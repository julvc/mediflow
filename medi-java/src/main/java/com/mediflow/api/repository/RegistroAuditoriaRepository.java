package com.mediflow.api.repository;

import com.mediflow.api.domain.RegistroAuditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    List<RegistroAuditoria> findAllByOrderByCreadoEnDesc(Pageable pageable);
}
