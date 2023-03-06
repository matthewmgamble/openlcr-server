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

import net.openlcr.common.classes.SystemConfiguration;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.sipstack.netty.codec.sip.SipMessageDatagramDecoder;
import io.sipstack.netty.codec.sip.SipMessageEncoder;
import java.io.File;
import java.io.IOException;

import java.net.InetSocketAddress;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Paths.get;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import net.openlcr.common.classes.Carrier;
import net.openlcr.common.classes.DIDRoute;
import net.openlcr.common.classes.Route;
import net.openlcr.common.classes.RouteModification;
import net.openlcr.common.classes.TrunkGroup;


/**
 *
 * @author mgamble
 */
public final class LCRServer {

    static Version version = new Version();
    static Logger logger = Logger.getLogger("net.openlcr.server");
    public static HikariDataSource ds = new HikariDataSource();
    static SystemConfiguration config = new SystemConfiguration();
      
    public static SystemConfiguration getConfiguration() {
        return config;
    }

    private static void appendLog(String logMessage) {
  //      System.out.println(logMessage);
        logger.info(logMessage);
    }

    public static void appendDebugLog(String logMessage, Throwable t) {
   //     System.out.println(logMessage);
        logger.debug(logMessage, t);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static void main(final String[] args) throws Exception {
        /* Init */

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

        System.out.println("");
        System.out.println("openLCR Core Server Version " + version.getBuildNumber() + " (" + version.getBuildName() + ") - Code By " + version.getAuthor());
        System.out.println("");
        OptionParser parser = new OptionParser("c:");
        parser.accepts("config").withRequiredArg();
        
        try {
            OptionSet options = parser.parse(args);
            if (!options.has("config")) {
                System.out.println("No config file provided - cannot startup without config file");
                System.exit(255);
            }
            String configFileName = options.valueOf("config").toString();
            File file = new File(configFileName);
            if ((!file.isFile()) || (!file.canRead())) {
                System.out.println("Error - cannot read configuration \"" + file + "\" - aborting.");
                System.exit(1);
            }
            try {
                String jsonInput = new String(readAllBytes(get(configFileName)));
                config = gson.fromJson(jsonInput, SystemConfiguration.class);

            } catch (IOException | JsonSyntaxException ex) {
                System.out.println("Error - cannot parse configuration \"" + file + "\" - error is \"" + ex + "\" - aborting.");
                System.exit(1);
            }

        } catch (Exception ex) {
            System.out.println("Error processing command line arguments: " + ex);
            System.exit(1);
        }
        /* Light up logging */
        try {
            logger.addAppender(new DailyRollingFileAppender(new PatternLayout("%d{ISO8601} [%-5p] %m%n (%t)"), config.getLogDir() + "/" + config.getLogFileName(), "'.'yyyy-MM-dd"));
        } catch (Exception ex) {
            System.out.println("FATAL - COULD NOT SETUP LOG FILE APPENDER - CHECK CONFIGURATION.");
            logger.fatal("Could not setup appender?");
        }
        logger.setAdditivity(false);
        logger.setLevel(Level.DEBUG);

        logger.info("openLCR Core Server Version " + version.getBuildNumber() + " (" + version.getBuildName() + ") - Code By " + version.getAuthor());

        /* Light up database */
        Class.forName("org.mariadb.jdbc.Driver"); 	// load the DB driver
        HikariConfig hikariConfig = new HikariConfig();	// create a new configuration object
        hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.getMySQLServer() + "/" + config.getMySQLDatabase()+ "?useSSL=false");	// set the JDBC url
        hikariConfig.setUsername(config.getMySQLUser());			// set the username
        hikariConfig.setPassword(config.getMySQLPass());				// set the password
        hikariConfig.setLeakDetectionThreshold(60000);
        hikariConfig.setMaximumPoolSize(50);
        hikariConfig.setReadOnly(false);
        hikariConfig.setAutoCommit(true);
        
           ds = new HikariDataSource(hikariConfig);
        //  ds.setUsername(config.getDbUser());
        //  ds.setPassword(config.getDbPass());
        Connection connection = ds.getConnection();
        DatabaseMetaData dbmd = connection.getMetaData();
        logger.info("Connection to " + dbmd.getDatabaseProductName() + " " + dbmd.getDatabaseProductVersion() + " successful.\n");
        connection.close();
        logger.info("MySQL Connection Online");
	// setup the connection pool
        logger.info("Listening on " + config.getSipAddress() + " and port " + config.getSipPort());
        logger.info("Configured for platform: " + config.getSipPlatform());
        InboundHandler handler = new InboundHandler();
        handler.setLogger(logger);
        /*
        if (config.getSipPlatform().contentEquals(SupportedPlatform.BROADWORKS.toString())) {
            handler.setPlatformHandler(new BroadsoftPlatformHandler());
        } else if (config.getSipPlatform().contentEquals(SupportedPlatform.METASWITCH.toString())) {
            handler.setPlatformHandler(new MetaswitchPlatformHandler());
        } else if (config.getSipPlatform().contentEquals(SupportedPlatform.M6.toString())) {
            handler.setPlatformHandler(new M6PlatformHandler());
        } else {
            handler.setPlatformHandler(new GenericPlatformHandler());
        }
        /* Start our SIP server for metrics and mgmt */

        final EventLoopGroup udpGroup = new NioEventLoopGroup();

     //   EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
     //   EventLoopGroup workerGroup = new NioEventLoopGroup();

        final Bootstrap b = new Bootstrap(); // (3)
        //b.option(EpollChannelOption.SO_REUSEPORT, true);

        b.group(udpGroup)
                .channel(NioDatagramChannel.class).handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(final DatagramChannel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new SipMessageDatagramDecoder()); // (4)
                pipeline.addLast("encoder", new SipMessageEncoder()); // (5)
                pipeline.addLast("handler", handler); // (6)
            }
        });

       

        /* Todo - put WS connection to "API" server here */
        
        final InetSocketAddress socketAddress = new InetSocketAddress(config.getSipAddress(), Integer.parseInt(config.getSipPort())); // (7)
        final ChannelFuture f = b.bind(socketAddress).sync(); // (8)
        f.channel().closeFuture().await();
    }

    /* ToDo - put timeouts into config file */
    public static LoadingCache<String, Carrier> carrierCache = CacheBuilder.newBuilder().maximumSize(20000).expireAfterWrite(60, TimeUnit.MINUTES).build(
            new CacheLoader<String, Carrier>() {
        @Override
        public Carrier load(String key) throws Exception {
            Connection connection;
            connection = ds.getConnection(); 	// fetch a connection
            Carrier carrier = new Carrier();
            PreparedStatement pstmt = connection.prepareStatement("select id, carrier_name, active, enable_e164 from carrier where id = ? ");
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                throw new Exception("Carrier ID " + key + "not found");
            } else {

                if (rs.getInt("active") > 0) {
                    carrier.setActive(true);
                } else {
                    carrier.setActive(false);
                }
                if (rs.getInt("enable_e164") > 0) {
                    carrier.setUseE164(true);
                } else {
                    carrier.setUseE164(false);
                }
                carrier.setCarrierName(rs.getString("carrier_name"));
                carrier.setCarrierID(Integer.parseInt(key));
                rs.close();
                pstmt.close();
                connection.close();
                //    return carrier;
            }

            /* Now load the trunk groups */
            connection = ds.getConnection(); 	// fetch a connection

            pstmt = connection.prepareStatement("select trunkgroup.id as trunk_table_id, tg_id, ipAddress, port, priority, carrier.id, carrier_name from carrier, trunkgroup where carrier.id = ? and carrier.id = trunkgroup.carrier_id and trunkgroup.active = 1 order by priority desc");
            pstmt.setString(1, key);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                appendLog("Found trunkgroup record ID" + rs.getString("trunk_table_id") + " for carrier " + key);
                TrunkGroup trunkGroup = new TrunkGroup();
                trunkGroup.setCarrerID(Integer.parseInt(key));
                trunkGroup.setIpAddress(rs.getString("ipAddress"));
                trunkGroup.setTrunkGroupID(rs.getString("tg_id"));
                trunkGroup.setPort(rs.getInt("port"));
                trunkGroup.setPriority(rs.getInt("priority"));
                carrier.addTrunkGroup(trunkGroup);
            }
            rs.close();
            pstmt.close();
            connection.close();
            return carrier;

        }
    });

    public static LoadingCache<String, DIDRoute> routeCache = CacheBuilder.newBuilder().maximumSize(20000).expireAfterWrite(15, TimeUnit.MINUTES).build(
            new CacheLoader<String, DIDRoute>() {

        public DIDRoute load(String targetNumber) throws Exception {

            DIDRoute routes = new DIDRoute();
            Connection connection;
            connection = ds.getConnection(); 	// fetch a connection
            try (//                    PreparedStatement pstmt = connection.prepareStatement("select id, digits, price, carrier_id from routes where active > 0 and LEFT('" + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC");
                    //       PreparedStatement pstmt = connection.prepareStatement("select routes.id, digits, price, carrier_id, route_modification, modification_string from routes, carrier where routes.active > 0 and carrier.active > 0 and carrier.id = routes.carrier_id and LEFT('" + targetNumber + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC")) {
                    PreparedStatement pstmt = connection.prepareStatement("select routes.id, digits, price, carrier_id, route_modification, modification_string from routes, carrier where routes.active > 0 and carrier.active > 0 and carrier.id = routes.carrier_id and LEFT('" + targetNumber + "' ,LENGTH(digits)) = digits AND digits LIKE CONCAT(LEFT('" + targetNumber + "',2),'%') ")) {

           //     LCRServer.appendLog("LCR Query: " + pstmt.toString());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Route route = new Route();
                    route.setActive(true);
                    /* We know the route is active at this point, since we only select active ones from the DB */
                    route.setCarrierID(rs.getInt("carrier_id"));
                    route.setDigits(rs.getString("digits"));
                    route.setPrice(rs.getDouble("price"));
                    route.setRouteID(rs.getInt("id"));
                    /* Patch Jan 30th to add digit modifications */
                    if (rs.getString("route_modification") != null) {
                        route.setRouteModification(RouteModification.valueOf(rs.getString("route_modification").toUpperCase()));
                    }
                    if (rs.getString("modification_string") != null) {
                        route.setModificationString(rs.getString("modification_string"));
                    }
                    routes.addTargetRoute(route);

                }
                rs.close();
                pstmt.close();
                connection.close();
            }
            routes.orderTargetRoutes();

            return routes;
        }
    });

    public static LoadingCache<String, DIDRoute> sourceDIDRouteCache = CacheBuilder.newBuilder().maximumSize(20000).expireAfterWrite(15, TimeUnit.MINUTES).build(
            new CacheLoader<String, DIDRoute>() {

        @Override
        public DIDRoute load(String billingNumber) throws Exception {

            DIDRoute routes = new DIDRoute();
            Connection connection;
            connection = ds.getConnection(); 	// fetch a connection
            try {//                    PreparedStatement pstmt = connection.prepareStatement("select id, digits, price, carrier_id from routes where active > 0 and LEFT('" + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC");
                //       PreparedStatement pstmt = connection.prepareStatement("select routes.id, digits, price, carrier_id, route_modification, modification_string from routes, carrier where routes.active > 0 and carrier.active > 0 and carrier.id = routes.carrier_id and LEFT('" + targetNumber + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC")) {
                PreparedStatement pstmt = connection.prepareStatement("select source_did_routes.id, billing_number, carrier_id, route_modification, modification_string from source_did_routes, carrier where source_did_routes.active > 0 and carrier.active > 0 and carrier.id = source_did_routes.carrier_id and billing_number = ? order by priority desc");
                pstmt.setString(1, billingNumber);

                //LCRServer.appendLog("Source DID LCR Query: " + pstmt.toString());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Route route = new Route();
                    route.setActive(true);
                    /* We know the route is active at this point, since we only select active ones from the DB */
                    route.setCarrierID(rs.getInt("carrier_id"));
                    route.setDigits(billingNumber);
                    route.setPrice(0.00);
                    route.setRouteID(rs.getInt("id"));
                    /* Patch Jan 30th to add digit modifications */
                    if (rs.getString("route_modification") != null) {
                        route.setRouteModification(RouteModification.valueOf(rs.getString("route_modification").toUpperCase()));
                    }
                    if (rs.getString("modification_string") != null) {
                        route.setModificationString(rs.getString("modification_string"));
                    }
                    routes.addTargetRoute(route);

                }
                rs.close();
                pstmt.close();
                connection.close();
            } catch (SQLException ex) {
                  logger.debug("Could not query LCR server for source did override: " + ex, ex);
                connection.close(); 
            }
            return routes;
        }
    });
    
    public static LoadingCache<String, ArrayList<Integer>> sourceDIDCarrierBlacklist = CacheBuilder.newBuilder().maximumSize(20000).expireAfterWrite(60, TimeUnit.MINUTES).build(
            new CacheLoader<String, ArrayList<Integer>>() {

        @Override
        public ArrayList<Integer> load(String billingNumber) throws Exception {

            ArrayList<Integer> blacklistCarriers = new ArrayList<>();
            Connection connection;
            connection = ds.getConnection(); 	// fetch a connection
            try {//                    PreparedStatement pstmt = connection.prepareStatement("select id, digits, price, carrier_id from routes where active > 0 and LEFT('" + calledNumber.getCountryCode() + "" + calledNumber.getNationalNumber() + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC");
                //       PreparedStatement pstmt = connection.prepareStatement("select routes.id, digits, price, carrier_id, route_modification, modification_string from routes, carrier where routes.active > 0 and carrier.active > 0 and carrier.id = routes.carrier_id and LEFT('" + targetNumber + "' ,LENGTH(digits)) = digits ORDER BY LENGTH(digits) DESC")) {
                PreparedStatement pstmt = connection.prepareStatement("select carrier_id from blacklist_routes where billing_number = ?");
                pstmt.setString(1, billingNumber);

                //LCRServer.appendLog("Source DID LCR Query: " + pstmt.toString());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    blacklistCarriers.add(rs.getInt("carrier_id"));
                    
                }
                rs.close();
                pstmt.close();
                connection.close();
            } catch (SQLException ex) {
                  logger.debug("Could not query LCR server for blacklist did carriers: " + ex, ex);
                connection.close(); 
            }
            return blacklistCarriers;
        }
    });

}
