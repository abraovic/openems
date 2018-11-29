package io.openems.edge.controller.emergencyclustermode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.test.AbstractComponentConfig;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.meter.test.DummySymmetricMeter;
import io.openems.edge.pvinverter.test.DummySymmetricPvInverter;
import io.openems.edge.io.test.DummyInputOutput;

/*
 * Example JUNit test case
 *
 */

public class EmergencyClusterModeTest {

	@SuppressWarnings("all")
	private static class MyConfig extends AbstractComponentConfig implements Config {

		// 
		private final boolean allowChargeFromAC;
		private final boolean gridFeedLimitation;
		private final boolean remoteStart;
		private final boolean isRemoteControlled;
		private final int maxGridFeedPower;
		private final int remoteActivePower;
		
		// devices
		private final String pvInverter;
		private final String primaryEssId;
		private final String backupEssId;
		private final String gridMeterId;
		private final String pvMeterId;
		
		// switches
		private final String Q1_channelAddress;
		private final String Q2_channelAddress;
		private final String Q3_channelAddress;
		private final String Q4_channelAddress;

		public MyConfig(
				String id,
				boolean allowChargeFromAC,
				boolean gridFeedLimitation,
				boolean remoteStart,
				boolean isRemoteControlled,
				int maxGridFeedPower,
				int remoteActivePower,
				String pvInverter,
				String primaryEssId,
				String backupEssId,
				String gridMeterId,
				String pvMeterId,
				String Q1_channelAddress,
				String Q2_channelAddress,
				String Q3_channelAddress,
				String Q4_channelAddress) {
			super(Config.class, id);
			
			this.allowChargeFromAC = allowChargeFromAC;
			this.gridFeedLimitation = gridFeedLimitation;
			this.remoteStart = remoteStart;
			this.isRemoteControlled = isRemoteControlled;
			this.maxGridFeedPower = maxGridFeedPower;
			this.remoteActivePower = remoteActivePower;
			
			this.pvInverter = pvInverter;
			this.primaryEssId = primaryEssId;
			this.backupEssId = backupEssId;
			this.gridMeterId = gridMeterId;
			this.pvMeterId = pvMeterId;
			
			this.Q1_channelAddress = Q1_channelAddress;
			this.Q2_channelAddress = Q2_channelAddress;
			this.Q3_channelAddress = Q3_channelAddress;
			this.Q4_channelAddress = Q4_channelAddress;
		}

		@Override
		public boolean allowChargeFromAC() {
			return this.allowChargeFromAC;
		}

		@Override
		public boolean gridFeedLimitation() {
			return this.gridFeedLimitation;
		}

		@Override
		public boolean isRemoteControlled() {
			return this.isRemoteControlled;
		}

		@Override
		public boolean remoteStart() {
			return this.remoteStart;
		}

		@Override
		public int maxGridFeedPower() {
			return this.maxGridFeedPower;
		}

		@Override
		public int remoteActivePower() {
			return this.remoteActivePower;
		}

		@Override
		public String pv_inverter_id() {
			return this.pvInverter;
		}

		@Override
		public String pv_inverter_target() {
			return "";
		}

		@Override
		public String Q1_channelAddress() {
			return this.Q1_channelAddress;
		}

		@Override
		public String Q1_component_target() {
			return "";
		}
		
		@Override
		public String Q2_channelAddress() {
			return this.Q2_channelAddress;
		}

		@Override
		public String Q2_component_target() {
			return "";
		}

		@Override
		public String Q3_channelAddress() {
			return this.Q3_channelAddress;
		}

		@Override
		public String Q3_component_target() {
			return "";
		}

		@Override
		public String Q4_channelAddress() {
			return this.Q4_channelAddress;
		}

		@Override
		public String Q4_component_target() {
			return "";
		}

		@Override
		public String grid_meter_id() {
			return this.gridMeterId;
		}

		@Override
		public String grid_meter_target() {
			return "";
		}

		@Override
		public String pv_meter_id() {
			return this.pvMeterId;
		}

		@Override
		public String pv_meter_target() {
			return "";
		}

		@Override
		public String primary_ess_id() {
			return this.primaryEssId;
		}

		@Override
		public String primary_ess_target() {
			return "";
		}

		@Override
		public String backup_ess_id() {
			return this.backupEssId;
		}

		@Override
		public String backup_ess_target() {
			return "";
		}

	}
	
