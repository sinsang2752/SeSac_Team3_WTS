package com.team.wts.trading.execution.application.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.trading.account.application.service.AccountService;
import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.execution.domain.ExecutionRepository;

/** 체결 내역 조회. (CLAUDE.md §24 – GET /api/trading/executions) */
@Service
public class ExecutionQueryService {

    private final AccountService accountService;
    private final ExecutionRepository executions;

    public ExecutionQueryService(AccountService accountService, ExecutionRepository executions) {
        this.accountService = accountService;
        this.executions = executions;
    }

    /**
     * {@code readOnly = true}를 쓰지 않는다.
     *
     * <p>계좌는 최초 조회 시점에 만들어진다(ADR-0005). 읽기 전용 트랜잭션은 JDBC 커넥션을
     * read-only로 열기 때문에 그 INSERT가 실패한다. 조회지만 쓰기가 일어날 수 있는 경로다.
     */
    @Transactional
    public List<Execution> findAll(String userId) {
        return executions.findByAccountIdOrderByIdDesc(accountService.getOrOpen(userId).id());
    }
}
