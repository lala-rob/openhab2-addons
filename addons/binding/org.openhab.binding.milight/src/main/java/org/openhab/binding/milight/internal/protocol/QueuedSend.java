/**
 * Copyright (c) IBOX_LED_ID14-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.milight.internal.protocol;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implements a queue for UDP sending, where each item to be send is associated with an id.
 * If a new item is added, that has the same id of an already queued item, it replaces the
 * queued item. This is used for milight packets, where older bridges accept commands with a 100ms
 * delay only. The user may issue brightness or color changes faster than 1/10s though, and we don't
 * want to just queue up those commands but apply the newest command only.
 *
 * @author David Graeff <david.graeff@web.de>
 * @since 2.1
 *
 */
public class QueuedSend implements Runnable {
    private static class QueueItem {
        byte[] data;
        int unique_command_id;
        boolean repeatable;

        public QueueItem(byte[] data, int unique_command_id, boolean repeatable) {
            super();
            this.data = data;
            this.unique_command_id = unique_command_id;
            this.repeatable = repeatable;
        }
    }

    BlockingQueue<QueueItem> queue = new LinkedBlockingQueue<>(20);
    protected final DatagramPacket packet;
    protected final DatagramSocket datagramSocket;
    private static final Logger logger = LoggerFactory.getLogger(QueuedSend.class);
    private int delay_between_commands = 100;
    private int repeat_commands = 1;
    private boolean willbeclosed = false;
    private Thread thread;

    public static final byte NO_CATEGORY = 0;

    public QueuedSend(InetAddress addr, int port) throws SocketException {
        byte[] a = new byte[0];
        packet = new DatagramPacket(a, a.length, addr, port);
        datagramSocket = new DatagramSocket();
        thread = new Thread(this);
        thread.start();
    }

    public int getDelayBetweenCommands() {
        return delay_between_commands;
    }

    public int getRepeatCommands() {
        return repeat_commands;
    }

    public void setRepeatCommands(int repeat_commands) {
        this.repeat_commands = repeat_commands;
    }

    public void setDelayBetweenCommands(int ms) {
        delay_between_commands = ms;
    }

    /**
     * The queue process
     */
    @Override
    public void run() {
        while (!willbeclosed) {
            QueueItem item;
            try {
                // block/wait for another item
                item = queue.take();
            } catch (InterruptedException e) {
                if (!willbeclosed) {
                    logger.error("Queue take failed: " + e.getLocalizedMessage());
                }
                break;
            }

            if (item == null) {
                continue;
            }

            packet.setData(item.data);
            try {
                int repeat_remaining = item.repeatable ? repeat_commands : 1;
                repeat_remaining = Math.min(repeat_remaining, 1);

                StringBuffer s = new StringBuffer();
                for (int i = 0; i < item.data.length; ++i) {
                    s.append(String.format("%02X ", item.data[i]));
                }

                while (repeat_remaining > 0) {
                    datagramSocket.send(packet);
                    --repeat_remaining;
                    logger.debug("Sent packet '{}' to bridge {}", s.toString(), packet.getAddress().getHostAddress());
                }

            } catch (Exception e) {
                logger.error("Failed to send Message to '{}': {}", packet.getAddress().getHostAddress(),
                        e.getMessage());
            }

            try {
                Thread.sleep(delay_between_commands);
            } catch (InterruptedException e) {
                if (!willbeclosed) {
                    logger.error("Queue sleep failed: " + e.getLocalizedMessage());
                }
                break;
            }
        }

    }

    /**
     * Once disposed, this object can't be reused anymore.
     */
    public void dispose() {
        willbeclosed = true;
        if (thread != null) {
            try {
                thread.join(delay_between_commands);
            } catch (InterruptedException e) {
            }
            thread.interrupt();
        }
        thread = null;
    }

    public void setRepeatTimes(int times) {
        repeat_commands = times;
    }

    /**
     * Add data to the send queue. Use a category of 0 to make an item non-categorised.
     * Commands which need to be queued up and not replacing itself must be non-categorised
     * (like relative in contrast to absolute commands)
     *
     * @param data Data to be send
     * @param unique_command_id A unique command id. Commands with the same id will overwrite themself.
     * @param repeatable Is the command auto-repeatable. Use false for relative in contrast to absolute commands.
     */
    public void queue(byte[] data, int unique_command_id, boolean repeatable) {
        synchronized (queue) {
            if (unique_command_id > 0) {
                while (queue.iterator().hasNext()) {
                    try {
                        if (queue.iterator().next().unique_command_id == unique_command_id) {
                            queue.iterator().remove();
                        }
                    } catch (NoSuchElementException e) {
                        // The element might have been processed already while iterate.
                        // Ignore NoSuchElementException here.
                    }
                }
            }
            queue.offer(new QueueItem(data, unique_command_id, repeatable));
        }
    }

    public InetAddress getAddr() {
        return packet.getAddress();
    }

    public int getPort() {
        return packet.getPort();
    }

    public DatagramSocket getSocket() {
        return datagramSocket;
    }
}
