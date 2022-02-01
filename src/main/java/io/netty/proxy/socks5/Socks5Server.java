package io.netty.proxy.socks5;

import io.netty.proxy.properties.AppProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
@Component
public class Socks5Server implements ApplicationListener<ContextRefreshedEvent> {

    private final AppProperties properties;

    public Socks5Server(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        EventLoopGroup clientWorkGroup = new NioEventLoopGroup();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        try {
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // socks5响应最后一个encode
                            pipeline.addLast(Socks5ServerEncoder.DEFAULT);

                            // 处理socks5初始化请求
                            pipeline.addLast(new Socks5InitialRequestDecoder());
                            pipeline.addLast(new Socks5InitialRequestInboundHandler(properties));

                            // 处理认证请求
                            if (properties.isEnableAuth()) {
                                pipeline.addLast(new Socks5PasswordAuthRequestDecoder());
                                pipeline.addLast(new Socks5PasswordAuthRequestInboundHandler(properties));
                            }

                            // 处理connection请求
                            pipeline.addLast(new Socks5CommandRequestDecoder());
                            pipeline.addLast(new Socks5CommandRequestInboundHandler(clientWorkGroup,
                                    properties.getProxyHost(),
                                    properties.getProxyPort()));
                        }
                    });
            ChannelFuture future = bootstrap.bind(properties.getSocks5Port()).sync();
            log.info("socks5 netty server has started on port {}", properties.getSocks5Port());
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.info("socks5 nett server terminated by interrupted");
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
