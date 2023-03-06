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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import io.netty.channel.ChannelHandler.Sharable;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipResponse;
import io.pkts.packet.sip.address.SipURI;
import io.sipstack.netty.codec.sip.Connection;
import net.openlcr.common.classes.Carrier;
import net.openlcr.common.classes.Route;
import net.openlcr.common.classes.RouteModification;
import net.openlcr.common.classes.SupportedPlatform;
import net.openlcr.common.classes.TrunkGroup;
import org.apache.log4j.Logger;

/**
 *
 * @author mgamble
 */
@Sharable
public abstract class GenericPlatformHandler implements PlatformHandler, Runnable {
    
    private final Logger logger;
    SipMessage msg;
    Connection connection;
    CoreSipStack stack;

    public GenericPlatformHandler(Logger logger, SipMessage msg, Connection connection, CoreSipStack stack) {
        this.msg = msg;
        this.connection = connection;
        this.stack = stack;
        this.logger = logger;
    }
   
    
    public void appendLog(String logMessage) {
        if (this.logger != null) {
        System.out.println(logMessage);
        logger.info(logMessage);
        } else {
            System.out.println("DANGER DANGER DANGER - LOGGER IS NULL");
        }
        
    }
    
    
    public void appendLog(String logMessage, Exception ex) {
        System.out.println(logMessage);
        logger.info(logMessage, ex);
    }
    
    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    
    
    @Override
    public void run() {
        
        if (msg.isOptions()) {
            this.handleOptions(this.msg, this.connection, this.stack);
        } else if (msg.isAck()) {
            return;
        } else if (msg.isInvite()) {
            this.handleInvite(this.msg, this.connection, this.stack);
        } else if (msg.isBye()) {
            this.handleBye(this.msg, this.connection, this.stack);
        } else if (msg.isCancel()) {
            this.handleCancel(msg, connection, stack);
        } else {
            this.appendLog("Got SIP method with no handler configured: " + msg.getMethod().toString());
        }
        
    }
    
    @Override
    public void handleOptions(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleInvite(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleAck(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleRegister(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleInfo(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleInitial(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleMessage(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handlRequest(SipMessage msg, Connection connection, CoreSipStack stack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void handleBye(SipMessage msg, Connection connection, CoreSipStack stack) {
      this.appendLog("Sending 200 OK for BYE request");
        SipResponse response = msg.toRequest().createResponse(200);
        connection.send(response);
    }
    
    @Override
    public void handleCancel(SipMessage msg, Connection connection, CoreSipStack stack) {
        this.appendLog("Sending 200 OK for CANCEL request");
        SipResponse response = msg.toRequest().createResponse(200);
        connection.send(response);
    }
    
    public SipURI buildContactURI(SipURI requestURI, Carrier carrier, TrunkGroup trunk, SupportedPlatform platform) throws NumberParseException {
        SipURI contactURI = null;
        if (carrier.isUseE164()) {
            contactURI = SipURI.with().user(requestURI.getUser()).host(trunk.getIpAddress()).port(trunk.getPort()).build();
        } else {
            // Use libphonenumber to convert to dialable format
            PhoneNumber calledNumber = phoneUtil.parse(requestURI.getUser().toString(), LCRServer.getConfiguration().getLocale());
            String formattedNumber = phoneUtil.formatOutOfCountryCallingNumber(calledNumber, LCRServer.getConfiguration().getLocale());
            formattedNumber = formattedNumber.replace(" ", "");
            formattedNumber = formattedNumber.replace("-", "");
            /* Now do any prepend/append needed */
            
            contactURI = SipURI.with().user(formattedNumber).host(trunk.getIpAddress()).port(trunk.getPort()).build();
        }
        
        return contactURI;
    }
    
    public SipURI buildAndRewriteContactURI(Route route, SipURI requestURI, Carrier carrier, TrunkGroup trunk) throws NumberParseException {
        SipURI contactURI = null;
        String originalUser = requestURI.getUser().toString();
        if (carrier.isUseE164()) {
            if (route.getRouteModification() != RouteModification.NONE) {
                this.appendLog("WARNING: Unsupported configuration - carrier set to use E164 but route modification requested. Ignoring route modification");
            }
            
            contactURI = SipURI.with().user(originalUser).host(trunk.getIpAddress()).port(trunk.getPort()).build();
        } else {
            // Use libphonenumber to convert to dialable format
            PhoneNumber calledNumber = phoneUtil.parse(originalUser, LCRServer.getConfiguration().getLocale());
            String formattedNumber = phoneUtil.formatOutOfCountryCallingNumber(calledNumber, LCRServer.getConfiguration().getLocale());
            formattedNumber = formattedNumber.replace(" ", "");
            formattedNumber = formattedNumber.replace("-", "");
            formattedNumber = formattedNumber.replace(")", "");
            formattedNumber = formattedNumber.replace("(", "");
            /* Now do any prepend/append needed */
            if (route.getRouteModification() != RouteModification.NONE) {
                this.appendLog("Route modification requested: " + route.getRouteModification().toString() + " - " + route.getModificationString());
                switch (route.getRouteModification()) {
                    case PREPEND:
                        formattedNumber = route.getModificationString().concat(formattedNumber);
                        break;
                    case APPEND:
                        formattedNumber = formattedNumber.concat(route.getModificationString());
                        break;
                    default:
                        this.appendLog("DB requested a route modification type we don't support: " + route.getRouteModification());
                        break;
                }
                
                this.appendLog("Finished modification - new target # is: " + formattedNumber);
            } else {
                this.appendLog("No route modification requested: " + route.getRouteModification());
            }
            contactURI = SipURI.with().user(formattedNumber).host(trunk.getIpAddress()).port(trunk.getPort()).build();
        }
        
        return contactURI;
    }

    

    
    
}
