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
package net.openlcr.server.utils;


import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SipUri {

    private SipUri() {
        // Singleton
    }

    private final static String SIP_SCHEME_RULE = "sip(?:s)?|tel";
    private final static Pattern SIP_CONTACT_ADDRESS_PATTERN = Pattern
            .compile("^([^@:]+)@([^@]+)$");
    private final static Pattern SIP_CONTACT_PATTERN = Pattern
            .compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?("+SIP_SCHEME_RULE+"):([^@]+)@([^>]+)(?:>)?$");
    private final static Pattern SIP_HOST_PATTERN = Pattern
            .compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?("+SIP_SCHEME_RULE+"):([^@>]+)(?:>)?$");

    
    public static class SipContactInfo {
        /**
         * Contact display name.
         */
        public String displayName = "";
        /**
         * User name of AoR
         */
        public String userPart = "";
        /**
         * Domaine name
         */
        public String domain = "";
        /**
         * Scheme of the protocol
         */
        public String scheme = "";

    }
    public static SipContactInfo parseSipContact(String sipUri) {
        SipContactInfo parsedInfos = new SipContactInfo();

        if (!sipUri.isEmpty()) {
            Matcher m = SIP_CONTACT_PATTERN.matcher(sipUri);
            if (m.matches()) {
                parsedInfos.displayName = URLDecoder.decode(m.group(1).trim());
                parsedInfos.domain = m.group(4);
                parsedInfos.userPart = URLDecoder.decode(m.group(3));
                parsedInfos.scheme = m.group(2);
            }else {
                // Try to consider that as host
                m = SIP_HOST_PATTERN.matcher(sipUri);
                if(m.matches()) {
                    parsedInfos.displayName = URLDecoder.decode(m.group(1).trim());
                    parsedInfos.domain = m.group(3);
                    parsedInfos.scheme = m.group(2);
                }else {
                    m = SIP_CONTACT_ADDRESS_PATTERN.matcher(sipUri);
                    if(m.matches()) {
                        parsedInfos.userPart = URLDecoder.decode(m.group(1));
                        parsedInfos.domain = m.group(2);
                    }else {
                        // Final fallback, we have only a username given
                        parsedInfos.userPart = sipUri;
                    }
                }
            }
        }

        return parsedInfos;
    }
    

}
