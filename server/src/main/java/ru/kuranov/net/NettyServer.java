package ru.kuranov.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;
import ru.kuranov.handler.ServerMessageHandler;

@Slf4j
    public class NettyServer {
        public NettyServer() {
            EventLoopGroup auth = new NioEventLoopGroup(1);
            EventLoopGroup worker = new NioEventLoopGroup();
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                ChannelFuture future = bootstrap.group(auth, worker)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) {
                                socketChannel.pipeline().addLast(
                                        new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                        new ObjectEncoder(),
                                        new ServerMessageHandler());
                            }
                        }).bind(8189).sync();
                log.debug("Server started ...");
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("e=", e);
            } finally {
                auth.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }
    }
