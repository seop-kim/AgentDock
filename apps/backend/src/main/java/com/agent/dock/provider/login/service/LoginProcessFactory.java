package com.agent.dock.provider.login.service;

import com.agent.dock.provider.login.interfaces.LoginProcess;
import org.springframework.stereotype.Component;

/** 세션마다 새 LoginProcess 를 만든다. 테스트에서는 가짜 프로세스로 대체한다. */
@Component
public class LoginProcessFactory {
    public LoginProcess create() {
        return new PipeLoginProcess();
    }
}
