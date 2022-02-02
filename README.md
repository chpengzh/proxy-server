# socks5代理网关

总所周知sslocal提供的socks5本地代理服务是没有安全策略的，本项目是自用安全代理服务网关;

```
mvn clean install
```

启动代理服务

```
nohup java -jar target/socks5-netty-1.0.0-SNAPSHOT.jar \
  --app.auth.some-user=some.password \
  --app.enable-auth=true \
  --proxy-host=127.0.0.1 \
  --proxy-port=1080 \
  --http-port=8991 \
  > proxy.log 1>&2 &
```

可以使用




