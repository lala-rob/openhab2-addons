/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.yamahareceiver.internal.protocol;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.yamahareceiver.internal.protocol.HttpXMLSendReceive.ReadWriteEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Zone protocol class used to control one zone of a Yamaha receiver with HTTP/xml.get().
 * No state will be saved here, but in {@link State} instead.
 *
 * @author David Gräff <david.graeff@tu-dortmund.de>
 * @author Eric Thill
 * @author Ben Jones
 * @since 1.6.0
 */
public class ZoneControl {
    /**
     * The state of a specific zone of a Yamaha receiver.
     *
     * @author David Graeff <david.graeff@web.de>
     */
    public static class State {
        public boolean power = false;
        public String inputName = "";
        public String inputID = "";
        public String surroundProgram = "";
        public float volume = 0.0f; // volume in percent
        public boolean mute = false;
    }

    public static class AvailableInputState {
        public List<String> available_inputs = new ArrayList<String>();
    }

    public interface Listener {
        void zoneStateChanged(State msg);

        void availableInputsChanged(AvailableInputState msg);
    }

    private Listener observer;
    private Logger logger = LoggerFactory.getLogger(ZoneControl.class);

    /**
     * The names of this enum are part of the protocol!
     * Receivers have different capabilities, some have 2 zones, some up to 4.
     * Every receiver has a "Main_Zone".
     */
    public enum Zone {
        Main_Zone,
        Zone_2,
        Zone_3,
        Zone_4;
    }

    /**
     * The volume min and max is the same for all supported devices.
     */
    public static final int VOLUME_MIN = -80;
    public static final int VOLUME_MAX = 12;
    public static final int VOLUME_RANGE = -VOLUME_MIN + VOLUME_MAX;

    // Menu navigation timeouts
    public static final int MENU_RETRY_DELAY = 500;
    public static final int MENU_MAX_WAITING_TIME = 5000;

    // The address of the receiver.
    private final WeakReference<HttpXMLSendReceive> xml;
    private final Zone zone;

    // Creates a yamaha protol connection object.
    // All commands always refer to a zone. A ZoneControl object
    // therefore consists of a AVR connection and a zone.
    public ZoneControl(HttpXMLSendReceive xml, Zone zone, Listener observer) {
        this.xml = new WeakReference<HttpXMLSendReceive>(xml);
        this.zone = zone;
        this.observer = observer;
    }

    /**
     * Return the zone
     */
    public Zone getZone() {
        return zone;
    }

    /**
     * Wraps the XML message with the zone tags. Example with zone=Main_Zone:
     * <Main_Zone>message</Main_Zone>.
     *
     * @param message XML message
     * @return
     */
    protected String wrZone(String message) {
        return "<" + zone.name() + ">" + message + "</" + zone.name() + ">";
    }

    public void setPower(boolean on) throws Exception {
        if (on) {
            xml.get().postPut(wrZone("<Power_Control><Power>On</Power></Power_Control>"));
        } else {
            xml.get().postPut(wrZone("<Power_Control><Power>Standby</Power></Power_Control>"));
        }
        updateState();
    }

    /**
     * Sets the absolute volume in decibel.
     *
     * @param volume Absolute value in decibel ([-80,+12]).
     * @throws IOException
     */
    public void setVolumeDB(float volume) throws Exception {
        if (volume < VOLUME_MIN) {
            volume = VOLUME_MIN;
        }
        if (volume > VOLUME_MAX) {
            volume = VOLUME_MAX;
        }
        int vol = (int) volume * 10;
        xml.get().postPut(wrZone(
                "<Volume><Lvl><Val>" + String.valueOf(vol) + "</Val><Exp>1</Exp><Unit>dB</Unit></Lvl></Volume>"));

        updateState();
    }

    /**
     * Sets the volume in percent
     *
     * @param volume
     * @throws IOException
     */
    public void setVolume(float volume) throws Exception {
        if (volume < 0) {
            volume = 0;
        }
        if (volume > 100) {
            volume = 100;
        }
        // Compute value in db
        setVolumeDB(volume * ZoneControl.VOLUME_RANGE / 100.0f + ZoneControl.VOLUME_MIN);
    }

