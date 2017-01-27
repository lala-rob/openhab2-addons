/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.milight.test;

import java.net.URL;
import java.util.Hashtable;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.openhab.binding.milight.MilightBindingConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Emulates a milight iBox bridge, which will be discovered by MilightBridgeDiscovery.
 *
 */
public class TestDiscovery extends AbstractDiscoveryService {
    private ServiceRegistration<?> reg = null;
    private EmulatedV6Bridge server;
    private URL url;

    public TestDiscovery() {
        super(MilightBindingConstants.BRIDGE_THING_TYPES_UIDS, 2, true);
        try {
            server = new EmulatedV6Bridge();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (reg != null) {
            reg.unregister();
        }
        reg = null;
    }

    @Override
    protected void startScan() {
    }

    public void start(BundleContext bundleContext) {
        if (reg != null) {
            return;
        }
        reg = bundleContext.registerService(DiscoveryService.class.getName(), this, new Hashtable<String, Object>());
    }
}