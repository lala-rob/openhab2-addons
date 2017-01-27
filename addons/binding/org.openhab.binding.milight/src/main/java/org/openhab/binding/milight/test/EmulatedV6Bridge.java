package org.openhab.binding.milight.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

import org.openhab.binding.milight.MilightBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmulatedV6Bridge {
    protected static final Logger logger = LoggerFactory.getLogger(EmulatedV6Bridge.class);
    private boolean willbeclosed = false;
    private byte SID1 = (byte) 0xed;
    private byte SID2 = (byte) 0xab;
    private byte PW1 = 0;
    private byte PW2 = 0;

    private static final byte GET_SESSION[] = new byte[] { (byte) 0x20, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x16, (byte) 0x02, (byte) 0x62, (byte) 0x3A, (byte) 0xD5, (byte) 0xED, (byte) 0xA3, (byte) 0x01,
            (byte) 0xAE, (byte) 0x08, (byte) 0x2D, (byte) 0x46, (byte) 0x61, (byte) 0x41, (byte) 0xA7, (byte) 0xF6,
            (byte) 0xDC, (byte) 0xAF, (byte) 0xfe, (byte) 0xf7, (byte) 0x00, (byte) 0x00, (byte) 0x1E };

    private static final byte SEARCH[] = new byte[] { 0x10, 0x00, 0x00, 0x00, 0x24, 0x02, (byte) 0xee, 0x3e, 0x02, 0x39,
            0x38, 0x35, 0x62, 0x31, 0x35, 0x37, 0x62, 0x66, 0x36, 0x66, 0x63, 0x34, 0x33, 0x33, 0x36, 0x38, 0x61, 0x36,
            0x33, 0x34, 0x36, 0x37, 0x65, 0x61, 0x33, 0x62, 0x31, 0x39, 0x64, 0x30, 0x64 };

    private byte[] KEEP_ALIVE_REQUEST = { (byte) 0xD0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x1D,
            (byte) 0x00 };

    private byte[] KEEP_ALIVE_RESPONSE = { (byte) 0xD8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0xAC,
            (byte) 0xCF, (byte) 0x23, (byte) 0xF5, (byte) 0x7A, (byte) 0xD4, (byte) 0x01 };

    EmulatedV6Bridge() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runDiscovery();
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                runBrigde();
            }
        }).start();
    }

    public void runDiscovery() {
        final byte DISCOVER[] = "HF-A11ASSISTHREAD".getBytes();

        try {
            byte[] a = new byte[0];
            DatagramPacket s_packet = new DatagramPacket(a, a.length);
            DatagramSocket datagramSocket = new DatagramSocket(MilightBindingConstants.PORT_DISCOVER);

            logger.debug("EmulatedV6Bridge discover thread ready");
            byte[] buffer = new byte[1024];
            DatagramPacket r_packet = new DatagramPacket(buffer, buffer.length);

            // Now loop forever, waiting to receive packets and printing them.
            while (!willbeclosed) {
                datagramSocket.receive(r_packet);
                s_packet.setAddress(r_packet.getAddress());
                s_packet.setPort(r_packet.getPort());

                int len = r_packet.getLength();

                if (len >= DISCOVER.length) {
                    if (Arrays.equals(DISCOVER, Arrays.copyOf(buffer, DISCOVER.length))) {
                        String data = r_packet.getAddress().getHostAddress() + ",ACCF23F57AD4,HF-LPB100";
                        logger.debug("Discover message received. Send: " + data);
                        sendMessage(s_packet, datagramSocket, data.getBytes());
                        continue;
                    }
                }
                logUnknownPacket(buffer, len, "No valid discovery received");

                // Reset the length of the packet before reusing it.
                r_packet.setLength(buffer.length);
            }
        } catch (IOException e) {
            if (willbeclosed) {
                return;
            }
            logger.error(e.getLocalizedMessage());
        }
    }

    public void runBrigde() {
        try {
            byte[] a = new byte[0];
            DatagramPacket s_packet = new DatagramPacket(a, a.length);
            DatagramSocket datagramSocket = new DatagramSocket(MilightBindingConstants.PORT_VER6);

            logger.debug("EmulatedV6Bridge control thread ready");
            byte[] buffer = new byte[1024];
            DatagramPacket r_packet = new DatagramPacket(buffer, buffer.length);

            // Now loop forever, waiting to receive packets and printing them.
            while (!willbeclosed) {
                datagramSocket.receive(r_packet);
                s_packet.setAddress(r_packet.getAddress());
                s_packet.setPort(r_packet.getPort());

                int len = r_packet.getLength();

                if (len < 5 || buffer[1] != 0 || buffer[2] != 0 || buffer[3] != 0) {
                    logUnknownPacket(buffer, len, "Not an iBox request!");
                    continue;
                }

                if (len >= GET_SESSION.length) {
                    if (Arrays.equals(GET_SESSION, Arrays.copyOf(buffer, GET_SESSION.length))) {
                        logger.debug("session get message received");
                        byte response[] = { (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x11,
                                (byte) 0x00, (byte) 0x02, (byte) 0xAC, (byte) 0xCF, (byte) 0x23, (byte) 0xF5,
                                (byte) 0x7A, (byte) 0xD4, (byte) 0x69, (byte) 0xF0, (byte) 0x3C, (byte) 0x23,
                                (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x00 };
                        response[19] = SID1;
                        response[20] = SID2;
                        sendMessage(s_packet, datagramSocket, response);
                        continue;
                    }
                }

                if (len >= SEARCH.length) {
                    if (Arrays.equals(SEARCH, Arrays.copyOf(buffer, SEARCH.length))) {
                        logger.debug("search request received");
                        byte response[] = { (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x11,
                                (byte) 0x00, (byte) 0x02, (byte) 0xAC, (byte) 0xCF, (byte) 0x23, (byte) 0xF5,
                                (byte) 0x7A, (byte) 0xD4, (byte) 0x69, (byte) 0xF0, (byte) 0x3C, (byte) 0x23,
                                (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x00 };
                        response[19] = SID1;
                        response[20] = SID2;
                        sendMessage(s_packet, datagramSocket, response);
                        continue;
                    }
                }
                KEEP_ALIVE_REQUEST[5] = SID1;
                KEEP_ALIVE_REQUEST[6] = SID2;

                if (len >= KEEP_ALIVE_REQUEST.length) {
                    if (Arrays.equals(KEEP_ALIVE_REQUEST, Arrays.copyOf(buffer, KEEP_ALIVE_REQUEST.length))) {
                        logger.debug("keep alive received");
                        sendMessage(s_packet, datagramSocket, KEEP_ALIVE_RESPONSE);
                        continue;
                    }
                }

                if (buffer[0] == (byte) 0x80 && len >= 22) {
                    byte seq = buffer[8];
                    if (buffer[5] != SID1 || buffer[6] != SID2) {
                        logUnknownPacket(buffer, len,
                                "No valid ssid. Current ssid is " + String.format("%02X %02X", SID1, SID2));
                        continue;
                    }
                    if (buffer[11] != PW1 || buffer[12] != PW2) {
                        logUnknownPacket(buffer, len,
                                "No valid password. Current password is " + String.format("%02X %02X", PW1, PW2));
                        continue;
                    }
                    if (buffer[13] == 0x08) {
                        logger.debug("RGBW led switch");
                    } else {
                        logger.debug("iBox led switch");
                    }

                    logger.debug("Zone: " + String.valueOf(buffer[19]));

                    byte chksum = (byte) (buffer[10 + 0] + buffer[10 + 1] + buffer[10 + 2] + buffer[10 + 3]
                            + buffer[10 + 4] + buffer[10 + 5] + buffer[10 + 6] + buffer[10 + 7] + buffer[10 + 8]
                            + buffer[19]);

                    if (chksum != buffer[21]) {
                        logger.error("Checksum wrong:" + String.valueOf(chksum) + " " + String.valueOf(buffer[21]));
                        continue;
                    }

                    // 80 00 00 00 11 {WifiBridgeSessionID1} {WifiBridgeSessionID2} 00 {SequenceNumber} 00 0x31
                    // {PasswordByte1 default 00} {PasswordByte2 default 00} {remoteStyle 08 for RGBW/WW/CW or 00 for
                    // bridge lamp} {LightCommandByte1} {LightCommandByte2} 0x00 0x00 0x00 {Zone1-4 0=All} 0x00
                    // {Checksum}

                    byte response[] = { (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00,
                            seq, (byte) 0x00 };
                    sendMessage(s_packet, datagramSocket, response);
                    continue;
                }

                logUnknownPacket(buffer, len, "Not recognised command");

                // Reset the length of the packet before reusing it.
                r_packet.setLength(buffer.length);
            }
        } catch (

        IOException e) {
            if (willbeclosed) {
                return;
            }
            logger.error(e.getLocalizedMessage());
        }
    }

    protected void logUnknownPacket(byte[] data, int len, String reason) {
        StringBuffer s = new StringBuffer();
        for (int i = 0; i < len; ++i) {
            s.append(String.format("%02X ", data[i]));
        }
        logger.error(reason + ": " + s.toString());
    }

    protected void sendMessage(DatagramPacket packet, DatagramSocket datagramSocket, byte buffer[]) {
        packet.setData(buffer);
        try {
            datagramSocket.send(packet);
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < buffer.length; ++i) {
                s.append(String.format("%02X ", buffer[i]));
            }
            logger.debug("Sent packet '{}' to ({})",
                    new Object[] { s.toString(), packet.getAddress().getHostAddress() });
        } catch (Exception e) {
            logger.error("Failed to send Message to '{}': ",
                    new Object[] { packet.getAddress().getHostAddress(), e.getMessage() });
        }
    }

}
