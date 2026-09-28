package com.team.wts.user.user.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 사용자 애그리거트. (CLAUDE.md §23 users)
 *
 * <p>DDD-lite로 간다. 도메인 모델과 영속 모델을 따로 두고 매퍼를 만드는 대신
 * 엔티티 하나에 JPA 매핑을 붙였다. MVP 단계에서 매퍼 중복은 이득보다 비용이 크다 (§52).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "email", length = 255, nullable = false, updatable = false)
    private String email;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    // Instant의 기본 매핑은 MySQL에서 timestamp가 되어 DDL의 datetime(6)과 어긋난다.
    // ddl-auto=validate를 통과하도록 매핑을 명시한다. 값 자체는 UTC로 기록된다 (§43).
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 전용. 애플리케이션 코드에서 호출하지 않는다. */
    protected User() {
    }

    private User(String id, String email, String nickname) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
    }

    /** 신규 사용자를 만든다. ID는 서비스 경계를 넘나들기 때문에 UUID를 쓴다. */
    public static User register(String email, String nickname) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(nickname, "nickname");
        return new User(UUID.randomUUID().toString(), email.trim().toLowerCase(), nickname.trim());
    }

    public void changeNickname(String nickname) {
        this.nickname = Objects.requireNonNull(nickname, "nickname").trim();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String nickname() {
        return nickname;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
