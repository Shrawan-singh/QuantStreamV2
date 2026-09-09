package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AnalyticsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshotEntity, Long> {
    List<AnalyticsSnapshotEntity> findTop50BySymbolOrderByTimestampDesc(String symbol);
}
