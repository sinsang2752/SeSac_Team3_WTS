package com.team.wts.trading.position.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.wts.trading.position.domain.Position;
import com.team.wts.trading.position.domain.PositionRepository;

public interface PositionJpaRepository extends PositionRepository, JpaRepository<Position, Long> {
}
