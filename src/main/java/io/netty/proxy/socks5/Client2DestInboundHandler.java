package io.netty.proxy.socks5;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
public class Client2DestInboundHandler extends ChannelInboundHandlerAdapter {

    private final ChannelFuture dstChannelFuture;

    Client2DestInboundHandler(ChannelFuture dstChannelFuture) {
        this.dstChannelFuture = dstChannelFuture;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        dstChannelFuture.channel().writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.trace("客户端与代理服务器的连接已经断开，即将断开代理服务器和目标服务器的连接");
        dstChannelFuture.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client2DestInboundHandler exception", cause);
    }
}
