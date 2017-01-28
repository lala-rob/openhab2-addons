/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.yamahareceiver.handler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingStatusInfoBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.yamahareceiver.YamahaReceiverBindingConstants;
import org.openhab.binding.yamahareceiver.discovery.ZoneDiscoveryService;
import org.openhab.binding.yamahareceiver.internal.protocol.HttpXMLSendReceive;
import org.openhab.binding.yamahareceiver.internal.protocol.SystemControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link YamahaBridgeHandler} is responsible for fetching basic information about the
 * found AVR and start the zone detection.
 *
 * @author David Graeff - Initial contribution
 */
public class YamahaBridgeHandler extends BaseBridgeHandler {
    private Logger logger = LoggerFactory.getLogger(YamahaBridgeHandler.class);
    private String host;
    private int refrehInterval = 60; // Default: Every 1min
    private float relativeVolumeChangeFactor = 0.5f; // Default: 0.5 percent
    private long lastRefreshInMS = 0;
    private ScheduledFuture<?> refreshTimer;
    private ZoneDiscoveryService zoneDiscoveryService;
    private String netRadioMenuDir;

    HttpXMLSendReceive xml;
    SystemControl.State state = new SystemControl.State();

    public YamahaBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    public float getRelativeVolumeChangeFactor() {
        return relativeVolumeChangeFactor;
    }

    public String getNetRadioMenuDir() {
        return netRadioMenuDir;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (xml == null || state.host == null) {
            return;
        }

        String id = channelUID.getId();

        try {
            if (command instanceof RefreshType) {
                refreshFromState(channelUID);
                return;
            }

            switch (id) {
                case YamahaReceiverBindingConstants.CHANNEL_POWER: {
                    boolean oldState = state.power;
                    SystemControl basicDeviceInformation = new SystemControl();
                    basicDeviceInformation.setPower(xml, ((OnOffType) command) == OnOffType.ON, state);
                    if (!oldState && state.power) {
                        // If the device was off and no goes on, we cause a refresh of all zone things.
                        // The user could have renamed some of the inputs etc.
                        updateAllZoneInformation();
                    }
                }
                    break;
                default:
                    logger.error("Channel " + id + " not supported!");
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void refreshFromState(ChannelUID channelUID) {
        switch (channelUID.getId()) {
            case YamahaReceiverBindingConstants.CHANNEL_POWER:
                updateState(channelUID, state.power ? OnOffType.ON : OnOffType.OFF);
                break;
            case YamahaReceiverBindingConstants.CHANNEL_VERSION:
                updateState(channelUID, StringType.valueOf(state.version));
                break;
            case YamahaReceiverBindingConstants.CHANNEL_ASSIGNED_NAME:
                updateState(channelUID, StringType.valueOf(state.name));
                break;
            default:
                logger.error("Channel refresh for " + channelUID.getId() + " not implemented!");
        }
    }

    /**
     * Sets up a refresh timer (using the scheduler) with the CONFIG_REFRESH interval.
     *
     * @param initial_wait_time The delay before the first refresh. Maybe 0 to immediately
     *            initiate a refresh.
     */
    private void setupRefreshTimer(int initial_wait_time) {
        if (state == null) {
            return;
        }

        Object interval_config_o = thing.getConfiguration().get(YamahaReceiverBindingConstants.CONFIG_REFRESH);
        Integer interval_config;

        if (interval_config_o == null) {
            interval_config = refrehInterval;
        } else {
            interval_config = interval_config_o instanceof Integer ? (Integer) interval_config_o
                    : ((BigDecimal) interval_config_o).intValue();
        }

        if (refreshTimer != null) {
            refreshTimer.cancel(false);
        }
        refreshTimer = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateAllZoneInformation();
            }
        }, initial_wait_time, interval_config, TimeUnit.SECONDS);

        refrehInterval = interval_config;
    }

