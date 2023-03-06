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
import net.openlcr.server.utils.SipUri;
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
public final class MetaswitchPlatformHandler extends GenericPlatformHandler {
  //  Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public MetaswitchPlatformHandler(Logger logger, SipMessage msg, Connection connection, CoreSipStack stack) {
        super(logger, msg, connection, stack);
    }
    
    @Override
    public void handleOptions(SipMessage msg, Connection connection, CoreSipStack stack) {
        SipResponse response = msg.createResponse(200);
        this.appendLog(("Got OPTIONS request from " + msg.getViaHeader().getHost() + " - responding 200.... all quiet on the western front....."));
        connection.send(response);
    }
    
    @Override
    public void handleInvite(SipMessage msg, Connection connection, CoreSipStack stack) {
        if (LCRServer.config.isSend100Trying()) {
            this.appendLog("Sending 100 Trying for inital invite");
            SipResponse tryingResponse = msg.toRequest().createResponse(100);
            connection.send(tryingResponse);
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final SipURI requestURI = (SipURI) msg.toRequest().getRequestUri();
        
        FromHeader fromHeader = msg.getFromHeader();
        // Check if calling number has special routing
        try {
            // Patch March 20, 2018 - Found issue with a deployment with CIC codes - the requestURI.getUser contains ; and additional values
            // Error finding route for phone number: +14169671111;cic=0001;dai=presub: Error type: NOT_A_NUMBER. The string supplied did not seem to be a phone number.
            // The real patch should be in the getUser of the requestURI, but I'm giong to fix it here so we can action on the CIC code if we need to
            String requestUser = requestURI.getUser().toString();
            if (requestURI.getUser().toString().contains(";")) {
                requestUser = requestURI.getUser().toString().split(";")[0];
                // If we wanted the CIC code we could get it here, but we don't need it (yet)
            }
            PhoneNumber calledNumber = phoneUtil.parse(requestUser, "CA");
            
            if (phoneUtil.isValidNumber(calledNumber)) {
                this.appendLog("Starting routing lookup for: " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());
                if (!LCRServer.getConfiguration().getCacheEnabled()) {
                    LCRServer.routeCache.invalidateAll();
                }
            }
            DIDRoute targetRoutes = LCRServer.routeCache.get(calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());
            this.appendLog("Found " + targetRoutes.getTargetRoutes().size() + " routes");
            
       //     this.appendLog("------------------");
       //     this.appendLog(gson.toJson(targetRoutes));
       //     this.appendLog("------------------");
            /* Now see if we have to preprend any routes based on the billing header */
            
            try {
              //  SipHeader pChargeInfo = msg.getHeader("P-Charge-Info");
                SipUri.SipContactInfo contactInfo = SipUri.parseSipContact(msg.getHeader("P-Charge-Info").getValue().toString().replace(";npi=ISDN", ""));
            //    String chargeURI =  pChargeInfo.getValue().toString();
                this.appendLog("Charge Number for call is: " + contactInfo.userPart);
                PhoneNumber sourceNumber = phoneUtil.parse(contactInfo.userPart, "CA");
                
                DIDRoute overrideRoutes = LCRServer.sourceDIDRouteCache.get(String.valueOf(sourceNumber.getNationalNumber()));
                if (!overrideRoutes.getTargetRoutes().isEmpty()) {
                     // We get the list in priority order, but the function below prepends them, so it acutally reverses what we did at the DB level.  To keep the idea of priority sane (higher is better) we need to get all Missy Eliot on this arraylist and drop down flip it and reverse it
                    Collections.reverse(overrideRoutes.getTargetRoutes());
                    for (Route route : overrideRoutes.getTargetRoutes()) {
                        this.appendLog("Override found - prepending route");
                        targetRoutes.prependTargetRoute(route);
                    }
                } else {
        //            this.appendLog("No overrides found for billing number - normal routing applies");
                }
                // Now we query for blacklist entries
               
                ArrayList<Integer> blacklistCarriers = LCRServer.sourceDIDCarrierBlacklist.get(String.valueOf(sourceNumber.getNationalNumber()));
                if (!blacklistCarriers.isEmpty()) {
                    for (Integer carrierID : blacklistCarriers) {
                        targetRoutes.removeTargetCarrier(carrierID);
                        this.appendLog("Blacklist found for carrierID " + carrierID + " - removing route");
                    }
                }
            } catch (Exception ex) {
          //      this.appendLog("No overrides found for charge number - continuing");
             //   this.appendLog("Could not get charging info for call: " + ex, ex);
            }
            
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
                    if (q > 0.6) {
                        //LCRServer.appendLog("Found route to carrier ID " + route.getCarrierID() + " for: " + calledNumber.toString());
                        Carrier carrier = LCRServer.carrierCache.get(route.getCarrierID() + "");
                        // Now for each carrier, loop over the trunks
                        for (TrunkGroup trunk : carrier.getTrunkGroups()) {
                            final SipURI contactURI = requestURI.clone();
                            contactURI.setParameter("dtg", trunk.getTrunkGroupID());
                            contactHeaderBuilder.address(contactURI).headerParam("q", String.format("%01.1f", q));
                          
                            q = q - 0.1;
                        }
                    } else {
                        this.appendLog("Q Value is less than 0.6 - skipping further routes");
                        break;
                    }
                }
                
                response.addHeader(contactHeaderBuilder.build());
                connection.send(response);
                this.appendLog("Finished routing lookup for: " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());
            }

            /* For now, we're going to return whatever error code is set in the config file when we can't parse the number */
        } catch (Exception ex) {
            this.appendLog("Error finding route for phone number: " + requestURI.getUser().toString() + ": " + ex, ex);
            
            SipResponse response = msg.toRequest().createResponse(503);
            connection.send(response);
        }
        
    }
    
    @Override
    public void handleCancel(SipMessage msg, Connection connection, CoreSipStack stack) {
        this.appendLog("Sending 200 OK for CANCEL request");
        SipResponse response = msg.toRequest().createResponse(200);
        connection.send(response);
    }
    
}
