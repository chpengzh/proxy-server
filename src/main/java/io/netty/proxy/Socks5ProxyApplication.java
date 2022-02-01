package io.netty.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author chen.pengzhi (chpengzh@foxmail.com)
 */
@SpringBootApplication
public class Socks5ProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(Socks5ProxyApplication.class, args);
    }

}
