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
 * Implements the iBox led bulb. The bulb is integrated into the iBox and does not include a white channel, so no
 * saturation or colour temperature available.
 *
 * @author David Graeff <david.graeff@web.de>
 * @since 2.1
 */
public class MilightV6RGB_IBOX extends MilightV6 {
    private final byte ADDR = 0x00;

    public MilightV6RGB_IBOX(QueuedSend sendQueue, MilightV6SessionManager session) {
        super(0, sendQueue, session, 1);
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
            sendQueue.queue(make_command(3, 3), uidc(type_offset, CAT_POWER_SET), true);
        } else {
            sendQueue.queue(make_command(3, 4), uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        sendQueue.queue(make_command(3, 5), uidc(type_offset, CAT_WHITEMODE), true);
    }

    @Override
    public void nightMode(MilightThingState state) {
        logger.info("Night mode not supported by iBox led!");
    }

    @Override
    public void setColorTemperature(int color_temp, MilightThingState state) {
        logger.info("Color temperature not supported by iBox led!");
    }

    @Override
    public void changeColorTemperature(int color_temp_relative, MilightThingState state) {
        logger.info("Color temperature not supported by iBox led!");
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
        sendQueue.queue(make_command(2, br), uidc(type_offset, CAT_BRIGHTNESS_SET), true);

        state.brightness = value;
    }

    @Override
    public void setSaturation(int value, MilightThingState state) {
        logger.info("Saturation not supported by iBox led!");
    }

    @Override
    public void setLedMode(int mode, MilightThingState state) {
        if (!session.isValid()) {
            logger.error("Bridge communication session not valid yet!");
            return;
        }

        mode = Math.min(mode, 9);
        mode = Math.max(mode, 1);
        sendQueue.queue(make_command(4, mode), uidc(type_offset, CAT_MODE_SET), true);
        state.ledMode = mode;
    }

    @Override
    public void changeSpeed(int relative_speed, MilightThingState state) {
        if (relative_speed > 1) {
            sendQueue.queue(make_command(3, 2), QueuedSend.NO_CATEGORY, false);
        } else if (relative_speed < 1) {
            sendQueue.queue(make_command(3, 1), QueuedSend.NO_CATEGORY, false);
        }
    }

    @Override
    public void link(int zone) {
    }

    @Override
    public void unlink(int zone) {
    }

}
