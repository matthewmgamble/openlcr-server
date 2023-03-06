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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.pkts.packet.sip.SipMessage;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.log4j.Logger;

/**
 *
 * @author mgamble
 */
@Sharable // (1)
public final class InboundHandler extends SimpleChannelInboundHandler<SipMessageEvent> { // (2)

    private Logger logger;
  //  private PlatformHandler platformHandler;
    ExecutorService executor;
    private CoreSipStack stack;

    public InboundHandler() {
        //executor = Executors.newCachedThreadPool();
         executor = Executors.newFixedThreadPool(100);
        // TODO Auto-generated constructor stub
    }

    public void setStack(final CoreSipStack stack) {
        this.stack = stack;
    }

     public void appendLog(String logMessage) {
  //      System.out.println(logMessage);
        logger.info(logMessage);
    }
     
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    public void setPlatformHandler(final PlatformHandler platformHandler) {
     //   this.platformHandler = platformHandler;
 //       this.platformHandler.setLogger(logger);
    }
    
    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, // (3)
            final SipMessageEvent event)
            throws Exception {
        final SipMessage msg = event.getMessage(); // (4)
       // MetaswitchPlatformHandler test = new MetaswitchPlatformHandler(ctx, event.getConnection(), this.stack);
        
        executor.execute(new MetaswitchPlatformHandler(logger, msg, event.getConnection(), this.stack));

    }
}
