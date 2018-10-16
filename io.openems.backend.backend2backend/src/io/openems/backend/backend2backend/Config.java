package io.openems.backend.backend2backend;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Backend2BackendWebsocket", //
		description = "Configures the Backend2Backend-Websocket")
@interface Config {

	@AttributeDefinition(name = "Port", description = "The port of the websocket server.")
	int port() default Backend2Backend.DEFAULT_PORT;

	String webconsole_configurationFactory_nameHint() default "Backend-2-Backend-Websocket";
}
