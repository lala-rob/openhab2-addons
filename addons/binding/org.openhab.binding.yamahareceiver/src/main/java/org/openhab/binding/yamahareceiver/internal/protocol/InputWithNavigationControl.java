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
import java.util.Set;

import org.openhab.binding.yamahareceiver.internal.protocol.HttpXMLSendReceive.ReadWriteEnum;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.google.common.collect.Sets;

/**
 * This class implements the Yamaha Receiver protocol related to navigation functionally. USB, NET_RADIO, IPOD and
 * other inputs are using the same way of navigating through menus. A menu on Yamaha AVRs
 * is hierarchically organised. Entries are divided into pages with 8 elements per page.
 *
 * The XML nodes <List_Control> and <List_Info> are used.
 *
 * In contrast to other protocol classes an object of this type will store state information,
 * because it caches the received XML information of the updateNavigationState(). This makes
 * the API less cluttered.
 *
 * Example:
 *
 * NavigationControl menu = new NavigationControl("NET_RADIO", com_object);
 * menu.goToPath(menuDir);
 * menu.selectItem(stationName);
 *
 * @author Dennis Frommknecht
 * @author David Graeff
 * @since 2.0.0
 */
public class InputWithNavigationControl {
    protected final WeakReference<HttpXMLSendReceive> com;

    protected final String inputID;

    public static Set<String> supportedInputs = Sets.newHashSet("NET_RADIO", "USB", "DOCK", "iPOD_USB", "PC", "Napster",
            "Pandora", "SIRIUS", "Rhapsody");

    /**
     * The current state of the navigation
     */
    public static class State {
        public String Menu_Name = null;
        public int Menu_Layer = -1;
        public int Current_Line = 0;
        public int Max_Line = -1;
        public String items[] = new String[8];
    }

    /// Navigation is cached
    private State cache = new State();

    public interface Listener {
        void navigationUpdated(State msg);
    }

    private Listener observer;

    /**
     * Create a NavigationControl object for altering menu positions and requesting current menu information.
     *
     * @param inputID The input ID like USB or NET_RADIO.
     * @param com The Yamaha communication object to send http requests.
     */
    public InputWithNavigationControl(String inputID, HttpXMLSendReceive com, Listener observer) {
        this.com = new WeakReference<HttpXMLSendReceive>(com);
        this.inputID = inputID;
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

    protected boolean isInDirectory(String path) throws Exception {
        if (cache.Menu_Name == null) {
            return false;
        }

        String[] pathArr = path.split("/");
        // Full path info not available, so guess from last path element and number of path elements
        return getMenuName().equals(pathArr[pathArr.length - 1]) && getLevel() == pathArr.length;
    }

    /**
     * Navigate back
     *
     * @throws Exception
     */
    public void goBack() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Back</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Navigate up
     *
     * @throws Exception
     */
    public void goUp() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Up</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Navigate down
     *
     * @throws Exception
     */
    public void goDown() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Down</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Navigate left. Not for all zones or functions available.
     *
     * @throws Exception
     */
    public void goLeft() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Left</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Navigate right. Not for all zones or functions available.
     *
     * @throws Exception
     */
    public void goRight() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Right</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Select current item. Not for all zones or functions available.
     *
     * @throws Exception
     */
    public void selectCurrentItem() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Select</Cursor></List_Control>"));
        updateNavigationState();
    }

    /**
     * Navigate to root menu
     *
     * @throws Exception
     */
    public void goToRoot() throws Exception {
        com.get().postPut(wrInput("<List_Control><Cursor>Back to Home</Cursor></List_Control>"));
        updateNavigationState();
    }

    public void goToPage(int page) throws Exception {
        int line = (page - 1) * 8 + 1;
        com.get().postPut(wrInput("<List_Control><Jump_Line>" + line + "</Jump_Line></List_Control>"));
        updateNavigationState();
    }

    public void selectItemFullPath(String fullPath) throws Exception {
        updateNavigationState();

        if (!isInDirectory(fullPath)) {
            if (getLevel() > 0) {
                goToRoot();
            }

            String[] pathArr = fullPath.split("/");
            for (String pathElement : pathArr) {
                selectItem(pathElement);
            }
        }
    }

    /**
     * Get the menu name.
     * Operates on a cached XML node! Call refreshMenuState for up-to-date information.
     *
     * @return The menu name
     * @throws Exception
     */
    public String getMenuName() throws Exception {
        return cache.Menu_Name;
    }

    /**
     * Get the menu level.
     * Operates on a cached XML node! Call refreshMenuState for up-to-date information.
     *
     * @return The menu level. -1 if unknown. 0 equals root menu.
     * @throws Exception
     */
    public int getLevel() throws Exception {
        return cache.Menu_Layer;
    }

