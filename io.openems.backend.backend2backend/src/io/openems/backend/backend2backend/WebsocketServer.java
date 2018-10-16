package io.openems.backend.backend2backend;

import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import io.openems.common.websocket.AbstractOnClose;
import io.openems.common.websocket.AbstractOnError;
import io.openems.common.websocket.AbstractOnMessage;
import io.openems.common.websocket.AbstractOnOpen;
import io.openems.common.websocket.AbstractWebsocketServer;

public class WebsocketServer extends AbstractWebsocketServer {

	protected final Backend2Backend parent;
	protected final Map<Integer, WebSocket> websocketsMap = new HashMap<>();

	public WebsocketServer(Backend2Backend parent, int port) {
		super(port);
		this.parent = parent;
	}

	@Override
	protected AbstractOnMessage _onMessage(WebSocket websocket, String message) {
		return new OnMessage(this, websocket, message);
	}

	@Override
	protected AbstractOnOpen _onOpen(WebSocket websocket, ClientHandshake handshake) {
		return new OnOpen(this, websocket, handshake);
	}

	@Override
	protected AbstractOnError _onError(WebSocket websocket, Exception ex) {
		return new OnError(this, websocket, ex);
	}

	@Override
	protected AbstractOnClose _onClose(WebSocket websocket, int code, String reason, boolean remote) {
		return new OnClose(this, websocket, code, reason, remote);
	}
}
