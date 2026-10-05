package com.team.wts.user.watchlist.domain;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.team.wts.common.market.StockSymbol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 관심종목 한 줄. (CLAUDE.md §23 watchlists)
 *
 * <p>종목이 실제로 존재하는지는 확인하지 않는다. 종목 마스터는 market-service의 것이고,
 * 확인하려면 서비스 간 동기 호출이 필요하다 (§6.2가 막는 방향이다).
 * 여기서는 <b>형식</b>만 본다. 화면은 이 목록을 market-service의 종목 목록과 맞춰 보여준다.
 */
@Entity
@Table(name = "watchlists")
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "symbol", length = 20, nullable = false, updatable = false)
    private String symbol;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용. */
    protected WatchlistItem() {
    }

    private WatchlistItem(String userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
    }

    public static WatchlistItem of(String userId, String symbol) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(symbol, "symbol");
        if (!StockSymbol.isValid(symbol)) {
            throw new IllegalArgumentException(StockSymbol.FORMAT_MESSAGE + " " + symbol);
        }
        return new WatchlistItem(userId, symbol);
    }

    /** 종목코드 형식이 맞는가. 컨트롤러가 400을 내려면 예외 없이 물어볼 수 있어야 한다. */
    public static boolean isValidSymbol(String symbol) {
        return StockSymbol.isValid(symbol);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String symbol() {
        return symbol;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
