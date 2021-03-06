package io.openems.edge.ess.kaco.blueplanet.gridsave50;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "ESS KACO blueplanet gridsave 50.0 TL3", //
		description = "Implements the FENECON Commercial 40 energy storage system.")
@interface Config {
	String id() default "ess0";

	boolean enabled() default true;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus brige.")
	String modbus_id();

	@AttributeDefinition(name = "Modbus target filter", description = "This is auto-generated by 'Modbus-ID'.")
	String Modbus_target() default "";

	@AttributeDefinition(name = "Watchdog", description = "Sets the watchdog timer interval in seconds, 0=disable")
	int watchdoginterval() default 0;

	@AttributeDefinition(name = "Battery-ID", description = "ID of Battery.")
	String battery_id();

	@AttributeDefinition(name = "Battery target filter", description = "This is auto-generated by 'Battery-ID'.")
	String Battery_target() default "";

	String webconsole_configurationFactory_nameHint() default "ESS KACO blueplanet gridsave 50.0 TL3 [{id}]";
}