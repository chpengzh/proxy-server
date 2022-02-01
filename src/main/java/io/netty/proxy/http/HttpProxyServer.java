package io.netty.proxy.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.proxy.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * <h2>HTTP/HTTPS代理服务器</h2>
 * 三个角色：真实客户端，代理客户端，目标主机
 * <pre>
 *            ------>> 代理客户端  --------->>
 * 真实客户端                                 目标主机
 *         <<-------- 代理客户端  <<--------
 * </pre>
 */
@Slf4j
@Component
public class HttpProxyServer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private AppProperties properties;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new HttpProxyClientHandler());
                        }
                    })
                    .bind(properties.getHttpPort()).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    /**
     * 代理客户端去请求目标主机
     */
    private class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

        /**
         * 代理服务端channel
         */
        private Channel clientChannel;

        /**
         * 目标主机channel
         */
        private Channel remoteChannel;

        /**
         * 解析真实客户端的request
         */
        private final HttpRequestContext request = new HttpRequestContext();

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            clientChannel = ctx.channel();
        }

        /**
         * 注意在真实客户端请求一个页面的时候，此方法不止调用一次，这是TCP底层决定的（拆包/粘包）
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf input = (ByteBuf) msg;
            try {
                // 1.读包与完整性校验
                if (!request.isCompleted()) {
                    request.read(input);
                    if (request.isCompleted()) {
                        // 解包完毕
                        clientChannel.config().setAutoRead(false);
                    } else {
                        // 遇到了拆包，等待下一次读包结束
                        return;
                    }

                } else if (remoteChannel.isActive()) {
                    // 如果本次请求中已经解析过request了, 说明代理客户端已经在目标主机建立了连接，直接将真实客户端的数据写给目标主机
                    remoteChannel.writeAndFlush(msg);
                    return;
                }

                // 2.登录授权校验
                // https://developer.mozilla.org/zh-CN/docs/Web/HTTP/requests/Proxy-Authorization
                BasicAuthorization auth = BasicAuthorization.decode(request.getAuthorization());
                if (auth == null) {
                    log.error("{} deny by empty authorization", request.desc());
                    clientChannel.close();
                    return;
                } else if (!Objects.equals(properties.getAuth().get(auth.getUsername()), auth.getPassword())) {
                    log.error("{} deny by bad authorization user={} password={}",
                            request.desc(),
                            auth.getUsername(),
                            auth.getPassword());
                    clientChannel.close();
                    return;
                } else {
                    log.info("{} by user={}", request.desc(), auth.getUsername());
                }
            } catch (Throwable err) {
                log.error("Unexpected error " + err.toString());
                clientChannel.close();
                return;
            }

            if (request.isConnect()) {
                connEstablished();
            }

            remoteChannel = connectToServer(
                    // 后端要经过一次socks5代理
                    new Socks5ProxyHandler(new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort())),
                    new ChannelInboundHandlerAdapter() {

                        private Channel remoteChannel;

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            this.remoteChannel = ctx.channel();
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            clientChannel.writeAndFlush(msg);
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            flushAndClose(clientChannel);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
                            log.error("Unexpected error " + e.toString());
                            flushAndClose(remoteChannel);
                        }
                    }).channel();
        }


        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            flushAndClose(remoteChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
            log.error("Unexpected error " + e.toString());
            flushAndClose(clientChannel);
        }

        /**
         * 与后端进行建连操作
         */
        private ChannelFuture connectToServer(ChannelHandler... proxyHandler) {
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(clientChannel.eventLoop())
                    .channel(clientChannel.getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            for (ChannelHandler handler : proxyHandler) {
                                ch.pipeline().addLast(handler);
                            }
                        }
                    });
            ChannelFuture future = clientBootstrap.connect(request.getHost(), request.getPort());
            future.addListener((ChannelFutureListener) futureResult -> {
                if (futureResult.isSuccess()) {
                    // connection is ready, enable AutoRead
                    clientChannel.config().setAutoRead(true);
                    // forward request and remaining bytes
                    if (!request.isConnect()) {
                        //in读取一次缓冲区就没有了，request.byteBuf里面存了一份
                        remoteChannel.writeAndFlush(request.getByteBuf());
                    }
                } else {
                    clientChannel.close();
                }
            });
            return future;
        }

        /**
         * 与客户端提示建联成功
         */
        private void connEstablished() {
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
        }

        private void flushAndClose(Channel ch) {
            if (ch != null && ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

}

