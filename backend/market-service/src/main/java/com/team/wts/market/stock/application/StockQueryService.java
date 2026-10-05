package com.team.wts.market.stock.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.market.stock.domain.Market;
import com.team.wts.market.stock.domain.Stock;
import com.team.wts.market.stock.domain.StockRepository;

/** 종목 조회. (CLAUDE.md §24, §57.1) */
@Service
public class StockQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_SYMBOLS = 100;

    private final StockRepository stocks;

    public StockQueryService(StockRepository stocks) {
        this.stocks = stocks;
    }

    /** 상장 종목 검색. keyword가 비면 전체를 이름순으로 돌려준다. */
    public Page<Stock> search(String keyword, Market market, int page, int size) {
        if (page < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "page 는 0 이상이어야 합니다: " + page);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "size 는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다: " + size);
        }
        String normalized = keyword == null || keyword.isBlank() ? null : escapeLike(keyword.strip());
        return stocks.search(normalized, market, PageRequest.of(page, size));
    }

    /**
     * 종목코드 여러 개를 한 번에. 거래 화면이 주문 · 체결 · 보유 종목의 이름을 그릴 때 쓴다.
     * 상장폐지 종목도 돌려준다. 없는 종목코드는 빠진다. 순서는 요청한 순서다.
     */
    public List<Stock> findBySymbols(List<String> symbols) {
        Set<String> unique = new LinkedHashSet<>(symbols);
        if (unique.size() > MAX_SYMBOLS) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "symbols 는 한 번에 " + MAX_SYMBOLS + "개까지 조회할 수 있습니다: " + unique.size());
        }
        Map<String, Stock> found = stocks.findBySymbolIn(unique).stream()
                .collect(Collectors.toMap(Stock::symbol, Function.identity()));
        return unique.stream().map(found::get).filter(stock -> stock != null).toList();
    }

    public Stock getBySymbol(String symbol) {
        return stocks.findBySymbol(symbol)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.SYMBOL_NOT_FOUND, "종목을 찾을 수 없습니다: " + symbol));
    }

    /** 검색어의 % · _ 를 글자 그대로 찾게 한다. 이스케이프 문자는 쿼리와 같은 \ 다. */
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
