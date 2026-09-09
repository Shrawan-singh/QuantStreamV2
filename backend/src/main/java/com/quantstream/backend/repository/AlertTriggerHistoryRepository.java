package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.AlertTriggerHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertTriggerHistoryRepository extends JpaRepository<AlertTriggerHistoryEntity, Long> {
    List<AlertTriggerHistoryEntity> findByAlertIdOrderByTriggeredAtDesc(Long alertId);
    List<AlertTriggerHistoryEntity> findBySymbolOrderByTriggeredAtDesc(String symbol);
    List<AlertTriggerHistoryEntity> findTop50ByOrderByTriggeredAtDesc();
}
