package org.yu.flow.module.auth;

import org.yu.flow.module.auth.dto.LoginDto;

public interface LoginService {
    String login(LoginDto loginDto);
}
