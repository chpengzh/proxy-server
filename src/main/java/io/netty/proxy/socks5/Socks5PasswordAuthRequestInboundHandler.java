package io.netty.proxy.socks5;

import io.netty.proxy.properties.AppProperties;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author chpengzh@foxmail.com
 */
@Slf4j
@AllArgsConstructor
public class Socks5PasswordAuthRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest> {

    private final AppProperties properties;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) {
        //认证成功
        if (Objects.equals(properties.getAuth().get(msg.username()), msg.password().trim())) {
            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
            ctx.writeAndFlush(passwordAuthResponse);
            ctx.pipeline().remove(this);
            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
            return;
        }
        Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
        //发送鉴权失败消息，完成后关闭channel
        ctx.writeAndFlush(passwordAuthResponse).addListener(ChannelFutureListener.CLOSE);
    }
}
