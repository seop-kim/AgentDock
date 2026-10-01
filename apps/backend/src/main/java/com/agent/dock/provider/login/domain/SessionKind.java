package com.agent.dock.provider.login.domain;

/** 런타임에 대해 실행하는 CLI 명령의 종류. 로그인과 설치가 같은 세션/SSE 배관을 공유한다. */
public enum SessionKind {
    LOGIN,
    INSTALL
}
