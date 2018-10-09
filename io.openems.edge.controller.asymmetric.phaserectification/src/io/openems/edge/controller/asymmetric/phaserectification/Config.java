package io.openems.edge.controller.asymmetric.phaserectification;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Controller PhaseRectification", //
		description = "Sets the ess to the required activepower to get all three phases on the meter to the same level.")
@interface Config {
	String service_pid();

	String id() default "ctrlPhaseRectification0";

	boolean enabled() default true;

	@AttributeDefinition(name = "Ess-ID", description = "ID of Ess device.")
	String ess_id();

	@AttributeDefinition(name = "Ess target filter", description = "This is auto-generated by 'Ess-ID'.")
	String ess_target() default "";

	@AttributeDefinition(name = "Grid-Meter-ID", description = "ID of the Grid-Meter.")
	String meter_id();

	@AttributeDefinition(name = "Grid-Meter target filter", description = "This is auto-generated by 'Grid-Meter-ID'.")
	String meter_target() default "";

	String webconsole_configurationFactory_nameHint() default "Controller PhaseRectification Asymmetric [{id}]";
}