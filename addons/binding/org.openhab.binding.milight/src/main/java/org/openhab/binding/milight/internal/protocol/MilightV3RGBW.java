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

public class MilightV3RGBW extends MilightV3 {
    protected static final int rgbwLevels = 26;

    public MilightV3RGBW(QueuedSend sendQueue, int zone) {
        super(10, sendQueue, zone);
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
            setPower(true, state);

            byte messageBytes[] = new byte[] { 0x40, (byte) milightColorNo.intValue(), 0x55 };
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
            switch (zone) {
                case 0:
                    // message all rgb-w bulbs ON
                    messageBytes = new byte[] { 0x42, 0x00, 0x55 };
                    break;
                case 1:
                    // message rgb-w bulbs channel1 ON
                    messageBytes = new byte[] { 0x45, 0x00, 0x55 };
                    break;
                case 2:
                    // message rgb-w bulbs channel2 ON
                    messageBytes = new byte[] { 0x47, 0x00, 0x55 };
                    break;
                case 3:
                    // message rgb-w bulbs channel3 ON
                    messageBytes = new byte[] { 0x49, 0x00, 0x55 };
                    break;
                case 4:
                    // message rgb-w bulbs channel4 ON
                    messageBytes = new byte[] { 0x4B, 0x00, 0x55 };
                    break;
            }
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        } else {
            logger.debug("milight: sendOff");
            byte messageBytes[] = null;
            switch (zone) {
                case 0:
                    // message all rgb-w bulbs OFF
                    messageBytes = new byte[] { 0x41, 0x00, 0x55 };
                    break;
                case 1:
                    // message rgb-w bulbs channel1 OFF
                    messageBytes = new byte[] { 0x46, 0x00, 0x55 };
                    break;
                case 2:
                    // message rgb-w bulbs channel2 OFF
                    messageBytes = new byte[] { 0x48, 0x00, 0x55 };
                    break;
                case 3:
                    // message rgb-w bulbs channel3 OFF
                    messageBytes = new byte[] { 0x4A, 0x00, 0x55 };
                    break;
                case 4:
                    // message rgb-w bulbs channel4 OFF
                    messageBytes = new byte[] { 0x4C, 0x00, 0x55 };
                    break;
            }
            sendQueue.queue(messageBytes, uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {
        logger.debug("milight: sendWhiteMode");
        byte messageBytes[] = null;
        switch (zone) {
            case 0:
                // message whiteMode all RGBW bulbs
                messageBytes = new byte[] { (byte) 0xC2, 0x00, 0x55 };
                break;
            case 1:
                // message whiteMode RGBW bulb channel 1
                messageBytes = new byte[] { (byte) 0xC5, 0x00, 0x55 };
                break;
            case 2:
                // message whiteMode RGBW bulb channel 2
                messageBytes = new byte[] { (byte) 0xC7, 0x00, 0x55 };
                break;
            case 3:
                // message whiteMode RGBW bulb channel 3
                messageBytes = new byte[] { (byte) 0xC9, 0x00, 0x55 };
                break;
            case 4:
                // message whiteMode RGBW bulb channel 4
                messageBytes = new byte[] { (byte) 0xCB, 0x00, 0x55 };
                break;
        }
        setPower(true, state);

        sendQueue.queue(messageBytes, uidc(type_offset, CAT_WHITEMODE), true);
    }

    @Override
    public void nightMode(MilightThingState state) {
        logger.debug("milight: sendNightMode");
        byte messageBytes[] = null;
        byte messageBytes2[] = null;
        switch (zone) {
            case 0:
                // message nightMode all RGBW bulbs
                messageBytes = new byte[] { 0x41, 0x00, 0x55 };
                messageBytes2 = new byte[] { (byte) 0xC1, 0x00, 0x55 };
                break;
            case 1:
                // message nightMode RGBW bulb channel 1
                messageBytes = new byte[] { 0x46, 0x00, 0x55 };
                messageBytes2 = new byte[] { (byte) 0xC6, 0x00, 0x55 };
                break;
            case 2:
                // message nightMode RGBW bulb channel 2
                messageBytes = new byte[] { 0x48, 0x00, 0x55 };
                messageBytes2 = new byte[] { (byte) 0xC8, 0x00, 0x55 };
                break;
            case 3:
                // message nightMode RGBW bulb channel 3
                messageBytes = new byte[] { 0x4A, 0x00, 0x55 };
                messageBytes2 = new byte[] { (byte) 0xCA, 0x00, 0x55 };
                break;
            case 4:
                // message nightMode RGBW bulb channel 4
                messageBytes = new byte[] { 0x4C, 0x00, 0x55 };
                messageBytes2 = new byte[] { (byte) 0xCC, 0x00, 0x55 };
                break;
        }
        // nightMode for RGBW bulbs requires second message 100ms later.
        sendQueue.queue(messageBytes, uidc(type_offset, CAT_NIGHTMODE1), false);
        sendQueue.queue(messageBytes2, uidc(type_offset, CAT_NIGHTMODE2), false);
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

        int newCommand = (int) Math.ceil((value * rgbwLevels) / 100.0) + 1;
        setPower(true, state);

        byte messageBytes[] = new byte[] { 0x4E, (byte) newCommand, 0x55 };
        logger.debug("milight: send dimming packet '{}' to RGBW bulb channel '{}'", messageBytes, zone);
        sendQueue.queue(messageBytes, uidc(type_offset, CAT_BRIGHTNESS_SET), true);

        state.brightness = value;
    }

    @Override
    public void changeBrightness(int relative_brightness, MilightThingState state) {
        if (relative_brightness < 0) {
            logger.debug("milight: sendDecrease");
            byte messageBytes[] = null;

            int newPercent = state.brightness - 10;
            if (newPercent < 0) {
                newPercent = 0;
            }

            if (state.brightness != -1 && newPercent == 0) {
                setPower(false, state);
            } else {
                int decreasePercent = (int) Math.ceil((newPercent * rgbwLevels) / 100.0) + 1;
                messageBytes = new byte[] { 0x4E, (byte) decreasePercent, 0x55 };
                logger.debug("Bulb '{}' set to '{}' dimming Steps", zone, decreasePercent);
                setPower(true, state);

                sendQueue.queue(messageBytes, uidc(type_offset, CAT_BRIGHTNESS_SET), true);
            }
            state.brightness = newPercent;
        } else if (relative_brightness > 1) {
            logger.debug("milight: sendIncrease");
            byte messageBytes[] = null;

            int currentPercent = state.brightness;
            int newPercent = currentPercent + 10;
            if (newPercent > 100) {
                newPercent = 100;
            }

            int increasePercent = (int) Math.ceil((newPercent * rgbwLevels) / 100.0) + 1;
            messageBytes = new byte[] { 0x4E, (byte) increasePercent, 0x55 };
            logger.debug("Bulb '{}' set to '{}' dimming Steps", zone, increasePercent);

            setPower(true, state);

            sendQueue.queue(messageBytes, uidc(type_offset, CAT_BRIGHTNESS_SET), true);
            state.brightness = newPercent;
        }
    }

    @Override
    public void changeSpeed(int relative_speed, MilightThingState state) {
        if (relative_speed > 0) {
            logger.debug("milight: sendIncreaseSpeed");
            byte messageBytes[] = null;

            // message increaseSpeed rgb-w bulbs
            messageBytes = new byte[] { 0x44, 0x00, 0x55 };

            setPower(true, state);

            sendQueue.queue(messageBytes, uidc(type_offset, CAT_SPEED_CHANGE), false);
        } else if (relative_speed < 0) {
            logger.debug("milight: sendDecreaseSpeed");
            byte[] messageBytes = null;

            // message decreaseSpeed rgb-w bulbs
            messageBytes = new byte[] { 0x43, 0x00, 0x55 };

            setPower(true, state);

            sendQueue.queue(messageBytes, uidc(type_offset, CAT_SPEED_CHANGE), false);
        }
    }

    @Override
    public void nextAnimationMode(MilightThingState state) {
        logger.debug("milight: sendDiscoModeUp");
        byte messageBytes[] = null;
        messageBytes = new byte[] { 0x4D, 0x00, 0x55 };
        setPower(true, state);

        sendQueue.queue(messageBytes, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.max(state.ledMode + 10, 100);
    }

}