    /**
     * Increase or decrease the volume by the given percentage.
     *
     * @param percent
     * @throws IOException
     */
    public void setVolumeRelative(State state, float percent) throws Exception {
        setVolume(state.volume + percent);
    }

    public void setMute(boolean mute) throws Exception {
        if (mute) {
            xml.get().postPut(wrZone("<Volume><Mute>On</Mute></Volume>"));
        } else {
            xml.get().postPut(wrZone("<Volume><Mute>Off</Mute></Volume>"));
        }
        updateState();
    }

    public void setInput(String name) throws Exception {
        xml.get().postPut(wrZone("<Input><Input_Sel>" + name + "</Input_Sel></Input>"));
        updateState();
    }

    public void setSurroundProgram(String name) throws Exception {
        if (name.toLowerCase().equals("straight")) {
            xml.get().postPut(wrZone(
                    "<Surround><Program_Sel><Current><Straight>On</Straight></Current></Program_Sel></Surround>"));
        } else {
            xml.get().postPut(wrZone("<Surround><Program_Sel><Current><Sound_Program>" + name
                    + "</Sound_Program></Current></Program_Sel></Surround>"));
        }

        updateState();
    }

    public void updateState() throws Exception {
        if (observer == null) {
            return;
        }

        Document doc = xml.get().postAndGetXmlResponse(wrZone("<Basic_Status>GetParam</Basic_Status>"),
                ReadWriteEnum.GET);
        Node basicStatus = HttpXMLSendReceive.getNode(doc.getFirstChild(), zone + "/Basic_Status");

        Node node;
        String value;

        State state = new State();

        node = HttpXMLSendReceive.getNode(basicStatus, "Power_Control/Power");
        value = node != null ? node.getTextContent() : "";
        state.power = "On".equalsIgnoreCase(value);

        node = HttpXMLSendReceive.getNode(basicStatus, "Input/Input_Sel");
        value = node != null ? node.getTextContent() : "";
        state.inputName = value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Input/Input_Sel_Item_Info/Src_Name");
        value = node != null ? node.getTextContent() : "";
        state.inputID = value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Surround/Program_Sel/Current/Sound_Program");
        value = node != null ? node.getTextContent() : "";
        state.surroundProgram = value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Volume/Lvl/Val");
        value = node != null ? node.getTextContent() : String.valueOf(VOLUME_MIN);
        state.volume = Float.parseFloat(value) * .1f; // in DB
        state.volume = (state.volume + -ZoneControl.VOLUME_MIN) * 100.0f / ZoneControl.VOLUME_RANGE; // in percent
        if (state.volume < 0 || state.volume > 100) {
            logger.error("Received volume is out of range: " + String.valueOf(state.volume));
            state.volume = 0;
        }

        node = HttpXMLSendReceive.getNode(basicStatus, "Volume/Mute");
        value = node != null ? node.getTextContent() : "";
        state.mute = "On".equalsIgnoreCase(value);

        observer.zoneStateChanged(state);
    }

    public void fetchAvailableInputs() throws Exception {
        if (observer == null) {
            return;
        }

        Document doc = xml.get().postAndGetXmlResponse(
                wrZone("<Input><Input_Sel_Item>GetParam</Input_Sel_Item></Input>"), ReadWriteEnum.GET);
        Node inputSelItem = HttpXMLSendReceive.getNode(doc.getFirstChild(), zone + "/Input/Input_Sel_Item");
        NodeList items = inputSelItem.getChildNodes();

        AvailableInputState state = new AvailableInputState();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String name = item.getElementsByTagName("Param").item(0).getTextContent();
            boolean writable = item.getElementsByTagName("RW").item(0).getTextContent().contains("W");
            if (writable) {
                state.available_inputs.add(name);
            }
        }

        observer.availableInputsChanged(state);
    }

}
