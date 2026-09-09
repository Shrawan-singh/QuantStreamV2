package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistItemEntity, Long> {
    Optional<WatchlistItemEntity> findBySymbol(String symbol);
    boolean existsBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}
