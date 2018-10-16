package io.openems.backend.backend2backend;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.JsonUtils;
import io.openems.common.websocket.AbstractOnMessage;

public class OnMessage extends AbstractOnMessage {

	private final Logger log = LoggerFactory.getLogger(OnMessage.class);
	private final WebsocketServer parent;

	public OnMessage(WebsocketServer parent, WebSocket websocket, String message) {
		super(websocket, message);
		this.parent = parent;
	}

	protected void run(WebSocket websocket, JsonObject jMessage) {
		log.info("Server: OnMessage [" + jMessage + "]");

		JsonObject jParams;
		String id;
		String method;
		try {
			id = JsonUtils.getAsString(jMessage, "id");
			jParams = JsonUtils.getAsJsonObject(jMessage, "params");
			method = JsonUtils.getAsString(jMessage, "method");
		} catch (OpenemsException e) {
			e.printStackTrace();
			return;
		}

		JsonObject jReply = null;
		switch (method) {
		case "listAllEdge":
			jReply = this.listAllEdge(jParams);
			break;
		}

		if (jReply != null) {
			websocket.send(Utils.createJsonRpcResult(id, jReply).toString());
		}
	}

	private JsonObject listAllEdge(JsonObject jParams) {
		JsonObject j = new JsonObject();
		JsonObject j1 = new JsonObject();
		j1.addProperty("online", true);
		j.add("fems1", j1);
		JsonObject j2 = new JsonObject();
		j2.addProperty("online", false);
		j.add("fems2", j2);
		return j;
	}
}
