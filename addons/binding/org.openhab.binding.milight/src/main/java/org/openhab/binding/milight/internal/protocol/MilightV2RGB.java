/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
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

public class MilightV2RGB extends AbstractBulbInterface {
    protected static final Logger logger = LoggerFactory.getLogger(MilightV2RGB.class);
    protected final int type_offset = 5;

    public MilightV2RGB(QueuedSend sendQueue, int zone) {
        super(sendQueue, zone);
    }

    @Override
    public void setHSB(int hue, int saturation, int brightness, MilightThingState state) {
        // We have no real saturation control for RGBW bulbs. If the saturation is under a 50% threshold
        // we just change to white mode instead.
        if (saturation < 50) {
            whiteMode(state);
        } else {
            // we have to map [0,360] to [0,0xFF], where red equals hue=0 and the milight color 0xB0 (=176)
            Integer milightColorNo = (256 + 176 - (int) (hue / 360.0 * 255.0)) % 256;
            byte messageBytes[] = new byte[] { 0x20, (byte) milightColorNo.intValue(), 0x55 };
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_COLOR_SET), true);
            state.hue = (float) (hue / 360.0 * 255.0);

            if (brightness != -1) {
                setBrightness(brightness, state);
            }
        }
    }

    @Override
    public void setPower(boolean on, MilightThingState state) {
        if (on) {
            logger.debug("milight: sendOn");
            byte messageBytes[] = null;
            // message rgb bulbs ON
            messageBytes = new byte[] { 0x22, 0x00, 0x55 };
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        } else {
            logger.debug("milight: sendOff");
            byte messageBytes[] = null;

            // message rgb bulbs OFF
            messageBytes = new byte[] { 0x21, 0x00, 0x55 };

            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {

    }

    @Override
    public void nightMode(MilightThingState state) {

    }

    @Override
    public void changeColorTemperature(int color_temp_relative, MilightThingState state) {

    }

    @Override
    public void setLedMode(int mode, MilightThingState state) {

    }

    @Override
    public void previousAnimationMode(MilightThingState state) {

    }

    @Override
    public void setAnimationSpeed(int speed, MilightThingState state) {

    }

    @Override
    public void setSaturation(int value, MilightThingState state) {

    }

    @Override
    public void setColorTemperature(int color_temp, MilightThingState state) {

    }

    @Override
    public void setBrightness(int value, MilightThingState state) {
        if (value <= 0) {
            setPower(false, state);
            return;
        }

        if (value > state.brightness) {
            int repeatCount = (value - state.brightness) / 10;
            for (int i = 0; i < repeatCount; i++) {

                changeBrightness(+1, state);

            }
        } else if (value < state.brightness) {
            int repeatCount = (state.brightness - value) / 10;
            for (int i = 0; i < repeatCount; i++) {

                changeBrightness(-1, state);
            }
        }

        state.brightness = value;
    }

    @Override
    public void changeBrightness(int relative_brightness, MilightThingState state) {
        if (relative_brightness < 0) {
            logger.debug("milight: decrease brightness");
            byte messageBytes[] = null;

            // Decrease brightness of rgb bulbs
            messageBytes = new byte[] { 0x24, 0x00, 0x55 };
            int newPercent = state.brightness - 10;
            if (newPercent < 0) {
                newPercent = 0;
            }

            if (state.brightness != -1 && newPercent == 0) {
                setPower(false, state);
            } else {
                setPower(true, state);
                sendQueue.queue(messageBytes, QueuedSend.NO_CATEGORY, false);
            }
            state.brightness = newPercent;
        } else if (relative_brightness > 1) {
            logger.debug("milight: increase brightness");
            byte messageBytes[] = null;
            // increase brightness of rgb bulbs
            messageBytes = new byte[] { 0x23, 0x00, 0x55 };
            int currentPercent = state.brightness;
            int newPercent = currentPercent + 10;
            if (newPercent > 100) {
                newPercent = 100;
            }

            setPower(true, state);

            sendQueue.queue(messageBytes, QueuedSend.NO_CATEGORY, false);
            state.brightness = newPercent;
        }
    }

    @Override
    public void changeSpeed(int relative_speed, MilightThingState state) {

    }

    @Override
    public void nextAnimationMode(MilightThingState state) {

    }

}
