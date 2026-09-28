package com.team.wts.trading.order.application.command;

/** 주문 취소 명령. (CLAUDE.md §45) */
public record CancelOrderCommand(String userId, Long orderId) {
}
