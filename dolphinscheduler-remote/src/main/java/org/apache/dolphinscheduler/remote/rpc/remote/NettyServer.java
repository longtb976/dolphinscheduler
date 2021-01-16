package org.apache.dolphinscheduler.remote.rpc.remote;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import org.apache.dolphinscheduler.remote.decoder.NettyDecoder;
import org.apache.dolphinscheduler.remote.config.NettyServerConfig;

import org.apache.dolphinscheduler.remote.decoder.NettyEncoder;

import org.apache.dolphinscheduler.remote.rpc.common.RpcRequest;
import org.apache.dolphinscheduler.remote.rpc.common.RpcResponse;
import org.apache.dolphinscheduler.remote.utils.Constants;
import org.apache.dolphinscheduler.remote.utils.NettyUtils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NettyServer
 */
public class NettyServer {

    private static final Logger logger =LoggerFactory.getLogger(NettyServer.class);

    /**
     * boss group
     */
    private final EventLoopGroup bossGroup;

    /**
     * worker group
     */
    private final EventLoopGroup workGroup;

    /**
     * server config
     */
    private final NettyServerConfig serverConfig;

    /**
     * server bootstrap
     */
    private final ServerBootstrap serverBootstrap = new ServerBootstrap();

    /**
     * started flag
     */
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    /**
     * server init
     *
     * @param serverConfig server config
     */
    public NettyServer(final NettyServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        if (NettyUtils.useEpoll()) {
            this.bossGroup = new EpollEventLoopGroup(1, new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerBossThread_%d", this.threadIndex.incrementAndGet()));
                }
            });

            this.workGroup = new EpollEventLoopGroup(serverConfig.getWorkerThread(), new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerWorkerThread_%d", this.threadIndex.incrementAndGet()));
                }
            });
        } else {
            this.bossGroup = new NioEventLoopGroup(1, new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerBossThread_%d", this.threadIndex.incrementAndGet()));
                }
            });

            this.workGroup = new NioEventLoopGroup(serverConfig.getWorkerThread(), new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerWorkerThread_%d", this.threadIndex.incrementAndGet()));
                }
            });
        }
        this.start();
    }


    /**
     * server start
     */
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            this.serverBootstrap
                .group(this.bossGroup, this.workGroup)
                .channel(NettyUtils.getServerSocketChannelClass())
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BACKLOG, serverConfig.getSoBacklog())
                .childOption(ChannelOption.SO_KEEPALIVE, serverConfig.isSoKeepalive())
                .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNoDelay())
                .childOption(ChannelOption.SO_SNDBUF, serverConfig.getSendBufferSize())
                .childOption(ChannelOption.SO_RCVBUF, serverConfig.getReceiveBufferSize())
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        initNettyChannel(ch);
                    }
                });

            ChannelFuture future;
            try {
                future = serverBootstrap.bind(serverConfig.getListenPort()).sync();
            } catch (Exception e) {
                //logger.error("NettyRemotingServer bind fail {}, exit", e.getMessage(), e);
                throw new RuntimeException(String.format("NettyRemotingServer bind %s fail", serverConfig.getListenPort()));
            }
            if (future.isSuccess()) {
               // logger.info("NettyRemotingServer bind success at port : {}", serverConfig.getListenPort());
            } else if (future.cause() != null) {
                throw new RuntimeException(String.format("NettyRemotingServer bind %s fail", serverConfig.getListenPort()), future.cause());
            } else {
                throw new RuntimeException(String.format("NettyRemotingServer bind %s fail", serverConfig.getListenPort()));
            }
        }
        System.out.println("netty ser ver start");
    }

    /**
     * init netty channel
     *
     * @param ch socket channel
     */
    private void initNettyChannel(SocketChannel ch) {
        ch.pipeline()
            .addLast(new NettyDecoder(RpcRequest.class))
            .addLast(new NettyEncoder(RpcResponse.class))
            .addLast("server-idle-handle", new IdleStateHandler(0, 0, Constants.NETTY_SERVER_HEART_BEAT_TIME, TimeUnit.MILLISECONDS))
            .addLast("handler", new NettyServerHandler());
    }


    public void close() {
        if (isStarted.compareAndSet(true, false)) {
            try {
                if (bossGroup != null) {
                    this.bossGroup.shutdownGracefully();
                }
                if (workGroup != null) {
                    this.workGroup.shutdownGracefully();
                }

            } catch (Exception ex) {
                logger.error("netty server close exception", ex);
            }
            logger.info("netty server closed");
        }
    }


}
