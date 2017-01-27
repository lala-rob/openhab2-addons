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
        logger.debug("milight: sendFull");
        byte messageBytes[] = null;
        switch (zone) {
            case 0:
                // message fullBright all white bulbs
                messageBytes = new byte[] { (byte) 0xB5, 0x00, 0x55 };
                break;
            case 1:
                // message fullBright white bulb channel 1
                messageBytes = new byte[] { (byte) 0xB8, 0x00, 0x55 };
                break;
            case 2:
                // message fullBright white bulb channel 2
                messageBytes = new byte[] { (byte) 0xBD, 0x00, 0x55 };
                break;
            case 3:
                // message fullBright white bulb channel 3
                messageBytes = new byte[] { (byte) 0xB7, 0x00, 0x55 };
                break;
            case 4:
                // message fullBright white bulb channel 4
                messageBytes = new byte[] { (byte) 0xB2, 0x00, 0x55 };
                break;
            default:
                break;
        }

        sendQueue.queue(messageBytes, uidc(type_offset, CAT_BRIGHTNESS_SET), true);
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
        if (on) {
            logger.debug("milight: sendOn");
            byte messageBytes[] = null;
            switch (zone) {
                case 0:
                    // message all white bulbs ON
                    messageBytes = new byte[] { 0x35, 0x00, 0x55 };
                    break;
                case 1:
                    // message white bulb channel 1 ON
                    messageBytes = new byte[] { 0x38, 0x00, 0x55 };
                    break;
                case 2:
                    // message white bulb channel 2 ON
                    messageBytes = new byte[] { 0x3D, 0x00, 0x55 };
                    break;
                case 3:
                    // message white bulb channel 3 ON
                    messageBytes = new byte[] { 0x37, 0x00, 0x55 };
                    break;
                case 4:
                    // message white bulb channel 4 ON
                    messageBytes = new byte[] { 0x32, 0x00, 0x55 };
                    break;
            }
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        } else {
            logger.debug("milight: sendOff");
            byte messageBytes[] = null;
            switch (zone) {
                case 0:
                    // message all white bulbs OFF
                    messageBytes = new byte[] { 0x39, 0x00, 0x55 };
                    break;
                case 1:
                    // message white bulb channel 1 OFF
                    messageBytes = new byte[] { 0x3B, 0x00, 0x55 };
                    break;
                case 2:
                    // message white bulb channel 2 OFF
                    messageBytes = new byte[] { 0x33, 0x00, 0x55 };
                    break;
                case 3:
                    // message white bulb channel 3 OFF
                    messageBytes = new byte[] { 0x3A, 0x00, 0x55 };
                    break;
                case 4:
                    // message white bulb channel 4 OFF
                    messageBytes = new byte[] { 0x36, 0x00, 0x55 };
                    break;
            }
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {

    }

    @Override
    public void nightMode(MilightThingState state) {
        logger.debug("milight: sendNightMode");
        byte messageBytes[] = null;
        switch (zone) {
            case 0:
                // message nightMode all white bulbs
                messageBytes = new byte[] { (byte) 0xB9, 0x00, 0x55 };
                break;
            case 1:
                // message nightMode white bulb channel 1
                messageBytes = new byte[] { (byte) 0xBB, 0x00, 0x55 };
                break;
            case 2:
                // message nightMode white bulb channel 2
                messageBytes = new byte[] { (byte) 0xB3, 0x00, 0x55 };
                break;
            case 3:
                // message nightMode white bulb channel 3
                messageBytes = new byte[] { (byte) 0xBA, 0x00, 0x55 };
                break;
            case 4:
                // message nightMode white bulb channel 4
                messageBytes = new byte[] { (byte) 0xB6, 0x00, 0x55 };
                break;
        }
        sendQueue.queue(messageBytes, uidc(type_offset, CAT_NIGHTMODE1), false);
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
        logger.debug("milight: CT change from '{}' with command '{}' via '{}' steps.", state.colorTemperature,
                color_temp, repeatCount);
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
    public void nextAnimationMode(MilightThingState state) {
        logger.debug("milight: sendDiscoModeUp");
        byte messageBytes[] = null;
        messageBytes = new byte[] { 0x27, 0x00, 0x55 };
        setPower(true, state);

        sendQueue.queue(messageBytes, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.max(state.ledMode + 10, 100);
    }

}