    /**
     * Get the page number.
     * Operates on a cached XML node! Call refreshMenuState for up-to-date information.
     *
     * @return The page number. Each page contains 8 items.
     * @throws Exception
     */
    public int getCurrentItemNumber() throws Exception {
        return cache.Current_Line;
    }

    /**
     * Get the page numbers.
     * Operates on a cached XML node! Call refreshMenuState for up-to-date information.
     *
     * @return The page numbers. Each page contains 8 items.
     * @throws Exception
     */
    public int getNumberOfItems() throws Exception {
        return cache.Max_Line;
    }

    /**
     * Finds an item on the current page. A page contains up to 8 items.
     * Operates on a cached XML node! Call refreshMenuState for up-to-date information.
     *
     * @return Return the item index [1,8] or -1 if not found.
     * @throws Exception
     */
    private int findItemOnCurrentPage(String itemName) throws Exception {
        for (int i = 1; i <= 8; i++) {
            if (itemName.equals(cache.items[i - 1])) {
                return i;
            }
        }

        return -1;
    }

    public void selectItem(String name) throws Exception {
        final int pageCount = (int) Math.ceil(cache.Max_Line / 8d);
        final int currentPage = (cache.Current_Line - 1) / 8;

        for (int pageIndex = 1; pageIndex <= pageCount; pageIndex++) {
            if (currentPage != pageIndex) {
                goToPage(pageIndex);
            }

            int index = findItemOnCurrentPage(name);
            if (index > 0) {
                com.get().postPut(wrInput("<List_Control><Direct_Sel>Line_" + index + "</Direct_Sel></List_Control>"));
                updateNavigationState();
                return;
            }
        }

        throw new IOException("Item '" + name + "' doesn't exist in menu " + getMenuName());
    }

    /**
     * Refreshes the menu state and caches the List_Info node from the response. This method may take
     * some time because it retries the request for up to MENU_MAX_WAITING_TIME or the menu state reports
     * "Ready", whatever comes first.
     *
     * @throws Exception
     */
    public void updateNavigationState() throws Exception {
        int totalWaitingTime = 0;

        Document doc;
        Node currentMenu;

        while (true) {
            doc = com.get().postAndGetXmlResponse(wrInput("<List_Info>GetParam</List_Info>"), ReadWriteEnum.GET);
            currentMenu = HttpXMLSendReceive.getNode(doc.getFirstChild(), "List_Info");

            Node nodeMenuState = HttpXMLSendReceive.getNode(currentMenu, "Menu_Status");

            if (nodeMenuState == null || nodeMenuState.getTextContent().equals("Ready")) {
                break;
            }

            totalWaitingTime += ZoneControl.MENU_RETRY_DELAY;
            if (totalWaitingTime > ZoneControl.MENU_MAX_WAITING_TIME) {
                throw new IOException("Menu still not ready after " + ZoneControl.MENU_MAX_WAITING_TIME + "ms");
            }

            try {
                Thread.sleep(ZoneControl.MENU_RETRY_DELAY);
            } catch (InterruptedException e) {
                // Ignore and just retry immediately
            }
        }

        Node node = HttpXMLSendReceive.getNode(currentMenu, "Menu_Name");
        if (node == null) {
            throw new IOException("Menu_Name child in parent node missing!");
        }

        cache.Menu_Name = node.getTextContent();

        node = HttpXMLSendReceive.getNode(currentMenu, "Menu_Layer");
        if (node == null) {
            throw new IOException("Menu_Layer child in parent node missing!");
        }

        cache.Menu_Layer = Integer.parseInt(node.getTextContent()) - 1;

        node = HttpXMLSendReceive.getNode(currentMenu, "Cursor_Position/Current_Line");
        if (node == null) {
            throw new IOException("Cursor_Position/Current_Line child in parent node missing!");
        }

        int currentLine = Integer.parseInt(node.getTextContent());
        cache.Current_Line = currentLine;

        node = HttpXMLSendReceive.getNode(currentMenu, "Cursor_Position/Max_Line");
        if (node == null) {
            throw new IOException("Cursor_Position/Max_Line child in parent node missing!");
        }

        int maxLines = Integer.parseInt(node.getTextContent());
        cache.Max_Line = maxLines;

        for (int i = 1; i < 8; ++i) {
            node = HttpXMLSendReceive.getNode(currentMenu, "Current_List/Line_" + i + "/Txt");
            cache.items[i - 1] = node != null ? node.getTextContent() : null;
        }

        if (observer != null) {
            observer.navigationUpdated(cache);
        }
    }
}
