package io.netty.proxy.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chpengzh@foxmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 用户认证
     */
    private final Map<String, String> auth = new HashMap<>();

    /**
     * 是否认证的开关
     */
    private boolean enableAuth = false;

    /**
     * 服务绑定的端口号
     */
    private Integer socks5Port = 8991;

    /**
     * 服务绑定的端口号
     */
    private Integer httpPort = 8992;

    /**
     * 代理地址
     */
    private String proxyHost = "127.0.0.1";

    /**
     * 代理端口
     */
    private Integer proxyPort = 1080;

    private boolean doLog = true;

    private boolean doForwardIP = true;

    private boolean doSendUrlFragment = true;

    private boolean doPreserveHost = false;

    private boolean doPreserveCookies = false;

    private boolean doHandleRedirects = false;

    private boolean useSystemProperties = true;

    private boolean doHandleCompression = false;

    private int connectTimeout = -1;

    private int readTimeout = -1;

    private int connectionRequestTimeout = -1;

    private int maxConnections = -1;

}
