package io.netty.proxy.http;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Data
class BasicAuthorization {

    private static final String BASIC_PREFIX = "Basic ";

    private String username;

    private String password;

    static BasicAuthorization decode(String authorization) {
        if (StringUtils.isEmpty(authorization)) {
            return null;
        }

        byte[] result = Base64.getDecoder().decode(authorization.startsWith(BASIC_PREFIX)
                ? authorization.substring(BASIC_PREFIX.length())
                : authorization);
        String[] seg = new String(result).split(":");

        BasicAuthorization basicAuth = new BasicAuthorization();
        basicAuth.setUsername(seg[0]);
        basicAuth.setPassword(seg[1]);
        return basicAuth;
    }

}