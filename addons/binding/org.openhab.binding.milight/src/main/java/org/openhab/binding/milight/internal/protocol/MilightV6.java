/**
 * Copyright (c) IBOX_LED_ID14-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.milight.internal.protocol;

import org.openhab.binding.milight.internal.MilightThingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements basic V6 bulb functionally. But commands are different for different v6 bulbs, so almost all the work is
 * done in subclasses.
 *
 * @author David Graeff <david.graeff@web.de>
 * @since 2.1
 */
public abstract class MilightV6 extends AbstractBulbInterface {
    protected static final Logger logger = LoggerFactory.getLogger(MilightV6.class);

    protected static final int MAX_BR = 0x64; // Maximum brightness
    protected static final int MAX_SAT = 0x64; // Maximum saturation

    // Each bulb type including zone has to be unique. To realise this, each type has an offset.
    protected final int type_offset;
    protected MilightV6SessionManager session;

    public MilightV6(int type_offset, QueuedSend sendQueue, MilightV6SessionManager session, int zone) {
        super(sendQueue, zone);
        this.type_offset = type_offset;
        this.session = session;
    }

    protected abstract byte getAddr();

    /**
     * Produces data like:
     * SN: Sequence number
     * S1: SessionID1
     * S2: SessionID2
     * P1/P2: Password bytes
     * WB: Remote (08) or iBox integrated bulb (00)
     * ZN: Zone {Zone1-4 0=All}
     * CK: Checksum
     *
     * #zone 1 on
     * @ 80 00 00 00 11 84 00 00 0c 00 31 00 00 08 04 01 00 00 00 01 00 3f
     *
     * @param light1 The first light command byte
     * @param light2 The second light command byte
     * @param zone The zone. 0 means all, 1-4
     * @return
     */
    protected byte[] make_command(int light1, int light2) {
        byte[] t = { (byte) 0x80, 0x00, 0x00, 0x00, 0x11, session.getSid1(), session.getSid2(), 0x00,
                session.getNextSequenceNo(), 0x00, 0x31, session.getPw1(), session.getPw2(), getAddr(), (byte) light1,
                (byte) light2, 0x00, 0x00, 0x00, (byte) zone, 0x00, 0x00 };

        byte chksum = (byte) (t[10 + 0] + t[10 + 1] + t[10 + 2] + t[10 + 3] + t[10 + 4] + t[10 + 5] + t[10 + 6]
                + t[10 + 7] + t[10 + 8] + zone);
        t[21] = chksum;
        return t;
    }

    protected byte[] make_link(boolean link) {
        byte[] t = { (link ? (byte) 0x3D : (byte) 0x3E), 0x00, 0x00, 0x00, 0x11, session.getSid1(), session.getSid2(),
                0x00, session.getNextSequenceNo(), 0x00, 0x31, session.getPw1(), session.getPw2(), getAddr(), 0x00,
                0x00, 0x00, 0x00, 0x00, (byte) zone, 0x00, 0x00 };

        byte chksum = (byte) (t[10 + 0] + t[10 + 1] + t[10 + 2] + t[10 + 3] + t[10 + 4] + t[10 + 5] + t[10 + 6]
                + t[10 + 7] + t[10 + 8] + zone);
        t[21] = chksum;
        return t;
    }

    /**
     *
     * 80 00 00 00 11 S1 S2 00 SN 00 31 P1 P2 WB 01 CC CC CC CC ZN 00 CK
     *
     * 80 00 00 00 11 D4 00 00 12 00 31 00 00 08 01 FF FF FF FF 01 00 38
     *
     *
     * @param remote Is a dedicated bulb or the iBox integrated one
     * @param color The hue value
     * @param zone The zone. 0 means all, 1-4
     * @return
     */
    protected byte[] make_color(int addr, byte color) {
        byte[] t = { (byte) 0x80, 0x00, 0x00, 0x00, 0x11, session.getSid1(), session.getSid2(), 0x00,
                session.getNextSequenceNo(), 0x00, 0x31, session.getPw1(), session.getPw2(), (byte) addr, 0x01, color,
                color, color, color, (byte) zone, 0x00, 0x00 };

        byte chksum = (byte) ((t[10 + 0] + t[10 + 1] + t[10 + 2] + t[10 + 3] + t[10 + 4] + t[10 + 5] + t[10 + 6]
                + t[10 + 7] + t[10 + 8] + zone) % 0xff);

        t[21] = chksum;
        return t;
    }

    @Override
    public void setHSB(int hue, int saturation, int brightness, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        // 0xFF = Red, D9 = Lavender, BA = Blue, 85 = Aqua, 7A = Green, 54 = Lime, 3B = Yellow, 1E = Orange
        // we have to map [0,360] to [0,0xFF], where red equals hue=0 and the milight color 0xFF (=255)
        // Integer milightColorNo = (256 + 0xFF - (int) (hue / 360.0 * 255.0)) % 256;
        Integer milightColorNo = (256 + 176 - (int) (hue / 360.0 * 255.0)) % 256;

        sendQueue.queue(make_color(getAddr(), (byte) milightColorNo.intValue()), uidc(type_offset, CAT_COLOR_SET),
                true);
        state.hue = (float) (hue / 360.0);

        if (brightness != -1) {
            setBrightness(brightness, state);
        }

        if (saturation != -1) {
            setSaturation(saturation, state);
        }
    }

    @Override
    public void changeColorTemperature(int color_temp_relative, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        if (color_temp_relative == 0) {
            return;
        }

        int ct = (state.colorTemperature * 64) / 100 + color_temp_relative;
        ct = Math.min(ct, 64);
        ct = Math.max(ct, 0);
        setColorTemperature(ct * 100 / 64, state);
    }

    @Override
    public void changeBrightness(int relative_brightness, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        if (relative_brightness == 0) {
            return;
        }

        int br = (state.brightness * MAX_BR) / 100 + relative_brightness;
        br = Math.min(br, MAX_BR);
        br = Math.max(br, 0);

        setBrightness(br * 100 / MAX_BR, state);
    }

    @Override
    public void previousAnimationMode(MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        int mode = state.ledMode - 1;
        mode = Math.min(mode, 9);
        mode = Math.max(mode, 1);

        setLedMode(mode, state);
    }

    @Override
    public void nextAnimationMode(MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        int mode = state.ledMode + 1;
        mode = Math.min(mode, 9);
        mode = Math.max(mode, 1);

        setLedMode(mode, state);
    }

    @Override
    public void setAnimationSpeed(int speed, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        if (speed > state.animationSpeed) {
            int repeatCount = (speed - state.animationSpeed) / 10;
            for (int i = 0; i < repeatCount; i++) {
                changeSpeed(+1, state);
            }
        } else if (speed < state.animationSpeed) {
            int repeatCount = (state.animationSpeed - speed) / 10;
            for (int i = 0; i < repeatCount; i++) {
                changeSpeed(-1, state);
            }
        }

        state.animationSpeed = speed;
    }

    public void link(int zone) {
        sendQueue.queue(make_link(true), uidc(type_offset, CAT_LINK), true);
    }

    public void unlink(int zone) {
        sendQueue.queue(make_link(false), uidc(type_offset, CAT_LINK), true);
    }
}