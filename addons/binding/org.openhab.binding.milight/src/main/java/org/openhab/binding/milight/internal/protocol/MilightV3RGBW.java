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

    // We have no real saturation control for RGBW bulbs. If the saturation is under a 50% threshold
    // we just change to white mode instead.
    @Override
    public void setHSB(int hue, int saturation, int brightness, MilightThingState state) {
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
        byte command_on[] = { (byte) 0x42, (byte) 0x45, (byte) 0x47, (byte) 0x49, (byte) 0x4B };
        byte command_off[] = { (byte) 0x41, (byte) 0x46, (byte) 0x48, (byte) 0x4A, (byte) 0x4C };
        if (on) {
            sendQueue.queue(new byte[] { command_on[zone], 0x00, 0x55 }, uidc(type_offset, CAT_POWER_SET), true);
        } else {
            sendQueue.queue(new byte[] { command_off[zone], 0x00, 0x55 }, uidc(type_offset, CAT_POWER_SET), true);
        }
    }

    @Override
    public void whiteMode(MilightThingState state) {
        byte command[] = { (byte) 0xC2, (byte) 0xC5, (byte) 0xC7, (byte) 0xC9, (byte) 0xCB };
        setPower(true, state);
        sendQueue.queue(new byte[] { command[zone], 0x00, 0x55 }, uidc(type_offset, CAT_WHITEMODE), true);
    }

    @Override
    public void nightMode(MilightThingState state) {
        logger.debug("milight: sendNightMode");
        byte first[] = { 0x41, 0x46, 0x48, 0x4A, 0x4C };
        byte second[] = { (byte) 0xC1, (byte) 0xC6, (byte) 0xC8, (byte) 0xCA, (byte) 0xCC };

        // nightMode for RGBW bulbs requires a second message
        sendQueue.queue(new byte[] { first[zone], 0x00, 0x55 }, uidc(type_offset, CAT_NIGHTMODE1), false);
        sendQueue.queue(new byte[] { second[zone], 0x00, 0x55 }, uidc(type_offset, CAT_NIGHTMODE2), false);
    }

    @Override
    public void setColorTemperature(int color_temp, MilightThingState state) {

    }

    @Override
    public void changeColorTemperature(int color_temp_relative, MilightThingState state) {

    }

    @Override
    public void setBrightness(int value, MilightThingState state) {
        if (value <= 0) {
            setPower(false, state);
            return;
        }

        setPower(true, state);

        int br = (int) Math.ceil((value * rgbwLevels) / 100.0) + 1;
        sendQueue.queue(new byte[] { 0x4E, (byte) br, 0x55 }, uidc(type_offset, CAT_BRIGHTNESS_SET), true);
        state.brightness = value;
    }

    @Override
    public void changeBrightness(int relative_brightness, MilightThingState state) {
        setBrightness(Math.max(100, Math.min(100, state.brightness + relative_brightness)), state);
    }

    @Override
    public void changeSpeed(int relative_speed, MilightThingState state) {
        if (relative_speed == 0) {
            return;
        }

        setPower(true, state);
        sendQueue.queue(new byte[] { (byte) (relative_speed > 0 ? 0x44 : 0x43), 0x00, 0x55 }, QueuedSend.NO_CATEGORY,
                false);
    }

    // This bulb actually doesn't implement a previous animation mode command. We just use the next mode command
    // instead.
    @Override
    public void previousAnimationMode(MilightThingState state) {
        setPower(true, state);
        sendQueue.queue(new byte[] { 0x4D, 0x00, 0x55 }, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.max(state.ledMode + 1, 100);
    }

    @Override
    public void nextAnimationMode(MilightThingState state) {
        setPower(true, state);
        sendQueue.queue(new byte[] { 0x4D, 0x00, 0x55 }, uidc(type_offset, CAT_MODE_SET), false);
        state.ledMode = Math.max(state.ledMode + 1, 100);
    }

}
