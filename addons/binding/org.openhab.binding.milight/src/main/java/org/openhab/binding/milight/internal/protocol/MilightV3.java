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

/**
 * This milight protocol implementation class is able to do the following tasks with Milight compatible systems:
 * <ul>
 * <li>Switching bulbs on and off.</li>
 * <li>Change color temperature of a bulb, what results in a white color.</li>
 * <li>Change the brightness of a bulb without changing the color.</li>
 * <li>Change the RGB values of a bulb.</li>
 * </ul>
 * The class is state-less, use {@link MilightThingState} instead.
 *
 * @author David Gräff
 * @author Hans-Joerg Merk
 * @author Kai Kreuzer
 * @since 2.0
 */
public abstract class MilightV3 extends AbstractBulbInterface {
    protected static final Logger logger = LoggerFactory.getLogger(MilightV3.class);
    // Each bulb type including zone has to be unique. To realise this, each type has an offset.
    protected final int type_offset;

    public MilightV3(int type_offset, QueuedSend sendQueue, int zone) {
        super(sendQueue, zone);
        this.type_offset = type_offset;
    }

    @Override
    public void setLedMode(int mode, MilightThingState state) {
        // Not supported
    }

    // Emulate an absolute animation speed command. This will not be accurate.
    @Override
    public void setAnimationSpeed(int speed, MilightThingState state) {
        if (speed > state.animationSpeed) {
            int repeatCount = (speed - state.animationSpeed) / 10;
            for (int i = 0; i < repeatCount; i++) {

                changeSpeed(1, state);
            }
        } else if (speed < state.animationSpeed) {
            int repeatCount = (state.animationSpeed - speed) / 10;
            for (int i = 0; i < repeatCount; i++) {

                changeSpeed(-1, state);
            }
        }

        state.animationSpeed = speed;
    }

    @Override
    public void setSaturation(int value, MilightThingState state) {
        // Not supported
    }
}