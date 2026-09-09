package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.SymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolRepository extends JpaRepository<SymbolEntity, String> {
    List<SymbolEntity> findByActiveTrue();
}
