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

/**
 * Implements the RGB cold white / warm white bulb. It is the most feature rich bulb.
 *
 * @author David Graeff <david.graeff@web.de>
 * @since 2.1
 */
public class MilightV6RGB_CW_WW extends MilightV6 {
    private final int ADDR = 0x08;

    public MilightV6RGB_CW_WW(QueuedSend sendQueue, MilightV6SessionManager session, int zone) {
        super(10, sendQueue, session, zone);
    }

    @Override
    protected byte getAddr() {
        return ADDR;
    }

    @Override
    public void setPower(boolean on, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        if (on) {
            sendQueue.queue(make_command(4, 1), uidc(type_offset, CAT_POWER_SET), true);
        } else {
            sendQueue.queue(make_command(4, 2), uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        sendQueue.queue(make_command(5, 0x64), uidc(type_offset, CAT_WHITEMODE), true);
    }

    @Override
    public void nightMode(MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        setPower(true, state);
        sendQueue.queue(make_command(4, 5), uidc(type_offset, CAT_NIGHTMODE1), true);
    }

    @Override
    public void setColorTemperature(int color_temp, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        int ct = (color_temp * 64) / 100;
        ct = Math.min(ct, 64);
        ct = Math.max(ct, 0);
        sendQueue.queue(make_command(5, ct), uidc(type_offset, CAT_TEMPERATURE_SET), true);
        state.colorTemperature = color_temp;
    }

    @Override
    public void setBrightness(int value, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        if (value == 0) {
            setPower(false, state);
        } else if (state.brightness == 0) {
            // If if was dimmed to minimum (off), turn it on again
            setPower(true, state);
        }

        int br = (value * MAX_BR) / 100;
        br = Math.min(br, MAX_BR);
        br = Math.max(br, 0);
        sendQueue.queue(make_command(3, br), uidc(type_offset, CAT_BRIGHTNESS_SET), true);

        state.brightness = value;
    }

    @Override
    public void setSaturation(int value, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        int br = (value * MAX_SAT) / 100;
        br = Math.min(br, MAX_SAT);
        br = Math.max(br, 0);
        sendQueue.queue(make_command(2, br), uidc(type_offset, CAT_SATURATION_SET), true);
        state.saturation = value;
    }

    @Override
    public void setLedMode(int mode, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        mode = Math.min(mode, 9);
        mode = Math.max(mode, 1);
        sendQueue.queue(make_command(6, mode), uidc(type_offset, CAT_MODE_SET), true);
        state.ledMode = mode;
    }

    @Override
    public void changeSpeed(int relative_speed, MilightThingState state) {
        if (relative_speed > 1) {
            sendQueue.queue(make_command(4, 3), QueuedSend.NO_CATEGORY, false);
        } else if (relative_speed < 1) {
            sendQueue.queue(make_command(4, 4), QueuedSend.NO_CATEGORY, false);
        }
    }
}
