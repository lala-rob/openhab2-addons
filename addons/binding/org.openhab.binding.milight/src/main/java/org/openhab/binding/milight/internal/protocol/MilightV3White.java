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

public class MilightV3White extends MilightV3 {
    protected static final int wLevels = 11;

    public MilightV3White(QueuedSend sendQueue, int zone) {
        super(0, sendQueue, zone);
    }

    protected void setFull(int zone, MilightThingState state) {
        byte command[] = { (byte) 0xB5, (byte) 0xB8, (byte) 0xBD, (byte) 0xB7, (byte) 0xB2 };
        sendQueue.queue(new byte[] { command[zone], 0x00, 0x55 }, uidc(type_offset, CAT_BRIGHTNESS_SET), true);
        state.brightness = 100;
    }

    @Override
    public void setHSB(int hue, int saturation, int brightness, MilightThingState state) {
        // This bulb type only supports a brightness value
        if (brightness != -1) {
            setBrightness(brightness, state);
        }
    }

    @Override
    public void setPower(boolean on, MilightThingState state) {
        byte command_on[] = { (byte) 0x35, (byte) 0x38, (byte) 0x3D, (byte) 0x37, (byte) 0x32 };
        byte command_off[] = { (byte) 0x39, (byte) 0x3B, (byte) 0x33, (byte) 0x3A, (byte) 0x36 };
        if (on) {
            sendQueue.queue(new byte[] { command_on[zone], 0x00, 0x55 }, uidc(type_offset, CAT_POWER_SET), true);
        } else {
            sendQueue.queue(new byte[] { command_off[zone], 0x00, 0x55 }, uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {

    }

    @Override
    public void nightMode(MilightThingState state) {
        byte command[] = { (byte) 0xB9, (byte) 0xBB, (byte) 0xB3, (byte) 0xBA, (byte) 0xB6 };
        sendQueue.queue(new byte[] { command[zone], 0x00, 0x55 }, uidc(type_offset, CAT_NIGHTMODE1), true);
    }

    @Override
    public void setColorTemperature(int color_temp, MilightThingState state) {
        // White Bulbs: 11 levels of temperature + Off.
        int newLevel;
        int oldLevel;
        // Reset bulb to known state
        if (color_temp <= 0) {
            color_temp = 0;
            newLevel = 1;
            oldLevel = wLevels;
        } else if (color_temp >= 100) {
            color_temp = 100;
            newLevel = wLevels;
            oldLevel = 1;
        } else {
            newLevel = (int) Math.ceil((color_temp * wLevels) / 100.0);
            oldLevel = (int) Math.ceil((state.colorTemperature * wLevels) / 100.0);
        }

        final int repeatCount = Math.abs(newLevel - oldLevel);
        if (newLevel > oldLevel) {
            for (int i = 0; i < repeatCount; i++) {
                changeColorTemperature(1, state);
            }
        } else if (newLevel < oldLevel) {
            for (int i = 0; i < repeatCount; i++) {
                changeColorTemperature(-1, state);
            }
        }

        state.colorTemperature = color_temp;
    }

    @Override
    public void changeColorTemperature(int color_temp_relative, MilightThingState state) {
        if (color_temp_relative > 0) {
            logger.debug("milight: sendWarmer");
            int newPercent = state.colorTemperature + 10;
            if (newPercent > 100) {
                newPercent = 100;
            }
            byte messageBytes[] = { 0x3E, 0x00, 0x55 };
            setPower(true, state);

            sendQueue.queue(messageBytes, QueuedSend.NO_CATEGORY, false);
            state.colorTemperature = newPercent;
        } else if (color_temp_relative < 0) {
            logger.debug("milight: sendCooler");
            int newPercent = state.colorTemperature - 10;

            byte messageBytes[] = { 0x3F, 0x00, 0x55 };
            setPower(true, state);

            sendQueue.queue(messageBytes, QueuedSend.NO_CATEGORY, false);
            state.colorTemperature = newPercent;
        }
    }

    // This just emulates an absolute brightness command with the relative commands.
    @Override
    public void setBrightness(int value, MilightThingState state) {
        if (value <= 0) {
            setPower(false, state);
            return;
        } else if (value >= 100) {
            setFull(zone, state);
            return;
        }

        // White Bulbs: 11 levels of brightness + Off.
        final int newLevel = (int) Math.ceil((value * wLevels) / 100.0);

        // When turning on start from full brightness
        int oldLevel;
        if (state.brightness == 0) {
            setFull(zone, state);
            oldLevel = wLevels;
        } else {
            oldLevel = (int) Math.ceil((state.brightness * wLevels) / 100.0);
        }

        final int repeatCount = Math.abs(newLevel - oldLevel);
        logger.debug("milight: dim from '{}' with command '{}' via '{}' steps.", String.valueOf(state.brightness),
                String.valueOf(value), repeatCount);
        if (newLevel > oldLevel) {
            for (int i = 0; i < repeatCount; i++) {
                changeBrightness(+1, state);
            }
        } else if (newLevel < oldLevel) {
            for (int i = 0; i < repeatCount; i++) {
                changeBrightness(-1, state);
            }
        }

        state.brightness = value;
    }

    @Override
    public void changeBrightness(int relative_brightness, MilightThingState state) {
        if (relative_brightness < 0) {
            logger.debug("milight: sendDecrease");
            byte messageBytes[] = null;

            // decrease brightness of white bulbs
            messageBytes = new byte[] { 0x34, 0x00, 0x55 };

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
            logger.debug("milight: sendIncrease");
            byte messageBytes[] = null;

            // increase brightness of white bulbs
            messageBytes = new byte[] { 0x3C, 0x00, 0x55 };

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
    public void previousAnimationMode(MilightThingState state) {
        setPower(true, state);
        sendQueue.queue(new byte[] { 0x28, 0x00, 0x55 }, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.min(state.ledMode - 1, 0);
    }

    @Override
    public void nextAnimationMode(MilightThingState state) {
        setPower(true, state);
        sendQueue.queue(new byte[] { 0x27, 0x00, 0x55 }, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.max(state.ledMode + 1, 100);
    }

}
