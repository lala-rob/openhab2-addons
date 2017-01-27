/**
 * Copyright (c) IBOX_LED_ID14-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.milight.internal.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The milightV6 protocol is stateful and needs an established session for each client.
 * This class handles the password bytes, session bytes and sequence number.
 *
 * @author David Graeff <david.graeff@web.de>
 * @since 2.1
 */
public class MilightV6SessionManager implements Runnable {
    protected static final Logger logger = LoggerFactory.getLogger(MilightV6SessionManager.class);

    // The used sequence number for a command will be present in the response if the iBox. This
    // allows us to identify failed command deliveries.
    private byte sequence_no = 0;
    // Password bytes 1 and 2
    private byte pw1 = 0;
    private byte pw2 = 0;
    // Session bytes 1 and 2
    private byte sid1 = 0;
    private byte sid2 = 0;
    private boolean sessionValid = false;
    private boolean willbeclosed = false;
    private Map<Byte, Long> used_sequence_no = new TreeMap<Byte, Long>();
    private boolean wait_for_session_still_valid_confirmation = false;
    private Thread thread;

    private final QueuedSend sendQueue;
    private final String bridgeId;

    public MilightV6SessionManager(QueuedSend sendQueue, String bridgeId) {
        this.sendQueue = sendQueue;
        this.bridgeId = bridgeId;
        thread = new Thread(this);
        thread.start();
    }

    public byte getPw1() {
        return pw1;
    }

    public byte getPw2() {
        return pw2;
    }

    public byte getSid1() {
        return sid1;
    }

    public byte getSid2() {
        return sid2;
    }

    public void setPasswordBytes(byte pw1, byte pw2) {
        this.pw1 = pw1;
        this.pw2 = pw2;
    }

    public void setSessionID(byte sid1, byte sid2) {
        this.sid1 = sid1;
        this.sid2 = sid2;
        sessionValid = true;
    }

    public String getSession() {
        return String.format("%02X %02X", sid1, sid2);
    }

    byte getNextSequenceNo() {
        byte t = sequence_no;
        long current = System.currentTimeMillis();
        used_sequence_no.put(t, current);
        // Check old seq no:
        for (Iterator<Map.Entry<Byte, Long>> it = used_sequence_no.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Byte, Long> entry = it.next();
            if (entry.getValue() + 2000 < current) {
                logger.error("Command not confirmed: " + entry.getKey());
                it.remove();
            }
        }
        ++sequence_no;
        return t;
    }

    public void dispose() {
        willbeclosed = true;
        if (thread != null) {
            try {
                thread.join(100);
            } catch (InterruptedException e) {
            }
            thread.interrupt();
        }
        thread = null;
    }

