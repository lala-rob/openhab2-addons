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
import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.yamahareceiver.internal.protocol.HttpXMLSendReceive.ReadWriteEnum;
import org.openhab.binding.yamahareceiver.internal.protocol.ZoneControl.Zone;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class SystemControl {
    /**
     * Receiver state
     *
     */
    public static class State {
        public String host = null;
        // Some AVR information
        public String name = "";
        public String id = "";
        public String version = "";
        public List<Zone> zones = new ArrayList<>();
        public boolean power = false;
    }

    /**
     * We need that called only once. Will give us name, id, version and
     * zone information.
     *
     * @throws IOException
     */
    public void fetchDeviceInformation(HttpXMLSendReceive xml, State state) throws Exception {
        Document doc = xml.postAndGetXmlResponse("<System><Config>GetParam</Config></System>", ReadWriteEnum.GET);
        if (doc == null) {
            return;
        }

        state.host = xml.getHost();

        Node basicStatus = HttpXMLSendReceive.getNode(doc.getFirstChild(), "System/Config");

        Node node;
        String value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Model_Name");
        value = node != null ? node.getTextContent() : "";
        state.name = value;

        node = HttpXMLSendReceive.getNode(basicStatus, "System_ID");
        value = node != null ? node.getTextContent() : "";
        state.id = value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Version");
        value = node != null ? node.getTextContent() : "";
        state.version = value;

        state.zones.clear();

        node = HttpXMLSendReceive.getNode(basicStatus, "Feature_Existence");
        if (node == null) {
            throw new IOException("Zone information not provided!");
        }

        Node subnode;
        subnode = HttpXMLSendReceive.getNode(node, "Main_Zone");
        value = subnode != null ? subnode.getTextContent() : null;
        if (value != null && (value.equals("1") || value.equals("Available"))) {
            state.zones.add(Zone.Main_Zone);
        }

        subnode = HttpXMLSendReceive.getNode(node, "Zone_2");
        value = subnode != null ? subnode.getTextContent() : null;
        if (value != null && (value.equals("1") || value.equals("Available"))) {
            state.zones.add(Zone.Zone_2);
        }
        subnode = HttpXMLSendReceive.getNode(node, "Zone_3");
        value = subnode != null ? subnode.getTextContent() : null;
        if (value != null && (value.equals("1") || value.equals("Available"))) {
            state.zones.add(Zone.Zone_3);
        }
        subnode = HttpXMLSendReceive.getNode(node, "Zone_4");
        value = subnode != null ? subnode.getTextContent() : null;
        if (value != null && (value.equals("1") || value.equals("Available"))) {
            state.zones.add(Zone.Zone_4);
        }
    }

    public void fetchPowerInformation(HttpXMLSendReceive xml, State state) throws Exception {
        Document doc = xml.postAndGetXmlResponse("<System><Power_Control>GetParam</Power_Control></System>",
                ReadWriteEnum.GET);
        if (doc == null) {
            return;
        }

        Node basicStatus = HttpXMLSendReceive.getNode(doc.getFirstChild(), "System/Power_Control");

        Node node;
        String value;

        node = HttpXMLSendReceive.getNode(basicStatus, "Power");
        value = node != null ? node.getTextContent() : "";
        state.power = (value.equals("On"));
    }

    public void setPower(HttpXMLSendReceive xml, boolean power, State state) throws Exception {
        String str = power ? "On" : "Standby";
        xml.postPut("<System><Power_Control><Power>" + str + "</Power></Power_Control></System>");
        fetchPowerInformation(xml, state);
    }
}
