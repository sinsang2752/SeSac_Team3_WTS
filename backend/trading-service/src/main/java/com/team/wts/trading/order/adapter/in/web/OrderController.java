package com.team.wts.trading.order.adapter.in.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.web.CurrentUserId;
import com.team.wts.common.web.WtsHeaders;
import com.team.wts.trading.order.adapter.in.web.dto.OrderResponse;
import com.team.wts.trading.order.adapter.in.web.dto.PlaceOrderRequest;
import com.team.wts.trading.order.application.command.CancelOrderCommand;
import com.team.wts.trading.order.application.query.OrderQueryService;
import com.team.wts.trading.order.application.service.CancelOrderService;
import com.team.wts.trading.order.application.service.PlaceOrderService;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderStatus;

import jakarta.validation.Valid;

/** 주문 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/trading/orders")
public class OrderController {

    private final PlaceOrderService placeOrderService;
    private final CancelOrderService cancelOrderService;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderService placeOrderService,
            CancelOrderService cancelOrderService, OrderQueryService orderQueryService) {
        this.placeOrderService = placeOrderService;
        this.cancelOrderService = cancelOrderService;
        this.orderQueryService = orderQueryService;
    }

    /**
     * 주문을 낸다. {@code Idempotency-Key} 헤더가 필수다 (CLAUDE.md §14).
     *
     * <p>거절된 주문은 기록으로 남지만 응답은 에러다. 주문이 성립하지 않았다는 사실을
     * 200번대로 알리면 클라이언트가 성공과 구분하기 어렵다.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @CurrentUserId String userId,
            @RequestHeader(WtsHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    WtsHeaders.IDEMPOTENCY_KEY + " 헤더는 1~64자여야 합니다.");
        }

        Order order = placeOrderService.place(request.toCommand(userId, idempotencyKey));

        if (order.status() == OrderStatus.REJECTED) {
            // 거절 주문은 이미 커밋됐다. 여기서 던지는 예외는 응답 코드만 바꾼다.
            throw new DomainException(ErrorCode.valueOf(order.rejectReason()));
        }

        return ResponseEntity.created(URI.create("/api/trading/orders/" + order.id()))
                .body(OrderResponse.from(order));
    }

    /** 주문 목록. {@code status=ACCEPTED} 로 미체결 주문만 볼 수 있다. */
    @GetMapping
    public List<OrderResponse> list(
            @CurrentUserId String userId,
            @RequestParam(required = false) OrderStatus status) {
        return orderQueryService.findAll(userId, status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{orderId}")
    public OrderResponse one(@CurrentUserId String userId, @PathVariable Long orderId) {
        return OrderResponse.from(orderQueryService.findOne(userId, orderId));
    }

    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(@CurrentUserId String userId, @PathVariable Long orderId) {
        return OrderResponse.from(cancelOrderService.cancel(new CancelOrderCommand(userId, orderId)));
    }
}
