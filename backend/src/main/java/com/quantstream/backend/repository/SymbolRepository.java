/*
 * ==================================================================================
 * FILE: SymbolRepository.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Database Access Layer for the database table of supported symbols (`SymbolEntity`).
 *
 * METHOD PROVIDED:
 * - findByActiveTrue():
 *   Retrieves only the symbols whose "active" flag is true.
 * ==================================================================================
 */

package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.SymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolRepository extends JpaRepository<SymbolEntity, String> {

    /** Returns all symbols marked as active */
    List<SymbolEntity> findByActiveTrue();
}