    private byte[] make_reg() {
        byte[] t = { (byte) 0x80, 0x00, 0x00, 0x00, 0x11, sid1, sid2, 0x00, getNextSequenceNo(), 0x00, 0x33, pw1, pw2,
                0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

        t[21] = 0x33;
        return t;
    }

    // 0x20,0x00,0x00,0x00,0x16,0x02,0x62,0x3a,0xd5,0xed,0xa3,0x01,0xae,0x08,0x2d,0x46,0x61,0x41,0xa7,0xf6,0xdc,0xaf,0xfe,0xf7,0x00,0x00,0x1e
    public void keep_alive() {
        if (wait_for_session_still_valid_confirmation) {
            sessionValid = false;
            wait_for_session_still_valid_confirmation = false;
        }

        if (!sessionValid) {
            // This message is send to get the session bytes.
            final byte B1 = (byte) 0xD3; // (byte) 0xfe; // de
            final byte B2 = (byte) 0xE6; // (byte) 0xf7; // 7b
            sendQueue.queue(
                    new byte[] { (byte) 0x20, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x16, (byte) 0x02,
                            (byte) 0x62, (byte) 0x3A, (byte) 0xD5, (byte) 0xED, (byte) 0xA3, (byte) 0x01, (byte) 0xAE,
                            (byte) 0x08, (byte) 0x2D, (byte) 0x46, (byte) 0x61, (byte) 0x41, (byte) 0xA7, (byte) 0xF6,
                            (byte) 0xDC, (byte) 0xAF, B1, B2, (byte) 0x00, (byte) 0x00, (byte) 0x1E },
                    AbstractBulbInterface.CAT_DISCOVER, false);

        } else {
            // Keep alive messages
            wait_for_session_still_valid_confirmation = true;
            sendQueue.queue(new byte[] { (byte) 0xD0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, sid1, sid2 },
                    AbstractBulbInterface.CAT_KEEP_ALIVE, false);
        }
    }

    protected void logUnknownPacket(byte[] data, int len, String reason) {
        StringBuffer s = new StringBuffer();
        for (int i = 0; i < len; ++i) {
            s.append(String.format("%02X ", data[i]));
        }
        logger.error(reason + ": " + s.toString());
    }

    @Override
    public void run() {

        try {
            logger.debug("MilightCommunicationV6 receive thread ready");
            byte[] buffer = new byte[1024];
            DatagramPacket r_packet = new DatagramPacket(buffer, buffer.length);

            keep_alive();

            // Now loop forever, waiting to receive packets and printing them.
            while (!willbeclosed) {
                sendQueue.getSocket().receive(r_packet);
                int len = r_packet.getLength();

                if (len < 5 || buffer[1] != 0 || buffer[2] != 0 || buffer[3] != 0) {
                    logUnknownPacket(buffer, len, "Not an iBox response!");
                    continue;
                }

                int expected_len = buffer[4] + 5;

                // Response to the keepAlive() packet if session is not valid yet.
                // Should contain the session ids
                switch (buffer[0]) {
                    case (byte) 0x28: {
                        if (expected_len > len) {
                            logUnknownPacket(buffer, len, "Unexpected size for session ids!");
                            continue;
                        }

                        String remote_id = String.format("%02X%02X%02X%02X%02X%02X", buffer[7], buffer[8], buffer[9],
                                buffer[10], buffer[11], buffer[12]);
                        if (remote_id.equals(bridgeId)) {
                            logger.debug("Session ID received:" + String.format("%02X %02X", buffer[19], buffer[20]));
                            logUnknownPacket(buffer, len, "Debug session id packet");
                            setSessionID(buffer[19], buffer[20]);
                            // Send registration command
                            sendQueue.queue(make_reg(), AbstractBulbInterface.CAT_DISCOVER, false);
                        } else {
                            logger.error("Session ID received, but bridgeid wrong"
                                    + String.format("%02X %02X", buffer[19], buffer[20]) + " " + remote_id);
                            logUnknownPacket(buffer, len, "ID not matching");
                        }

                        break;
                    }
                    case (byte) 0x80: {
                        if (expected_len > len) {
                            logUnknownPacket(buffer, len, "Unexpected size for confirm reg!");
                            continue;
                        }
                        String remote_id = String.format("%02X%02X%02X%02X%02X%02X", buffer[5], buffer[6], buffer[7],
                                buffer[8], buffer[9], buffer[10]);
                        if (remote_id.equals(bridgeId)) {
                            wait_for_session_still_valid_confirmation = false;
                            logger.debug("Registration complete");
                        } else {
                            logger.error("Registration failed, not a matching bridge ID");
                            logUnknownPacket(buffer, len, "ID not matching");
                        }
                        break;
                    }
                    case (byte) 0x88:
                        // 88 00 00 00 03 00 SN 00
                        if (expected_len > len) {
                            logUnknownPacket(buffer, len, "Unexpected size for confirmation!");
                            continue;
                        }

                        used_sequence_no.remove(buffer[6]);
                        if (buffer[07] == 0) {
                            logger.debug("Confirmation received for command:" + String.valueOf(buffer[6]));
                        } else {
                            logger.error("Bridge reports an error for command:" + String.valueOf(buffer[6]));
                        }
                        break;
                    case (byte) 0xD8: {// Response to the keepAlive() packet including the mac of the
                                       // bridge (==bridgeID)
                        // D8 00 00 00 07 (AC CF 23 F5 7A D4) 01
                        if (expected_len > len) {
                            logUnknownPacket(buffer, len, "Unexpected size for keep alive!");
                            continue;
                        }

                        String remote_id = String.format("%02X%02X%02X%02X%02X%02X", buffer[5], buffer[6], buffer[7],
                                buffer[8], buffer[9], buffer[10]);
                        if (remote_id.equals(bridgeId)) {
                            wait_for_session_still_valid_confirmation = false;
                            logger.debug("Keep alive received");
                        } else {
                            logger.error("Keep alive received but not matching bridge ID");
                            logUnknownPacket(buffer, len, "ID not matching");
                        }
                        break;
                    }
                    default:
                        logUnknownPacket(buffer, len, "No valid start byte");

                }

                // Reset the length of the packet before reusing it.
                r_packet.setLength(buffer.length);
            }
        } catch (IOException e) {
            if (!willbeclosed) {
                logger.error(e.getLocalizedMessage());
            }
        }
        logger.debug("MilightCommunicationV6 receive thread ready stopped");
    }

    public void setSequenceNumber(byte number) {
        this.sequence_no = number;
    }

    public boolean isValid() {
        return sessionValid;
    }

}
