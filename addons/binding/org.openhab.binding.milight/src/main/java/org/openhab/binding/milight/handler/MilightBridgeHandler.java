/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.milight.handler;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.milight.MilightBindingConstants;
import org.openhab.binding.milight.internal.discovery.ThingDiscoveryService;
import org.openhab.binding.milight.internal.protocol.MilightDiscover;
import org.openhab.binding.milight.internal.protocol.MilightDiscover.DiscoverResult;
import org.openhab.binding.milight.internal.protocol.MilightV6SessionManager;
import org.openhab.binding.milight.internal.protocol.QueuedSend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MilightBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author David Graeff - Initial contribution
 */
public class MilightBridgeHandler extends BaseBridgeHandler implements DiscoverResult {
    private Logger logger = LoggerFactory.getLogger(MilightBridgeHandler.class);
    private MilightDiscover discover;
    private QueuedSend com;
    private MilightV6SessionManager session;
    private ScheduledFuture<?> discoverTimer;
    private int refrehIntervalSec;
    private String bridgeid;
    private int bridgeversion;
    private ThingDiscoveryService thingDiscoveryService;
    private long last_keep_alive = 0;

    public MilightBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // There is nothing to handle in the bridge handler
    }

    @Override
    public void thingUpdated(Thing thing) {
        this.thing = thing;
        if (com == null) {
            return;
        }

        boolean reconnect = false;

        // Create a new communication object if the user changed the IP configuration.
        Object host_config_obj = thing.getConfiguration().get(MilightBindingConstants.CONFIG_HOST_NAME);
        String host_config = ((host_config_obj instanceof String) ? (String) host_config_obj
                : (host_config_obj instanceof InetAddress) ? ((InetAddress) host_config_obj).getHostAddress() : null);
        if (host_config != null && !host_config.equals(com.getAddr().getHostAddress())) {
            reconnect = true;
        }

        // Create a new communication object if the user changed the bridge ID configuration.
        String id_config = (String) thing.getConfiguration().get(MilightBindingConstants.CONFIG_ID);
        if (id_config != null && !id_config.equals(bridgeid)) {
            reconnect = true;
        }

        // Create a new communication object if the user changed the port configuration.
        BigDecimal port_config = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_CUSTOM_PORT);
        if (port_config != null && port_config.intValue() > 0 && port_config.intValue() <= 65000
                && port_config.intValue() != com.getPort()) {
            reconnect = true;
        }

        BigDecimal protocol_version = (BigDecimal) thing.getConfiguration()
                .get(MilightBindingConstants.CONFIG_PROTOCOL_VERSION);
        if (protocol_version != null && protocol_version.intValue() != bridgeversion) {
            bridgeversion = protocol_version.intValue();
            reconnect = true;
        }

        BigDecimal refresh_time = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_REFRESH_SEC);
        if (refresh_time != null && refresh_time.intValue() != refrehIntervalSec) {
            setupRefreshTimer();
        }

        if (reconnect) {
            createCommunicationObject();
        }

        BigDecimal pw_byte1 = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_PASSWORD_BYTE_1);
        BigDecimal pw_byte2 = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_PASSWORD_BYTE_2);
        if (pw_byte1 != null && pw_byte2 != null && pw_byte1.intValue() >= 0 && pw_byte1.intValue() <= 255
                && pw_byte2.intValue() >= 0 && pw_byte2.intValue() <= 255 && session != null) {
            session.setPasswordBytes((byte) pw_byte1.intValue(), (byte) pw_byte2.intValue());
        }

        BigDecimal repeat_command = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_REPEAT);
        if (repeat_command != null && repeat_command.intValue() > 1 && repeat_command.intValue() <= 5) {
            com.setRepeatCommands(repeat_command.intValue());
        }

        BigDecimal wait_between_commands = (BigDecimal) thing.getConfiguration()
                .get(MilightBindingConstants.CONFIG_WAIT_BETWEEN_COMMANDS);
        if (wait_between_commands != null && wait_between_commands.intValue() > 1
                && wait_between_commands.intValue() <= 200) {
            com.setDelayBetweenCommands(wait_between_commands.intValue());
        }
    }

    /**
     * You need a CONFIG_HOST_NAME and CONFIG_ID for a milight bridge handler to initialize correctly.
     * The ID is a unique 12 character long ASCII based on the bridge MAC address (for example ACCF23A20164)
     * and is send as response for a discovery message.
     */
    @Override
    public void initialize() {
        thingDiscoveryService = new ThingDiscoveryService(thing.getUID(), this);
        thingDiscoveryService.start(bundleContext);

        createCommunicationObject();
        setupRefreshTimer();
    }

    private void createCommunicationObject() {
        Object host_config_obj = thing.getConfiguration().get(MilightBindingConstants.CONFIG_HOST_NAME);
        String host_config = ((host_config_obj instanceof String) ? (String) host_config_obj
                : (host_config_obj instanceof InetAddress) ? ((InetAddress) host_config_obj).getHostAddress() : null);

        InetAddress addr = null;

        try {
            addr = InetAddress.getByName(host_config);
        } catch (UnknownHostException ignored) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No address known!");
            return;
        }

        bridgeid = (String) thing.getConfiguration().get(MilightBindingConstants.CONFIG_ID);

        // Version 1/2 do not support response messages / detection. We therefore directly call bridgeDetected(addr).
        if (bridgeid == null || bridgeid.length() != 12) {
            bridgeid = null; // for the case length() != 12
            logger.warn("BridgeID not known. Version 2 fallback behaviour activated, no periodical refresh available!");
            bridgeDetected(addr, "", 2);
            return;
        }

        // The MilightDiscover class is used here for periodically ping the device and update the state.
        if (discover != null) {
            discover.stopReceiving();
        }
        try {
            discover = new MilightDiscover(addr, this, 1000, 3);
            discover.start();
        } catch (SocketException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            return;
        }

        discover.sendDiscover(scheduler);
    }

    /**
     * Sets up the periodically refresh via the scheduler. If the user set CONFIG_REFRESH to 0, no refresh will be
     * done.
     */
    private void setupRefreshTimer() {
        // Version 1/2 do not support response messages / detection.
        if (bridgeid == null) {
            return;
        }

        if (discoverTimer != null) {
            discoverTimer.cancel(true);
        }

        BigDecimal refresh_sec = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_REFRESH_SEC);
        if (refresh_sec == null || refresh_sec.intValue() == 0) {
            refrehIntervalSec = 0;
            return;
        }

        refrehIntervalSec = refresh_sec.intValue();

        // This timer will do the state update periodically.
        discoverTimer = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                discover.sendDiscover(scheduler);
            }
        }, refrehIntervalSec, refrehIntervalSec, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        if (discover != null) {
            discover.stopReceiving();
        }
        if (com != null) {
            com.dispose();
        }
    }

    /**
     * @return Return the protocol communication object. This may be null
     *         if the bridge is offline.
     */
    public QueuedSend getCommunication() {
        return com;
    }

    public MilightV6SessionManager getSessionManager() {
        return session;
    }

    @Override
    public void bridgeDetected(InetAddress addr, String id, int version) {
        if (com != null && bridgeid != null && bridgeid.equals(id)) {
            // throttle to 1sec
            if (last_keep_alive + 1000 < System.currentTimeMillis()) {
                last_keep_alive = System.currentTimeMillis();
                if (session != null) {
                    session.keep_alive();
                    updateProperty(MilightBindingConstants.PROPERTY_SESSIONID, session.getSession());
                }
            }
            return;
        }

        this.bridgeid = id;

        BigDecimal port_config = (BigDecimal) thing.getConfiguration().get(MilightBindingConstants.CONFIG_CUSTOM_PORT);
        if (port_config != null && (port_config.intValue() < 0 || port_config.intValue() > 65000)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No valid port set!");
            return;
        }

        int port;
        if (port_config != null) {
            port = port_config.intValue();
        } else if (version == 2) {
            port = MilightBindingConstants.PORT_VER2;
        } else if (version == 3) {
            port = MilightBindingConstants.PORT_VER3;
        } else if (version == 6) {
            port = MilightBindingConstants.PORT_VER6;
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Protocol version unknown");
            return;
        }

        if (com != null) {
            com.dispose();
            com = null;
        }

        try {
            com = new QueuedSend(addr, port);
        } catch (SocketException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getLocalizedMessage());
            return;
        }

        updateProperty(MilightBindingConstants.PROPERTY_SESSIONID, String.valueOf(version));

        // A bridge may be connected/paired to white/rgbw/rgb bulbs. Unfortunately the bridge does
        // not know which bulbs are present and the bulbs do not have a bidirectional communication.
        // Therefore we present the user all possible bulbs
        // (4 groups each for white/rgbw and 1 for the obsolete rgb bulb ).

        if (version < 6) {
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.WHITE_THING_TYPE, this.getThing().getUID(), "0"), "All white");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.WHITE_THING_TYPE, this.getThing().getUID(), "1"),
                    "White (Zone 1)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.WHITE_THING_TYPE, this.getThing().getUID(), "2"),
                    "White (Zone 2)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.WHITE_THING_TYPE, this.getThing().getUID(), "3"),
                    "White (Zone 3)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.WHITE_THING_TYPE, this.getThing().getUID(), "4"),
                    "White (Zone 4)");

            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_THING_TYPE, this.getThing().getUID(), "0"), "All color");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_THING_TYPE, this.getThing().getUID(), "1"),
                    "Color (Zone 1)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_THING_TYPE, this.getThing().getUID(), "2"),
                    "Color (Zone 2)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_THING_TYPE, this.getThing().getUID(), "3"),
                    "Color (Zone 3)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_THING_TYPE, this.getThing().getUID(), "4"),
                    "Color (Zone 4)");
        } else {
            session = new MilightV6SessionManager(com, bridgeid);

            // The iBox has an integrated bridge lamp
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_IBOX_THING_TYPE, this.getThing().getUID(), "0"),
                    "Color (iBox)");

            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_CW_WW_THING_TYPE, this.getThing().getUID(), "0"),
                    "All rgbww color");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_CW_WW_THING_TYPE, this.getThing().getUID(), "1"),
                    "Rgbww Color (Zone 1)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_CW_WW_THING_TYPE, this.getThing().getUID(), "2"),
                    "Rgbww Color (Zone 2)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_CW_WW_THING_TYPE, this.getThing().getUID(), "3"),
                    "Rgbww Color (Zone 3)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_CW_WW_THING_TYPE, this.getThing().getUID(), "4"),
                    "Rgbww Color (Zone 4)");

            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_W_THING_TYPE, this.getThing().getUID(), "0"),
                    "All rgbw color");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_W_THING_TYPE, this.getThing().getUID(), "1"),
                    "Rgbw Color (Zone 1)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_W_THING_TYPE, this.getThing().getUID(), "2"),
                    "Rgbw Color (Zone 2)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_W_THING_TYPE, this.getThing().getUID(), "3"),
                    "Rgbw Color (Zone 3)");
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_W_THING_TYPE, this.getThing().getUID(), "4"),
                    "Rgbw Color (Zone 4)");
        }

        if (version == 2) {
            thingDiscoveryService.addDevice(
                    new ThingUID(MilightBindingConstants.RGB_V2_THING_TYPE, this.getThing().getUID(), "0"),
                    "Color Led (without white channel)");
        }

        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, version == 2 ? "V2 compatibility mode" : "");
    }

    @Override
    public void noBridgeDetected() {
        updateStatus(ThingStatus.OFFLINE);
    }
}
