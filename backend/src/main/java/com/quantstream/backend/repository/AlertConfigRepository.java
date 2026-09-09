package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AlertConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfigEntity, Long> {
    List<AlertConfigEntity> findBySymbol(String symbol);
    List<AlertConfigEntity> findByEnabledTrue();
}
