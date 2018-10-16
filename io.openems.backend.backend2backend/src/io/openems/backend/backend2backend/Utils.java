package io.openems.backend.backend2backend;

import java.util.UUID;

import com.google.gson.JsonObject;

public class Utils {

	public static JsonObject createJsonRpcRequest(String id, String method, JsonObject jParams) {
		JsonObject j = new JsonObject();
		j.addProperty("jsonrpc", "2.0");
		j.addProperty("id", id);
		j.addProperty("method", method);
		j.add("params", jParams);
		return j;
	}

	public static JsonObject createJsonRpcResult(String id, JsonObject jResult) {
		JsonObject j = new JsonObject();
		j.addProperty("jsonrpc", "2.0");
		j.addProperty("id", UUID.randomUUID().toString());
		j.add("result", jResult);
		return j;
	}

}
