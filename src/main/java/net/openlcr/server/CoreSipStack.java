/*
 * Copyright (C) 2023 Matthew M. Gamble <mgamble@mgamble.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.openlcr.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.pkts.buffer.Buffer;
import io.sipstack.netty.codec.sip.Connection;
import io.sipstack.netty.codec.sip.SipMessageDatagramDecoder;
import io.sipstack.netty.codec.sip.SipMessageEncoder;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import io.sipstack.netty.codec.sip.SipMessageStreamDecoder;
import io.sipstack.netty.codec.sip.UdpConnection;

import java.net.InetSocketAddress;

/**
 * Bootstrap netty and setup the sip message encoder/decoder for basic SIP support.
 * 
 * Note: you can't really call this a SIP stack since all it provides is basic framing and parsing
 * of SIP messages but it is the very first layer of a true SIP stack (see rfc3261 for the various
 * layers of SIP).
 * 
 * @author jonas@jonasborjesson.com
 */
public class CoreSipStack {

    private final String ip;

    private final int port;

    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final EventLoopGroup udpGroup = new NioEventLoopGroup();

    /**
     * The TCP based bootstrap.
     */
    private final ServerBootstrap serverBootstrap;

    /**
     * Our UDP based bootstrap.
     */
    private final Bootstrap bootstrap;

    private Channel udpListeningPoint = null;

    public CoreSipStack(final SimpleChannelInboundHandler<SipMessageEvent> handler, final String ip, final int port) {
        this.ip = ip;
        this.port = port;

        this.bootstrap = createUDPListeningPoint(handler);
        this.serverBootstrap = createTCPListeningPoint(handler);
    }

    public Connection connect(final String ip, final int port) {
        final InetSocketAddress remoteAddress = new InetSocketAddress(ip, port);
        return new UdpConnection(this.udpListeningPoint, remoteAddress);
    }

    public Connection connect(final Buffer ip, final int port) {
        return connect(ip.toString(), port);
    }

    public void run() throws Exception {
        try {
            final InetSocketAddress socketAddress = new InetSocketAddress(this.ip, this.port);
            this.udpListeningPoint = this.bootstrap.bind(socketAddress).sync().channel();
            this.serverBootstrap.bind(socketAddress).sync().channel().closeFuture().await();
        } finally {
            this.bossGroup.shutdownGracefully();
            this.workerGroup.shutdownGracefully();
            this.udpGroup.shutdownGracefully();
        }
    }

    private Bootstrap createUDPListeningPoint(final SimpleChannelInboundHandler<SipMessageEvent> handler) {
        final Bootstrap b = new Bootstrap();
        b.group(this.udpGroup)
        .channel(NioDatagramChannel.class)
        .handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(final DatagramChannel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new SipMessageDatagramDecoder());
                pipeline.addLast("encoder", new SipMessageEncoder());
                pipeline.addLast("handler", handler);
            }
        });
        return b;
    }

    private ServerBootstrap createTCPListeningPoint(final SimpleChannelInboundHandler<SipMessageEvent> handler) {
        final ServerBootstrap b = new ServerBootstrap();

        b.group(this.bossGroup, this.workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(final SocketChannel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new SipMessageStreamDecoder());
                pipeline.addLast("encoder", new SipMessageEncoder());
                pipeline.addLast("handler", handler);
            }
        })
        .option(ChannelOption.SO_BACKLOG, 128)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.TCP_NODELAY, true);
        return b;
    }

}