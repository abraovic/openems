package io.openems.backend.backend2backend;

import java.util.Optional;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.websocket.AbstractOnClose;

public class OnClose extends AbstractOnClose {

	private final Logger log = LoggerFactory.getLogger(OnClose.class);

	private final WebsocketServer parent;

	public OnClose(WebsocketServer parent, WebSocket websocket, int code, String reason, boolean remote) {
		super(websocket, code, reason, remote);
		this.parent = parent;
	}

	@Override
	protected void run(WebSocket websocket, int code, String reason, boolean remote) {
		log.info("OnClose...");
	}

}
