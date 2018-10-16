package io.openems.backend.backend2backend;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WebsocketTest {

	private final Logger log = LoggerFactory.getLogger(WebsocketTest.class);

	private Consumer<JsonObject> callback = null;
	private final WebSocketClient ws;

	public WebsocketTest(String serverUri) throws URISyntaxException {
		this.ws = new WebSocketClient(new URI(serverUri)) {

			@Override
			public void onOpen(ServerHandshake arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onMessage(String message) {
				log.info("Client: OnMessage [" + message + "]");
				if (callback != null) {
					JsonObject jMessage = (new JsonParser()).parse(message).getAsJsonObject();
					callback.accept(jMessage);
				}
			}

			@Override
			public void onError(Exception arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onClose(int arg0, String arg1, boolean arg2) {
				// TODO Auto-generated method stub
			}
		};
		this.ws.connect();
	}

	public void send(JsonObject jMessage, Consumer<JsonObject> callback) {
		this.callback = callback;
		this.ws.send(jMessage.toString());
	}
}
