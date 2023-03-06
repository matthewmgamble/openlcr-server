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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipResponse;
import io.pkts.packet.sip.address.SipURI;
import io.pkts.packet.sip.header.ContactHeader;
import io.pkts.packet.sip.header.FromHeader;
import io.sipstack.netty.codec.sip.SipMessageEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.openlcr.common.classes.Carrier;
import net.openlcr.common.classes.Route;
import net.openlcr.common.classes.SupportedPlatform;
import net.openlcr.common.classes.TrunkGroup;
import org.apache.log4j.Logger;

/**
 *
 * @author mgamble
 */
@Sharable // (1)
public final class M6InviteHandler extends SimpleChannelInboundHandler<SipMessageEvent> { // (2)

   
    private CoreSipStack stack;

    
    private Logger logger;
    
    
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    public void appendLog(String logMessage) {
        System.out.println(logMessage);
        logger.info(logMessage);
    }
    
    public M6InviteHandler() {
        // TODO Auto-generated constructor stub
    }

    public void setStack(final CoreSipStack stack) {
        this.stack = stack;
    }
    
	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, // (3)
			final SipMessageEvent event)
			throws Exception {

		DecimalFormat oneDigit = new DecimalFormat("#,##0.0");//format to 1 decimal place

		this.appendLog("Got packet... starting processing.....");
		final SipMessage msg = event.getMessage(); // (4)

		if (msg.isOptions()) {
                    SipResponse response = msg.createResponse(200);
                    this.appendLog(("Got OPTIONS request from " + msg.getViaHeader().getHost()  + " - responding 200.... all quiet on the western front....."));
                    /* Broadsoft NS sends from a high UDP port, but expects response on 5060 - we can't just reuse the existing socket like we do for Meta*/
                    
                     if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.METASWITCH.toString())) {
                         event.getConnection().send(response);
                     } else if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.BROADWORKS.toString())) {
                       
                         final io.sipstack.netty.codec.sip.Connection connection = this.stack.connect(msg.getViaHeader().getHost().toString(), 5060);
                         connection.send(response);
                   //      event.getConnection().send(response);
                     } else {
                         event.getConnection().send(response);
                     }
			
			
		}
		if (msg.isAck())  // (5)
			return;

		if (msg.isRequest()) {   // (6)
			PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
			final SipURI requestURI = (SipURI) msg.toRequest().getRequestUri();

			FromHeader fromHeader = msg.getFromHeader();
                        PhoneNumber calledNumber = phoneUtil.parse(requestURI.getUser().toString(), "CA");
			try {
				if (phoneUtil.isValidNumber(calledNumber)) {
                                    
					this.appendLog("Starting routing lookup for: " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber());

					Connection connection;
					connection = LCRServer.ds.getConnection(); 	// fetch a connection

//                    PreparedStatement pstmt = connection.prepareStatement("select id, digits, price, carrier_id from routes where active > 0 and LEFT('" + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC");
					PreparedStatement pstmt = connection.prepareStatement("select routes.id, digits, price, carrier_id from routes, carrier where routes.active > 0 and carrier.active > 0 and carrier.id = routes.carrier_id and LEFT('" + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC");

					this.appendLog("LCR Query: " + pstmt.toString());
					ResultSet rs = pstmt.executeQuery();
					ArrayList<Route> targetRoutes = new ArrayList<>();

					while (rs.next()) {
						this.appendLog("Got MySQL result for lookup, processing");
						Route route = new Route();
						route.setActive(true); /* We know the route is active at this point, since we only select active ones from the DB */
                        			route.setCarrierID(rs.getInt("carrier_id"));
						route.setDigits(rs.getString("digits"));
						route.setPrice(rs.getDouble("price"));
						route.setRouteID(rs.getInt("id"));
						targetRoutes.add(route);
					}
					pstmt.close();
					connection.close();
					double q = 1;
					if (targetRoutes.isEmpty()) {
						this.appendLog("No routes found for for: " + calledNumber.toString() + "(Lookup was " + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + ")");
						/* We have no routes - bail! */
						/* We will return a 503 for now */
						SipResponse response = msg.toRequest().createResponse(503);
						event.getConnection().send(response);
					} else {
						SipResponse response = msg.toRequest().createResponse(Integer.parseInt(LCRServer.getConfiguration().getSipResponseCode()));

						Collections.sort(targetRoutes);
						// Sort the routes
						targetRoutes.sort(null);
						
						ContactHeader.Builder contactHeaderBuilder = ContactHeader.with();
						Set<Integer> seenCarriers = new HashSet();
						for (Route route : targetRoutes){
							if(seenCarriers.contains(route.getCarrierID()))
								continue;
							else
								seenCarriers.add(route.getCarrierID());
							if (q > 0.1) {
								this.appendLog("Found route to carrier ID " + route.getCarrierID() + " for: " + calledNumber.toString());
								Carrier carrier = LCRServer.carrierCache.get(route.getCarrierID() + "");
								// Now for each carrier, loop over the trunks
								for (TrunkGroup trunk : carrier.getTrunkGroups()) {
                                                                    
                                                                        if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.METASWITCH.toString())) {
                                                                            // For Metaswitch we change the DTG
                                                                             } else if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.BROADWORKS.toString())) {
                                                                            // For Broadworks we return a 302 with the IP changed
                                                                            final SipURI contactURI = SipURI.with().user(requestURI.getUser()).host(trunk.getIpAddress()).port(trunk.getPort()).build();
                                                                            contactHeaderBuilder.address(contactURI).headerParam("q", String.format("%01.1f", q));
                                                                        }  else if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.M6.toString())) {
                                                                            // Only return one result - the M6 doesn't honor multiple contacts in a 302
                                                                           
                                                                        }  else {
                                                                            this.appendLog("Invalid platform configuration '" + LCRServer.getConfiguration().getSipPlatform() + "' - Returning 503");
                                                                            throw new Exception("Invalid Platform Configuration");
                                                                        }       
                                                                        
									q = q - 0.1;
								}
							} else {
								this.appendLog("Q Value is less than 0.1 - skipping further routes");
								break;
							}
						}
                                                
                                                
						response.addHeader(contactHeaderBuilder.build());
                                                
                                                if (LCRServer.getConfiguration().getSipPlatform().contentEquals(SupportedPlatform.BROADWORKS.toString())) {
                                                    final io.sipstack.netty.codec.sip.Connection bsConnection = this.stack.connect(msg.getViaHeader().getHost().toString(), 5060);
                                                    bsConnection.send(response);
                                                } else {
                                                    event.getConnection().send(response); 
                         
                                                }
					}

				} else {
					/* For now, we're going to return whatever error code is set in the config file when we can't parse the number */
					
					this.appendLog("Invalid phone number: " + requestURI.getUser().toString() + " for input - returning " + LCRServer.getConfiguration().getSipCodeError());
					SipResponse response = msg.toRequest().createResponse(Integer.parseInt(LCRServer.getConfiguration().getSipCodeError()));
					event.getConnection().send(response);
				}
			} catch (Exception ex) {
				this.appendLog("Error finding route for phone number: " + requestURI.getUser().toString() + ": " + ex);
				SipResponse response = msg.toRequest().createResponse(503);
				event.getConnection().send(response);
			}

		}
	}


    
}
