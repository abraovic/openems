package io.openems.backend.backend2backend;

import java.net.URISyntaxException;
import java.util.UUID;

import org.junit.Test;

import com.google.gson.JsonObject;

public class Backend2BackendTest {

	@Test
	public void test() throws URISyntaxException, InterruptedException {
		WebsocketTest ws = new WebsocketTest("ws://localhost:" + Backend2Backend.DEFAULT_PORT);

		String id = UUID.randomUUID().toString();
		JsonObject jRequest = Utils.createJsonRpcRequest(id, "listAllEdge", new JsonObject());
		ws.send(jRequest, jReply -> {
			System.out.println(jReply);
		});

		Thread.sleep(5000);
	}

}
