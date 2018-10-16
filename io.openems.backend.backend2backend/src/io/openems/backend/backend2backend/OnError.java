package io.openems.backend.backend2backend;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.websocket.AbstractOnError;

public class OnError extends AbstractOnError {

	private final Logger log = LoggerFactory.getLogger(OnError.class);
	private final WebsocketServer parent;

	public OnError(WebsocketServer parent, WebSocket websocket, Exception ex) {
		super(websocket, ex);
		this.parent = parent;
	}

	@Override
	protected void run(WebSocket websocket, Exception ex) {
		log.info("OnError...");
	}

}
