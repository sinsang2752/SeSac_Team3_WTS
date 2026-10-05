package com.team.wts.trading.order.adapter.in.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
import com.team.wts.common.error.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 주문 API. (CLAUDE.md §24) */
@Tag(name = "주문", description = "주문 접수·조회·취소")
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
    // 실제 응답 코드는 아래 ResponseEntity.created()가 정한다. 이 애너테이션은 OpenAPI
    // 명세용이다. 없으면 springdoc이 ResponseEntity를 보고 200으로 적는다.
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "주문 접수", description = "시장가(MARKET)는 최신 현재가로 즉시 전량 체결되어 FILLED로 돌아온다. 지정가(LIMIT)는 BUY면 현재가 ≤ 지정가, SELL이면 현재가 ≥ 지정가일 때 즉시 체결되고, 아니면 ACCEPTED로 남아 시세가 조건에 도달하면 자동 체결된다. 체결가는 지정가가 아니라 체결 시점 현재가다. 거절되면 에러 응답이 오고, 거절된 주문도 REJECTED로 주문 내역에 남는다. 같은 Idempotency-Key로 같은 내용을 다시 보내면 주문을 새로 만들지 않고 기존 주문을 그대로 201로 돌려준다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INSUFFICIENT_BALANCE, ErrorCode.INSUFFICIENT_POSITION, ErrorCode.MARKET_PRICE_UNAVAILABLE, ErrorCode.MARKET_PRICE_STALE, ErrorCode.DUPLICATE_ORDER_REQUEST})
    public ResponseEntity<OrderResponse> place(
            @CurrentUserId String userId,
            @Parameter(description = "주문마다 새로 만든 고유 키 (UUID 권장, 1~64자). 같은 키 재요청은 기존 주문을 돌려준다", example = "550e8400-e29b-41d4-a716-446655440000") @RequestHeader(WtsHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
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
    @Operation(summary = "주문 목록", description = "최근 주문부터 돌려준다. status=ACCEPTED로 미체결 주문만 볼 수 있다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED})
    public List<OrderResponse> list(
            @CurrentUserId String userId,
            @Parameter(description = "주문 상태로 거르기. ACCEPTED면 미체결만. 생략하면 전체") @RequestParam(required = false) OrderStatus status) {
        return orderQueryService.findAll(userId, status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "주문 상세", description = "주문 하나를 조회한다. 내 주문이 아니면 404다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.ORDER_NOT_FOUND})
    public OrderResponse one(@CurrentUserId String userId, @Parameter(description = "주문 ID", example = "2") @PathVariable Long orderId) {
        return OrderResponse.from(orderQueryService.findOne(userId, orderId));
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "주문 취소", description = "미체결(ACCEPTED) 주문을 취소하고 묶인 예수금·수량을 푼다. 취소된 주문(status=CANCELLED)을 돌려준다. 이미 FILLED / CANCELLED / REJECTED인 주문은 409다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.ORDER_NOT_FOUND, ErrorCode.INVALID_ORDER_STATE})
    public OrderResponse cancel(@CurrentUserId String userId, @Parameter(description = "주문 ID", example = "2") @PathVariable Long orderId) {
        return OrderResponse.from(cancelOrderService.cancel(new CancelOrderCommand(userId, orderId)));
    }
}
