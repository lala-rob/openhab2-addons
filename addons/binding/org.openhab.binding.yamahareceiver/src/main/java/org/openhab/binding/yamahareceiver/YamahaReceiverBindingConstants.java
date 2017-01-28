/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.yamahareceiver;

import java.util.Collections;
import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.Sets;

/**
 * The {@link YamahaReceiver2Binding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author David Graeff <david.graeff@web.de>
 */
public class YamahaReceiverBindingConstants {

    public static final String BINDING_ID = "yamahareceiver";

    // List of all Thing Type UIDs
    public final static ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, "yamahaAV");
    public final static ThingTypeUID ZONE_THING_TYPE = new ThingTypeUID(BINDING_ID, "zone");

    public final static Set<ThingTypeUID> BRIDGE_THING_TYPES_UIDS = Collections.singleton(BRIDGE_THING_TYPE);
    public static final Set<ThingTypeUID> ZONE_THING_TYPES_UIDS = Sets.newHashSet(ZONE_THING_TYPE);

    public final static String DEFAULT_NET_RADIO_MENU_DIR = "Bookmarks/__My_Favorites";

    // List of channel IDs for zone control (except power which is also a non-zone/bridge channel)
    public final static String CHANNEL_POWER = "power";
    public final static String CHANNEL_INPUT = "input";
    public final static String CHANNEL_SURROUND = "surroundProgram";
    public final static String CHANNEL_VOLUME = "volume";
    public final static String CHANNEL_VOLUME_DB = "volumeDB";
    public final static String CHANNEL_MUTE = "mute";

    public final static String CHANNEL_VERSION = "version";
    public final static String CHANNEL_ASSIGNED_NAME = "assigned_name";

    // List of channel IDs for navigation control: Read/Write
    public final static String CHANNEL_NAVIGATION_MENU = "navigation_menu"; // Navigate either in the current menu
                                                                            // or to the full menu path if "/" is used.
    // List of channel IDs for navigation control: Write only
    public final static String CHANNEL_NAVIGATION_UPDOWN = "navigation_updown"; // UpDown; Change current line
    public final static String CHANNEL_NAVIGATION_LEFTRIGHT = "navigation_leftright"; // UpDown Type
    public final static String CHANNEL_NAVIGATION_SELECT = "navigation_select"; // Switch Type
    public final static String CHANNEL_NAVIGATION_BACK = "navigation_back"; // Switch Type
    public final static String CHANNEL_NAVIGATION_BACKTOROOT = "navigation_backtoroot"; // Switch Type

    // List of channel IDs for navigation control: Read only
    public final static String CHANNEL_NAVIGATION_LEVEL = "navigation_level"; // DecType
    public final static String CHANNEL_NAVIGATION_CURRENT_ITEM = "navigation_current_item"; // DecType
    public final static String CHANNEL_NAVIGATION_TOTAL_ITEMS = "navigation_total_items"; // DecType

    public static final Set<String> CHANNELS_NAVIGATION = Sets.newHashSet(CHANNEL_NAVIGATION_MENU,
            CHANNEL_NAVIGATION_CURRENT_ITEM, CHANNEL_NAVIGATION_UPDOWN, CHANNEL_NAVIGATION_LEFTRIGHT,
            CHANNEL_NAVIGATION_SELECT, CHANNEL_NAVIGATION_BACK, CHANNEL_NAVIGATION_BACKTOROOT, CHANNEL_NAVIGATION_LEVEL,
            CHANNEL_NAVIGATION_TOTAL_ITEMS);

    // List of channel IDs for playback control
    public final static String CHANNEL_PLAYBACK_PRESET = "preset"; // Preset number; RW
    public final static String CHANNEL_PLAYBACK = "playback"; // Play,Pause,Stop,FastFW,Rewind,Next,Previous.
                                                              // Will show the current state as String.
    // List of channel IDs for playback control: Read only
    public final static String CHANNEL_PLAYBACK_STATION = "playback_station";
    public final static String CHANNEL_PLAYBACK_ARTIST = "playback_artist";
    public final static String CHANNEL_PLAYBACK_ALBUM = "playback_album";
    public final static String CHANNEL_PLAYBACK_SONG = "playback_song";

    public static final Set<String> CHANNELS_PLAYBACK = Sets.newHashSet(CHANNEL_PLAYBACK_PRESET, CHANNEL_PLAYBACK,
            CHANNEL_PLAYBACK_STATION, CHANNEL_PLAYBACK_ARTIST, CHANNEL_PLAYBACK_ALBUM, CHANNEL_PLAYBACK_SONG);

    public static final String UPNP_TYPE = "MediaRenderer";

    public static final CharSequence UPNP_MANUFACTURER = "YAMAHA";

    public static final String CONFIG_REFRESH = "REFRESH_IN_SEC";
    public static final String CONFIG_HOST_NAME = "HOST";
    public static final String CONFIG_ZONE = "ZONE";
    public static final String CONFIG_RELVOLUMECHANGE = "RELVOLUMECHANGE";
    public static final String CONFIG_NETRADIOMENU = "NETRADIOMENU";

    public static final String CHANNEL_GROUP_PLAYBACK = "playback_channels";
    public static final String CHANNEL_GROUP_NAVIGATION = "navigation_channels";
    public static final String CHANNEL_GROUP_ZONE = "zone_channels";
}