    /**
     * Periodically called and called initially.
     */
    void updateAllZoneInformation() {
        try {
            SystemControl basicDeviceInformation = new SystemControl();
            basicDeviceInformation.fetchDeviceInformation(xml, state);
            basicDeviceInformation.fetchPowerInformation(xml, state);

            Bridge bridge = (Bridge) thing;
            List<Thing> things = bridge.getThings();
            for (Thing thing : things) {
                YamahaZoneThingHandler handler = (YamahaZoneThingHandler) thing.getHandler();
                // If thing still thinks that the bridge is offline, update its status.
                if (thing.getStatusInfo().getStatusDetail() == ThingStatusDetail.BRIDGE_OFFLINE) {
                    handler.bridgeStatusChanged(ThingStatusInfoBuilder.create(bridge.getStatus()).build());
                } else {
                    try {
                        handler.updateZoneInformation();
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return;
        }

    }

    /**
     * We handle updates of this thing ourself.
     */
    @Override
    public void thingUpdated(Thing thing) {
        this.thing = thing;

        // Check if host configuration has changed
        String host_config = (String) thing.getConfiguration().get(YamahaReceiverBindingConstants.CONFIG_HOST_NAME);
        if (host_config != null && !host_config.equals(host)) {
            host = host_config;
            createCommunicationObject();
        }

        // Check if refresh configuration has changed
        BigDecimal interval_config = (BigDecimal) thing.getConfiguration()
                .get(YamahaReceiverBindingConstants.CONFIG_REFRESH);
        if (interval_config != null && interval_config.intValue() != refrehInterval) {
            setupRefreshTimer(interval_config.intValue());
        }

        // Read the configuration for the relative volume change factor.
        BigDecimal relVolumeChange = (BigDecimal) thing.getConfiguration()
                .get(YamahaReceiverBindingConstants.CONFIG_RELVOLUMECHANGE);
        if (relVolumeChange != null) {
            relativeVolumeChangeFactor = relVolumeChange.floatValue();
        } else {
            relativeVolumeChangeFactor = 0.5f;
        }

        String netRadioMenuConfig = (String) thing.getConfiguration()
                .get(YamahaReceiverBindingConstants.CONFIG_NETRADIOMENU);
        netRadioMenuDir = netRadioMenuConfig != null ? netRadioMenuConfig
                : YamahaReceiverBindingConstants.DEFAULT_NET_RADIO_MENU_DIR;
    }

    /**
     * Calls createCommunicationObject if the host name is configured correctly.
     */
    @Override
    public void initialize() {
        host = (String) thing.getConfiguration().get(YamahaReceiverBindingConstants.CONFIG_HOST_NAME);

        if (host == null || host.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Hostname not set!");
            return;
        }

        createCommunicationObject();

        zoneDiscoveryService = new ZoneDiscoveryService(state, thing.getUID());
        zoneDiscoveryService.start(bundleContext);
        zoneDiscoveryService.detectZones();
    }

    /**
     * We create a YamahaReceiverState that handles the current state (loudness, power, input etc)
     * and a communication object.
     */
    private void createCommunicationObject() {
        // Read the configuration for the relative volume change factor.
        BigDecimal relVolumeChange = (BigDecimal) thing.getConfiguration()
                .get(YamahaReceiverBindingConstants.CONFIG_RELVOLUMECHANGE);
        if (relVolumeChange != null) {
            relativeVolumeChangeFactor = relVolumeChange.floatValue();
        }

        String netRadioMenuConfig = (String) thing.getConfiguration()
                .get(YamahaReceiverBindingConstants.CONFIG_NETRADIOMENU);
        netRadioMenuDir = netRadioMenuConfig != null ? netRadioMenuConfig
                : YamahaReceiverBindingConstants.DEFAULT_NET_RADIO_MENU_DIR;

        xml = new HttpXMLSendReceive(host);

        SystemControl basicDeviceInformation = new SystemControl();
        try {
            basicDeviceInformation.fetchDeviceInformation(xml, state);
            basicDeviceInformation.fetchPowerInformation(xml, state);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return;
        }

        setupRefreshTimer(0);
    }

    @Override
    public void dispose() {
        if (zoneDiscoveryService != null) {
            zoneDiscoveryService.stop();
            zoneDiscoveryService = null;
        }
    }

    /**
     * @return Return the protocol communication object. This may be null
     *         if the bridge is offline.
     */
    public HttpXMLSendReceive getCommunication() {
        return xml;
    }
}
