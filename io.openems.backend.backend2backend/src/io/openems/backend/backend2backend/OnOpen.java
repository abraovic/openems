package io.openems.backend.backend2backend;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.websocket.AbstractOnOpen;

public class OnOpen extends AbstractOnOpen {

	private final Logger log = LoggerFactory.getLogger(OnOpen.class);
	private final WebsocketServer parent;

	public OnOpen(WebsocketServer parent, WebSocket websocket, ClientHandshake handshake) {
		super(websocket, handshake);
		this.parent = parent;
	}

	@Override
	protected void run(WebSocket websocket, ClientHandshake handshake) {
		log.info("OnOpen...");
	}
}
