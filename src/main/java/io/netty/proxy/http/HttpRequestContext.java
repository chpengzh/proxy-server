package io.netty.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 真实主机的请求头信息
 */
@Data
final class HttpRequestContext {

    /**
     * 是否是CONNECT方法
     */
    private boolean connect;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 目标主机
     */
    private String host;

    /**
     * 目标主机端口
     */
    private int port;

    /**
     * 访问授权信息
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * 是否解析完成
     */
    private boolean completed;

    /**
     * 数据缓存
     */
    private ByteBuf byteBuf = Unpooled.buffer();

    /**
     * 行缓存
     */
    private final StringBuilder lines = new StringBuilder();

    /**
     * 访问描述
     */
    String desc() {
        return "[" + method + "] " + host + ":" + port;
    }

    boolean read(ByteBuf in) {
        while (in.isReadable()) {
            if (completed) {
                return true;
            }

            String line = readLine(in);
            if (line == null) {
                return false;
            }

            if (method == null) {
                String[] seg = line.split(" ");
                method = seg[0];
                connect = method.equalsIgnoreCase("CONNECT");
            }

            if (line.startsWith("Host: ")) {
                String[] arr = line.split(":");
                host = arr[1].trim();
                if (arr.length == 3) {
                    port = Integer.parseInt(arr[2]);
                } else if (connect) {
                    port = 443;
                } else {
                    port = 80;
                }
            }

            if (line.contains(":")) {
                String[] arr = line.split(":");
                headers.put(arr[0], arr[1].trim());
            }

            if (line.isEmpty()) {
                if (host == null || port == 0) {
                    throw new IllegalStateException("cannot find header \'Host\'");
                }
                byteBuf = byteBuf.asReadOnly();
                completed = true;
                break;
            }
        }
        return completed;
    }

    private String readLine(ByteBuf in) {
        while (in.isReadable()) {
            byte b = in.readByte();
            byteBuf.writeByte(b);
            lines.append((char) b);
            int len = lines.length();
            if (len >= 2 && lines.substring(len - 2).equals("\r\n")) {
                String line = lines.substring(0, len - 2);
                lines.delete(0, len);
                return line;
            }
        }
        return null;
    }
}