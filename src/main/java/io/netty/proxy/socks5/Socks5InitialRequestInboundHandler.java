package io.netty.proxy.socks5;

import io.netty.proxy.properties.AppProperties;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
@AllArgsConstructor
public class Socks5InitialRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {

    private final AppProperties properties;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) {
        log.debug("初始化socks5链接");
        boolean failure = msg.decoderResult().isFailure();
        if (failure) {
            log.error("初始化socks5失败，请检查是否是socks5协议");
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
            return;
        }
        if (properties.isEnableAuth()) {
            Socks5InitialResponse socks5InitialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);
            ctx.writeAndFlush(socks5InitialResponse);
        } else {
            Socks5InitialResponse socks5InitialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
            ctx.writeAndFlush(socks5InitialResponse);
        }
        ctx.pipeline().remove(this);
        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
    }
}
