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

import io.pkts.packet.sip.SipMessage;
import io.sipstack.netty.codec.sip.Connection;



/**
 *
 * @author mgamble
 */
public interface PlatformHandler   {

    void handleOptions(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleInvite(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleAck(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleRegister(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleInfo(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleInitial(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleMessage(SipMessage msg, Connection connection, CoreSipStack stack);
    void handlRequest(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleBye(SipMessage msg, Connection connection, CoreSipStack stack);
    void handleCancel(SipMessage msg, Connection connection, CoreSipStack stack);
}