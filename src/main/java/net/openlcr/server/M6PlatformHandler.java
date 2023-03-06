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

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import io.netty.channel.ChannelHandler.Sharable;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipResponse;
import io.pkts.packet.sip.address.SipURI;
import io.pkts.packet.sip.header.ContactHeader;
import io.pkts.packet.sip.header.FromHeader;
import io.sipstack.netty.codec.sip.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.openlcr.common.classes.Carrier;
import net.openlcr.common.classes.DIDRoute;
import net.openlcr.common.classes.Route;
import net.openlcr.common.classes.TrunkGroup;
import org.apache.log4j.Logger;

/**
 *
 * @author mgamble
 */
@Sharable // (1)
public final class M6PlatformHandler extends GenericPlatformHandler {

    private Logger logger;

    public M6PlatformHandler(Logger logger, SipMessage msg, Connection connection, CoreSipStack stack) {
        super(logger, msg, connection, stack);
    }

  

  

    @Override
    public void appendLog(String logMessage) {
        System.out.println(logMessage);
        logger.info(logMessage);
    }
    
    public void handleOptions(SipMessage msg, Connection connection, CoreSipStack stack) {
        // TODO: Fix static port setting of 5060
        SipResponse response = msg.createResponse(200);
        this.appendLog(("Got OPTIONS request from " + msg.getViaHeader().getHost() + " - responding 200.... all quiet on the western front....."));
        final io.sipstack.netty.codec.sip.Connection remoteConnection = stack.connect(msg.getViaHeader().getHost().toString(), 5060);
        remoteConnection.send(response);
    }

    @Override
    public void handleInvite(SipMessage msg, Connection connection, CoreSipStack stack) {

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final SipURI requestURI = (SipURI) msg.toRequest().getRequestUri();

        FromHeader fromHeader = msg.getFromHeader();
        try {
            PhoneNumber calledNumber = phoneUtil.parse(requestURI.getUser().toString(), "CA");

            if (phoneUtil.isValidNumber(calledNumber)) {
                this.appendLog("Starting routing lookup for: " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());

            }
            DIDRoute targetRoutes = LCRServer.routeCache.get(calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());

            double q = 1;
            if (targetRoutes.getTargetRoutes().isEmpty()) {
                this.appendLog("No routes found for for: " + calledNumber.toString() + "(Lookup was " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + ")");
                /* We have no routes - bail! */
                /* We will return a 503 for now */
                SipResponse response = msg.toRequest().createResponse(503);
                connection.send(response);
            } else {
                SipResponse response = msg.toRequest().createResponse(Integer.parseInt(LCRServer.getConfiguration().getSipResponseCode()));
            
                ContactHeader.Builder contactHeaderBuilder = ContactHeader.with();
                Set<Integer> seenCarriers = new HashSet();
                for (Route route : targetRoutes.getTargetRoutes()) {
                    if (seenCarriers.contains(route.getCarrierID())) {
                        continue;
                    } else {
                        seenCarriers.add(route.getCarrierID());
                    }
                    if (q > 0.1) {
                        //LCRServer.appendLog("Found route to carrier ID " + route.getCarrierID() + " for: " + calledNumber.toString());
                        Carrier carrier = LCRServer.carrierCache.get(route.getCarrierID() + "");
                        // Now for each carrier, loop over the trunks
                        for (TrunkGroup trunk : carrier.getTrunkGroups()) {
                            if (q == 1) {
                                final SipURI contactURI = SipURI.with().user(requestURI.getUser()).host(trunk.getIpAddress()).port(trunk.getPort()).build();
                                contactHeaderBuilder.address(contactURI).headerParam("q", String.format("%01.1f", q));
                            }
                        }
                    } else {
                        this.appendLog("Q Value is less than 0.1 - skipping further routes");
                        break;
                    }
                }

                response.addHeader(contactHeaderBuilder.build());

                final io.sipstack.netty.codec.sip.Connection bsConnection = stack.connect(msg.getViaHeader().getHost().toString(), 5060);
                bsConnection.send(response);

            }

            /* For now, we're going to return whatever error code is set in the config file when we can't parse the number */
        } catch (Exception ex) {
            this.appendLog("Error finding route for phone number: " + requestURI.getUser().toString() + ": " + ex);
            SipResponse response = msg.toRequest().createResponse(503);
            connection.send(response);
        }

    }

}