	@Test
	public void testOnGrid() throws Exception {
		
		// init controller
		EmergencyClusterMode controller = new EmergencyClusterMode();
		final TimeLeapClock clock14 = new TimeLeapClock(Instant.ofEpochSecond(1543500000), ZoneId.systemDefault());
		final TimeLeapClock clock10 = new TimeLeapClock(Instant.ofEpochSecond(1543485600), ZoneId.systemDefault());
		
		// add references
		controller.cm = new DummyConfigurationAdmin();
		controller.primaryEss = new DummyManagedSymmetricEss("ess0");
		controller.backupEss = new DummyManagedSymmetricEss("ess1");
		controller.gridMeter = new DummySymmetricMeter("meter0");
		controller.pvMeter = new DummySymmetricMeter("meter1");
		controller.pvInverter = new DummySymmetricPvInverter("pvInverter0");
		DummyInputOutput inputOutput = new DummyInputOutput("io0");
		controller.backupEssSwitchComponent = inputOutput;
		controller.primaryEssSwitchComponent = inputOutput;
		controller.pvOffGridSwitchComponent = inputOutput;
		controller.pvOnGridSwitchComponent = inputOutput;
		
		// activate
		ChannelAddress backupEssSwitchChannel = new ChannelAddress("io0", "InputOutput0");
		ChannelAddress primaryEssSwitchChannel = new ChannelAddress("io0", "InputOutput1");
		ChannelAddress pvOffGridSwitchChannel = new ChannelAddress("io0", "InputOutput2");
		ChannelAddress pvOnGridSwitchChannel = new ChannelAddress("io0", "InputOutput3");
		
		MyConfig config = new MyConfig(
				"ctrlEmergencyClusterMode0",
				true,
				true,
				true,
				false,
				10000,
				80000,
				"inverter0",
				"ess0",
				"ess1",
				"meter0",
				"meter1",
				backupEssSwitchChannel.toString(),
				primaryEssSwitchChannel.toString(),
				pvOffGridSwitchChannel.toString(),
				pvOnGridSwitchChannel.toString()
		);
		
		controller.activate(null, config);
		// twice, so that reference target is set
		controller.activate(null, config);
		
		// ess0 - primary
		ChannelAddress ess0GridMode = new ChannelAddress("ess0", "GridMode");
		ChannelAddress ess0ActivePower = new ChannelAddress("ess0", "ActivePower");
		ChannelAddress ess0Soc = new ChannelAddress("ess0", "Soc");
		ChannelAddress ess0AllowedCharge = new ChannelAddress("ess0", "AllowedChargePower");
		ChannelAddress ess0AllowedDischarge = new ChannelAddress("ess0", "AllowedDischargePower");
		
		// ess1 - backup
		ChannelAddress ess1GridMode = new ChannelAddress("ess1", "GridMode");
		ChannelAddress ess1ActivePower = new ChannelAddress("ess1", "ActivePower");
		ChannelAddress ess1Soc = new ChannelAddress("ess1", "Soc");
		ChannelAddress ess1AllowedCharge = new ChannelAddress("ess1", "AllowedChargePower");
		ChannelAddress ess1AllowedDischarge = new ChannelAddress("ess1", "AllowedDischargePower");
		
		// pv inverter
		ChannelAddress pvInverter0ActivePower = new ChannelAddress("pvInverter0", "ActivePower");
		
		// meters
		ChannelAddress meter0ActivePower = new ChannelAddress("meter0", "ActivePower");
		ChannelAddress meter1ActivePower = new ChannelAddress("meter1", "ActivePower");
		
		new ControllerTest(
				controller,
				controller.primaryEss,
				controller.backupEss,
				controller.gridMeter,
				controller.pvMeter,
				controller.pvInverter,
				controller.backupEssSwitchComponent,
				controller.primaryEssSwitchComponent,
				controller.pvOffGridSwitchComponent,
				controller.pvOnGridSwitchComponent)
		
		.next(new TestCase() //
				.timeleap(clock14, 0, ChronoUnit.MINUTES)
				.input(ess0GridMode, 1)
				.input(ess0ActivePower, -40000) //
				.input(ess0Soc, 80) //
				.input(ess0AllowedCharge, -40000) //
				.input(ess0AllowedDischarge, 40000) //
				.input(ess1GridMode, 1)
				.input(ess1ActivePower, -40000) //
				.input(ess1Soc, 80) //
				.input(ess1AllowedCharge, -40000) //
				.input(ess1AllowedDischarge, 40000) //
				.input(pvInverter0ActivePower, 50000) //
				.input(meter0ActivePower, 7000) //
				.input(meter1ActivePower, 50000) //
				.input(backupEssSwitchChannel, false)
				.input(primaryEssSwitchChannel, false)
				.input(pvOffGridSwitchChannel, false)
				.input(pvOnGridSwitchChannel, true)
				.output(backupEssSwitchChannel, false)
				.output(primaryEssSwitchChannel, false)
				.output(pvOffGridSwitchChannel, false)
				.output(pvOnGridSwitchChannel, true)
			)
		.next(new TestCase() //
				.timeleap(clock10, 0, ChronoUnit.MINUTES)
				.input(ess0GridMode, 1)
				.input(ess0ActivePower, -40000) //
				.input(ess0Soc, 80) //
				.input(ess0AllowedCharge, -40000) //
				.input(ess0AllowedDischarge, 40000) //
				.input(ess1GridMode, 1)
				.input(ess1ActivePower, -40000) //
				.input(ess1Soc, 80) //
				.input(ess1AllowedCharge, -40000) //
				.input(ess1AllowedDischarge, 40000) //
				.input(pvInverter0ActivePower, 50000) //
				.input(meter0ActivePower, 7000) //
				.input(meter1ActivePower, 50000) //
				.input(backupEssSwitchChannel, false)
				.input(primaryEssSwitchChannel, false)
				.input(pvOffGridSwitchChannel, false)
				.input(pvOnGridSwitchChannel, true)
				.output(backupEssSwitchChannel, false)
				.output(primaryEssSwitchChannel, false)
				.output(pvOffGridSwitchChannel, false)
				.output(pvOnGridSwitchChannel, true)
			)
		.run();
	}
	
	@Test 
	public void testOffGrid() throws Exception {
		
	}
	
}
