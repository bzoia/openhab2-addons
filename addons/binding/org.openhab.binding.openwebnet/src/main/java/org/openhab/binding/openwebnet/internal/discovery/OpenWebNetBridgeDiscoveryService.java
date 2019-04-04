/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.openwebnet.internal.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
//import org.openhab.binding.openwebnet.handler.OpenWebNetBridgeHandler;
import org.openwebnet.OpenError;
import org.openwebnet.OpenGatewayZigBee;
import org.openwebnet.OpenListener;
import org.openwebnet.OpenWebNet;
import org.openwebnet.message.GatewayManagement;
import org.openwebnet.message.OpenMessage;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetBridgeDiscoveryService} is a {@link DiscoveryService} implementation responsible for discovering
 * OpenWebNet (Zigbee) gateways in the network.
 *
 * @author Massimo Valla - Initial contribution
 */

@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.openwebent")
public class OpenWebNetBridgeDiscoveryService extends AbstractDiscoveryService implements OpenListener {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetBridgeDiscoveryService.class);

    private final static int DISCOVERY_TIMEOUT = 30; // seconds

    // TODO support multiple dongles at the same time
    private OpenGatewayZigBee zbgateway;
    private int dongleZigBeeId = 0;
    private ThingUID dongleUID = null;

    public OpenWebNetBridgeDiscoveryService() {
        super(OpenWebNetBindingConstants.BRIDGE_SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT, false);
        logger.debug("#############################################################################################");
        logger.debug("==OWN:BridgeDiscovery== constructor()");
        logger.debug("#############################################################################################");
    }

    public OpenWebNetBridgeDiscoveryService(int timeout) throws IllegalArgumentException {
        super(timeout);
        logger.debug("==OWN:BridgeDiscovery== constructor(timeout)");
    }

    @Override
    protected void startScan() {
        logger.info("==OWN:BridgeDiscovery== ------ startScan() - SEARCHING for bridges...");
        startZigBeeScan();
    }

    /**
     * OWN ZigBee gw discovery
     */
    private void startZigBeeScan() {
        if (zbgateway == null) {
            logger.debug("==OWN:BridgeDiscovery:Dongle== Gateway NULL, creating a new one ...");
            zbgateway = OpenWebNet.gatewayZigBeeAsSingleton();
            zbgateway.subscribe(this);
        }
        if (!zbgateway.isConnected()) {
            logger.debug("==OWN:BridgeDiscovery:Dongle== ... trying to connect dongle ...");
            zbgateway.connect();
        } else { // dongle is already connected
            logger.debug("==OWN:BridgeDiscovery:Dongle== ... dongle is already connected ...");
            if (dongleZigBeeId != 0) {
                // a dongle was already discovered, notify new dongle thing to inbox
                logger.debug("==OWN:BridgeDiscovery:Dongle== ... dongle ZigBeeId is: {}", dongleZigBeeId);
                notifyNewDongleThing(dongleZigBeeId);
            } else {
                logger.debug("==OWN:BridgeDiscovery:Dongle== ... requesting again MACAddress ...");
                zbgateway.send(GatewayManagement.requestMACAddress());
            }
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        logger.debug("==OWN:BridgeDiscovery== getSupportedThingTypes()");
        return OpenWebNetBindingConstants.BRIDGE_SUPPORTED_THING_TYPES;
    }

    /**
     * Create and notify to Inbox a new USB dongle thing has been discovered
     *
     * @param dongleZigBeeId the discovered dongle ZigBeeId
     */
    private void notifyNewDongleThing(int dongleZigBeeId) {
        dongleUID = new ThingUID(OpenWebNetBindingConstants.THING_TYPE_DONGLE, Integer.toString(dongleZigBeeId));
        Map<String, Object> dongleProperties = new HashMap<>(2);
        dongleProperties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_SERIAL_PORT, zbgateway.getConnectedPort());
        dongleProperties.put(OpenWebNetBindingConstants.PROPERTY_FIRMWARE_VERSION, zbgateway.getFirmwareVersion());
        dongleProperties.put(OpenWebNetBindingConstants.PROPERTY_ZIGBEEID, dongleZigBeeId);

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(dongleUID).withProperties(dongleProperties)
                .withLabel(OpenWebNetBindingConstants.THING_LABEL_DONGLE + " (ID=" + dongleZigBeeId + ", "
                        + zbgateway.getConnectedPort() + ", v=" + zbgateway.getFirmwareVersion() + ")")
                .withRepresentationProperty(OpenWebNetBindingConstants.PROPERTY_ZIGBEEID).build();
        logger.info("==OWN:BridgeDiscovery== --- DONGLE thing discovered: {}", discoveryResult.getLabel());
        thingDiscovered(discoveryResult);
    }

    @Override
    public void onConnected() {
        logger.info("==OWN:BridgeDiscovery:Dongle== onConnected() FOUND DONGLE: CONNECTED port={}",
                zbgateway.getConnectedPort());
        dongleZigBeeId = 0; // reset dongleZigBeeId
        zbgateway.send(GatewayManagement.requestFirmwareVersion());
        zbgateway.send(GatewayManagement.requestMACAddress());
    }

    @Override
    public void onConnectionError(OpenError error, String errMsg) {
        if (error == OpenError.NO_SERIAL_PORTS_ERROR) {
            logger.info("==OWN:BridgeDiscovery== No serial ports found");
        } else {
            logger.warn("==OWN:BridgeDiscovery== onConnectionError() - CONNECTION ERROR: {} - {}", error, errMsg);
        }
        stopScan();
        // TODO handle other dongle connection problems
    }

    @Override
    public void onConnectionClosed() {
        logger.debug("==OWN:BridgeDiscovery== recevied onConnectionClosed()");
        stopScan();
    }

    @Override
    public void onDisconnected() {
        logger.warn("==OWN:BridgeDiscovery== received onDisconnected()");
        stopScan();
    }

    @Override
    public void onReconnected() {
        logger.warn("==OWN:BridgeDiscovery== received onReconnected()");
    }

    @Override
    public void onMessage(OpenMessage msg) {
        // TODO change this to listen to response to MACddress request session with timeout
        // and not to all messages that arrive here
        if (dongleZigBeeId == 0) { // we do not know the discovered ZigBeeID yet, check if it was discovered with this
            // message
            int zbid = zbgateway.getDongleZigBeeIdAsDecimal();
            if (zbid != 0) {
                // a dongle was discovered, notify new dongle thing to inbox
                dongleZigBeeId = zbid;
                logger.debug("==OWN:BridgeDiscovery== DONGLE ZigBeeID is set: {}", dongleZigBeeId);
                notifyNewDongleThing(dongleZigBeeId);
            }
        } else {
            logger.trace("==OWN:BridgeDiscovery== onReceiveFrame() ZigBeeID != 0 : ignoring (msg={})", msg);
        }

    }

}