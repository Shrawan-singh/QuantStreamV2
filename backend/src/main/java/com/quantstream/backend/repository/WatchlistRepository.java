/*
 * ==================================================================================
 * FILE: WatchlistRepository.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Database Access Layer for user favorite stocks (Watchlist).
 *
 * QUERIES PROVIDED:
 * - findBySymbol(symbol):
 *   Checks if a stock is in the user's watchlist.
 * - existsBySymbol(symbol):
 *   Fast boolean check (true/false) to prevent adding duplicate stocks.
 * - deleteBySymbol(symbol):
 *   Removes a stock from the watchlist when the user clicks the "Unpin" button.
 * ==================================================================================
 */

package com.quantstream.backend.repository;

import com.quantstream.backend.domain.entity.WatchlistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistItemEntity, Long> {

    /** Finds a watchlist item by ticker symbol */
    Optional<WatchlistItemEntity> findBySymbol(String symbol);

    /** Returns true if this stock is already on the watchlist */
    boolean existsBySymbol(String symbol);

    /** Deletes an item from the watchlist by symbol */
    void deleteBySymbol(String symbol);
}
