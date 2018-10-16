package io.openems.backend.backend2backend;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.osgi.service.metatype.annotations.Designate;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Backend2BackendWebsocket", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Backend2Backend {

	public final static int DEFAULT_PORT = 8076;
	
	private final Logger log = LoggerFactory.getLogger(Backend2Backend.class);

	private WebsocketServer server = null;

	@Activate
	void activate(Config config) {
		log.info("Activate Backend2BackendWebsocket [port=" + config.port() + "]");

		this.stopServer();
		this.startServer(config.port());
	}

	@Deactivate
	void deactivate() {
		log.info("Deactivate Backend2BackendWebsocket");
		this.stopServer();
	}

	/**
	 * Stop existing websocket server
	 */
	private synchronized void stopServer() {
		if (this.server != null) {
			int tries = 3;
			while (tries-- > 0) {
				try {
					this.server.stop(1000);
					return;
				} catch (NullPointerException | InterruptedException e) {
					log.warn("Unable to stop existing WebsocketServer. " + e.getClass().getSimpleName() + ": "
							+ e.getMessage());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						/* ignore */
					}
				}
			}
			log.error("Stopping WebsocketServer failed too often.");
		}
	}

	/**
	 * Create and start new server
	 * 
	 * @param port
	 */
	private synchronized void startServer(int port) {
		this.server = new WebsocketServer(this, port);
		this.server.start();
	}
}
