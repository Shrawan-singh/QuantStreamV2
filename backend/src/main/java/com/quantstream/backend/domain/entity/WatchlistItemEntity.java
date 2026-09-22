/*
 * ==================================================================================
 * FILE: WatchlistItemEntity.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This represents one item in the user's WATCHLIST — their personal list of
 * stocks they want to keep an eye on.
 *
 * Think of a watchlist like a "favorites" list:
 *   - User adds "AAPL" to their watchlist → a WatchlistItemEntity is created
 *   - User removes "AAPL" → the entity is deleted from the database
 *   - The frontend dashboard shows the user's watchlist stocks with live data
 *
 * UNIQUE CONSTRAINT:
 * The "symbol" column has "unique = true", meaning you can only add each stock
 * once. If "AAPL" is already in the watchlist, trying to add it again will fail.
 * ==================================================================================
 */

package com.quantstream.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * "unique = true" means each stock can only appear ONCE in the watchlist.
     * If user tries to add AAPL twice, the database will reject the second one.
     */
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();  // When the user added this stock to their watchlist

    @Column(length = 255)
    private String notes;  // Optional notes the user can write, e.g., "Watch for earnings report"

    // Empty constructor required by JPA
    public WatchlistItemEntity() {}

    public WatchlistItemEntity(String symbol, String notes) {
        this.symbol = symbol != null ? symbol.toUpperCase() : null;
        this.notes = notes;
        this.addedAt = Instant.now();
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.toUpperCase() : null; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
