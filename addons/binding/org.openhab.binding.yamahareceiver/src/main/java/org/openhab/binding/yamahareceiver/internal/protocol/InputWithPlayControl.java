/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.yamahareceiver.internal.protocol;

import java.lang.ref.WeakReference;
import java.util.Set;

import org.openhab.binding.yamahareceiver.internal.protocol.HttpXMLSendReceive.ReadWriteEnum;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.google.common.collect.Sets;

/**
 * Yamaha Receiver protocol related to navigation functionally. USB, NET_RADIO, IPOD and
 * other inputs are using the same way of playback control.
 *
 * The XML nodes <Play_Info> and <Play_Control> are used.
 *
 * Example:
 *
 * InputWithPlayControl menu = new InputWithPlayControl("NET_RADIO", com_object);
 * menu.goToPath(menuDir);
 * menu.selectItem(stationName);
 *
 * @author David Graeff
 * @since 2.0.0
 */
public class InputWithPlayControl {
    protected final WeakReference<HttpXMLSendReceive> com;

    protected final String inputID;

    public static Set<String> supportedInputs = Sets.newHashSet("TUNER", "NET_RADIO", "USB", "DOCK", "iPOD_USB", "PC",
            "Napster", "Pandora", "SIRIUS", "Rhapsody", "Bluetooth");

    public static class PlayInfoState {
        public String Station; // NET_RADIO. Will also be used for TUNER where Radio_Text_A/B will be used instead.
        public String Artist; // USB, iPOD, PC
        public String Album; // USB, iPOD, PC
        public String Song; // USB, iPOD, PC

        public String playbackMode = "Stop"; // All inputs
    }

    public static class PlayControlState {
        public int presetChannel = 0; // NET_RADIO, RADIO, HD_RADIO, iPOD, USB, PC
    }

    public interface Listener {
        void playInfoUpdated(PlayInfoState msg);

        void playControlUpdated(PlayControlState msg);
    }

    private Listener observer;

    /**
     * Create a InputWithPlayControl object for altering menu positions and requesting current menu information as well
     * as controlling the playback and choosing a preset item.
     *
     * @param inputID The input ID like USB or NET_RADIO.
     * @param com The Yamaha communication object to send http requests.
     */
    public InputWithPlayControl(String inputID, HttpXMLSendReceive com, Listener observer) {
        this.inputID = inputID;
        this.com = new WeakReference<HttpXMLSendReceive>(com);
        this.observer = observer;
    }

    /**
     * Wraps the XML message with the inputID tags. Example with inputID=NET_RADIO:
     * <NETRADIO>message</NETRADIO>.
     *
     * @param message XML message
     * @return
     */
    protected String wrInput(String message) {
        return "<" + inputID + ">" + message + "</" + inputID + ">";
    }

    /**
     * Updates the playback information
     *
     * @throws Exception
     */
    public void updatePlaybackInformation() throws Exception {
        if (observer == null) {
            return;
        }
        Document doc = com.get().postAndGetXmlResponse(wrInput("<Play_Info>GetParam</Play_Info>"), ReadWriteEnum.GET);

        PlayInfoState msg = new PlayInfoState();

        Node playbackInfoNode = HttpXMLSendReceive.getNode(doc.getFirstChild(), "Play_Info/Playback_Info");
        msg.playbackMode = playbackInfoNode != null ? playbackInfoNode.getTextContent() : "";

        Node metaInfoNode = HttpXMLSendReceive.getNode(doc.getFirstChild(), "Play_Info/Meta_Info");
        Node sub;

        if (inputID.equals("TUNER")) {
            sub = HttpXMLSendReceive.getNode(metaInfoNode, "Radio_Text_A");
            msg.Station = sub != null ? sub.getTextContent() : "";
        } else {
            sub = HttpXMLSendReceive.getNode(metaInfoNode, "Station");
            msg.Station = sub != null ? sub.getTextContent() : "";
        }

        sub = HttpXMLSendReceive.getNode(metaInfoNode, "Artist");
        msg.Artist = sub != null ? sub.getTextContent() : "";

        sub = HttpXMLSendReceive.getNode(metaInfoNode, "Album");
        msg.Album = sub != null ? sub.getTextContent() : "";

        sub = HttpXMLSendReceive.getNode(metaInfoNode, "Song");
        msg.Song = sub != null ? sub.getTextContent() : "";

        observer.playInfoUpdated(msg);
    }

    /**
     * Updates the preset information
     *
     * @throws Exception
     */
    public void updatePresetInformation() throws Exception {
        if (observer == null) {
            return;
        }
        Document doc = com.get().postAndGetXmlResponse(wrInput("<Play_Control>GetParam</Play_Control>"),
                ReadWriteEnum.GET);

        PlayControlState msg = new PlayControlState();

        Node playbackInfoNode = HttpXMLSendReceive.getNode(doc.getFirstChild(), "Play_Control/Preset/Preset_Sel");
        msg.presetChannel = playbackInfoNode != null ? Integer.valueOf(playbackInfoNode.getTextContent()) : -1;

        observer.playControlUpdated(msg);
    }

    /**
     * Select a preset channel.
     *
     * @param number The preset position [1,40]
     * @throws Exception
     */
    public void selectItemByPresetNumber(int presetChannel) throws Exception {
        com.get().postPut(wrInput(
                "<Play_Control><Preset><Preset_Sel>" + presetChannel + "</Preset_Sel></Preset></Play_Control>"));
        updatePresetInformation();
    }

    /**
     * Start the playback of the content which is usually selected by the means of the Navigation control class or
     * which has been stopped by stop().
     *
     * @throws Exception
     */
    public void play() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>Play</Playback></Play_Control>"));
        updatePlaybackInformation();
    }

    /**
     * Stop the currently playing content. Use start() to start again.
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>Stop</Playback></Play_Control>"));
        updatePlaybackInformation();
    }

    /**
     * Pause the currently playing content. This is not available for streaming content like on NET_RADIO.
     *
     * @throws Exception
     */
    public void pause() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>Pause</Playback></Play_Control>"));
        updatePlaybackInformation();
    }

    /**
     * Skip forward. This is not available for streaming content like on NET_RADIO.
     *
     * @throws Exception
     */
    public void skipFF() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>Skip Fwd</Playback></Play_Control>"));
    }

    /**
     * Skip reverse. This is not available for streaming content like on NET_RADIO.
     *
     * @throws Exception
     */
    public void skipREV() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>Skip Rev</Playback></Play_Control>"));
    }

    /**
     * Next track. This is not available for streaming content like on NET_RADIO.
     *
     * @throws Exception
     */
    public void nextTrack() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>>>|</Playback></Play_Control>"));
        updatePlaybackInformation();
    }

    /**
     * Previous track. This is not available for streaming content like on NET_RADIO.
     *
     * @throws Exception
     */
    public void previousTrack() throws Exception {
        com.get().postPut(wrInput("<Play_Control><Playback>|<<</Playback></Play_Control>"));
        updatePlaybackInformation();
    }

}
