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

/**
 *
 * @author mgamble
 */
public class Version {

	/*
         1.2.0 - First open source release, moving to Java 17, cleaning up code
         1.0.0 - First 1.0 release
         0.19.0 - Added ability to blacklist carriers for a given TN
         0.18.0 - Added HTTP interface for resetting caches
         0.17.3 - Removed another unneeded logging statement for SQL query
         0.17.2 - Removed unneeded logging statement for SQL Query
 	 0.17.1 - fixed bug found by @pgobis where we were not respecting priority for trunk groups 
         0.17.0 - Switch from BoneCP to HikariCP to be in-line with other projects we support.
         0.16.10 - Bug fix for parsing issue with URIs that have CIC codes
         0.16.9 - Performance improvment on source_did_route logic (cache negitive replies)
         0.16.8 - fixed priority support ordering issue (it was backwards)
         0.16.7 - Updated static routes to support priority so we can set the order of the override
         0.16.4 - Moved sorting to java 8 - updated sorting to use lambdas
         0.16.0 - Updated LCR query to include a like clause - this reduces the LCR query to a much smaller table space (currently first two digits match)
                  and is a signifigant improvement. The next optimization could be to look at the first digit - if that is a one, then change the like to 3 
         0.15.0 - Lots of changes for Dialer support
         0.11-13 Major performance enhancments (threading, mostly)
         0.10.0 - Start process of adding on-net routing to code and speed improvements
         0.9.0 - Fixed Metaswitch to revert to re-using the contact URI and just addig DTG/OTG so trunk routing works 
         0.8.0 - Added support for append / prepend digits on non-E164 routes
         0.7.0 - Updated to support E164 / no e164 return for Broadworks (ticket)
         0.4-0.6 - Refactor into different platform handlers
         0.3.0 - first fixed for Broadsoft (options replies)
         0.2.0 - Updated to support Broadworks & m6
	 0.1.3 - Fix rounding, fixed routes query to ignore inactive carriers
	 0.1.2 - sort on price 
    
	 */
	public Version() {

	}

	private String buildNumber = "1.2.0";
	private String buildName = "long story short";
	private String author = "Matthew M. Gamble";

	/**
	 * @return the buildNumber
	 */
	public String getBuildNumber() {
		return buildNumber;
	}

	/**
	 * @return the buildName
	 */
	public String getBuildName() {
		return buildName;
	}

	/**
	 * @return the author
	 */
	public String getAuthor() {
		return author;
	}

}
