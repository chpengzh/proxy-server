package io.netty.proxy.socks5;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
public class Socks5CommandRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private final EventLoopGroup eventExecutors;

    private final String proxyHost;

    private final int proxyPort;

    Socks5CommandRequestInboundHandler(EventLoopGroup eventExecutors, String proxyHost, int proxyPort) {
        this.eventExecutors = eventExecutors;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
        Socks5AddressType socks5AddressType = msg.dstAddrType();
        if (!msg.type().equals(Socks5CommandType.CONNECT)) {
            log.debug("receive commandRequest type={}", msg.type());
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
            return;
        }
        log.debug("准备连接目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        //添加服务端写客户端的Handler
                        ch.pipeline().addFirst(new Socks5ProxyHandler(new InetSocketAddress(proxyHost, proxyPort)));
                        ch.pipeline().addLast(new Dest2ClientInboundHandler(ctx));
                    }
                });
        ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
        future.addListener((ChannelFutureListener) resultFuture -> {
            if (resultFuture.isSuccess()) {
                log.debug("目标服务器连接成功");
                //添加客户端转发请求到服务端的Handler
                ctx.pipeline().addLast(new Client2DestInboundHandler(resultFuture));
                DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS, socks5AddressType);
                ctx.writeAndFlush(commandResponse);
                ctx.pipeline().remove(Socks5CommandRequestInboundHandler.class);
                ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
            } else {
                log.error("连接目标服务器失败,address={},port={}", msg.dstAddr(), msg.dstPort());
                DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.FAILURE, socks5AddressType);
                ctx.writeAndFlush(commandResponse);
                resultFuture.channel().close();
            }
        });
    }
}
