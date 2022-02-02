package io.netty.proxy.socks5;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
public class Dest2ClientInboundHandler extends ChannelInboundHandlerAdapter {

    private final ChannelHandlerContext clientChannelHandlerContext;

    Dest2ClientInboundHandler(ChannelHandlerContext clientChannelHandlerContext) {
        this.clientChannelHandlerContext = clientChannelHandlerContext;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("开始写回客户端");
        clientChannelHandlerContext.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.trace("代理服务器和目标服务器的连接已经断开，即将断开客户端和代理服务器的连接");
        clientChannelHandlerContext.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Dest2ClientInboundHandler exception " + cause);
    }
}
