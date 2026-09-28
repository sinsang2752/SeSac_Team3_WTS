package com.team.wts.user.user.application.command;

/**
 * Mock 로그인 요청. (CLAUDE.md §45 – Command/Query 분리)
 *
 * @param email    사용자 식별 기준. 같은 이메일로 다시 로그인하면 기존 사용자를 돌려준다.
 * @param nickname 신규 가입 시에만 사용한다. 비어 있으면 이메일 앞부분을 쓴다.
 */
public record MockLoginCommand(String email, String nickname) {
}
