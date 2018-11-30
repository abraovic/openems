package io.openems.edge.controller.emergencyclustermode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.pvinverter.api.SymmetricPvInverter;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Controller.EmergencyClusterMode", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class EmergencyClusterMode extends AbstractOpenemsComponent implements Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(EmergencyClusterMode.class);
	private final Clock clock;
	
	// defaults
	private boolean isSwitchedToOffGrid = true;
	private boolean primaryEssSwitch = false; // Q2
	private boolean backupEssSwitch = false; // Q1
	private boolean pvOnGridSwitch = false; // Q4
	private boolean pvOffGridSwitch = false; // Q3
	private int switchDealy = 10000; // 10 sec
	private int pvSwitchDealy = 10000; // 10 sec
	private int pvLimit = 100;
	private long lastPvOffGridDisconnected = 0L;
	private long waitOn = 0L;
	private long waitOff = 0L;
	private boolean firstRun = false;
	
	private boolean allowChargeFromAC;
	private boolean gridFeedLimitation;
	private boolean isRemoteControlled;
	private boolean remoteStart;
	private int maxGridFeedPower;
	private int remoteActivePower;
	private ManagedSymmetricEss activeEss;
	
	
	@Reference
	protected ConfigurationAdmin cm;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected SymmetricMeter gridMeter;
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected SymmetricMeter pvMeter;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected SymmetricPvInverter pvInverter;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected OpenemsComponent backupEssSwitchComponent = null;
	private WriteChannel<Boolean> backupEssSwitchChannel = null;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected OpenemsComponent primaryEssSwitchComponent = null;
	private WriteChannel<Boolean> primaryEssSwitchChannel = null;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected OpenemsComponent pvOffGridSwitchComponent = null;
	private WriteChannel<Boolean> pvOffGridSwitchChannel = null;
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected OpenemsComponent pvOnGridSwitchComponent = null;
	private WriteChannel<Boolean> pvOnGridSwitchChannel = null;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected ManagedSymmetricEss primaryEss;
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected ManagedSymmetricEss backupEss;
	private EssClusterWrapper cluster;
	
	
	public EmergencyClusterMode() {
		this(Clock.systemDefaultZone());
	}

	protected EmergencyClusterMode(Clock clock) {
		this.clock = clock;
	}
	

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.service_pid(), config.id(), config.enabled());
		
		ArrayList<Boolean> references = new ArrayList<Boolean>();
		// update filters
		try {
			// Solar Log
			references.add(OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "pvInverter", 
					config.pv_inverter_id()));
			
			// meters
			references.add(OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "gridMeter", 
					config.grid_meter_id()));
			references.add(OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "pvMeter", 
					config.pv_meter_id()));
						
			// esss
			references.add(OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "primaryEss", 
					config.primary_ess_id()));
			references.add(OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "backupEss", 
					config.backup_ess_id()));
			
			
			// wago
			//Q1
			ChannelAddress channelAddress = ChannelAddress.fromString(config.Q1_channelAddress());
			
			references.add(OpenemsComponent.updateReferenceFilter(this.cm, config.service_pid(), "backupEssSwitchComponent", 
					channelAddress.getComponentId()));
			
			this.backupEssSwitchChannel = this.backupEssSwitchComponent.channel(channelAddress.getChannelId());
			
			//Q2
			channelAddress = ChannelAddress.fromString(config.Q2_channelAddress());
			
			references.add(OpenemsComponent.updateReferenceFilter(this.cm, config.service_pid(), "primaryEssSwitchComponent", 
					channelAddress.getComponentId()));
			
			this.primaryEssSwitchChannel = this.primaryEssSwitchComponent.channel(channelAddress.getChannelId());
			
			//Q3
			channelAddress = ChannelAddress.fromString(config.Q3_channelAddress());
			
			references.add(OpenemsComponent.updateReferenceFilter(this.cm, config.service_pid(), "pvOffGridSwitchComponent", 
					channelAddress.getComponentId()));
			
			this.pvOffGridSwitchChannel = this.pvOffGridSwitchComponent.channel(channelAddress.getChannelId());
			
			//Q4
			channelAddress = ChannelAddress.fromString(config.Q4_channelAddress());
			
			references.add(OpenemsComponent.updateReferenceFilter(this.cm, config.service_pid(), "pvOnGridSwitchComponent", 
					channelAddress.getComponentId()));

			
			this.pvOnGridSwitchChannel = this.pvOnGridSwitchComponent.channel(channelAddress.getChannelId());
			
			if (!references.contains(false)) {
				// all update references passes
				return;
			}
		} catch (OpenemsException e) {
			e.printStackTrace();
		}
		
		// make some preparations here
		this.maxGridFeedPower = config.maxGridFeedPower();
		this.allowChargeFromAC = config.allowChargeFromAC();
		this.remoteActivePower = config.remoteActivePower();
		this.gridFeedLimitation = config.gridFeedLimitation();
		this.remoteStart = config.remoteStart();
		this.isRemoteControlled = config.isRemoteControlled();
		this.activeEss = this.primaryEss;
		this.cluster = new EssClusterWrapper();
		this.cluster.add(this.primaryEss);
		this.cluster.add(this.backupEss);
		
		this.log.debug("EmergencyClusterMode bundle activated");
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() {
		
		if (!this.firstRun) {
			this.checkOnFristRun();
		}
		
		if (this.remoteStart) {
			try {
				if (this.cluster.isOnGrid()) {
					this.onGrid();
				} else {
					this.offGrid();
				}
				
				this.pvInverter.getActivePowerLimit().setNextWriteValue(this.pvLimit);
				this.pvOnGridSwitchChannel.setNextWriteValue(this.pvOnGridSwitch);
				this.pvOffGridSwitchChannel.setNextWriteValue(this.pvOffGridSwitch);
				this.primaryEssSwitchChannel.setNextWriteValue(this.primaryEssSwitch);
				this.backupEssSwitchChannel.setNextWriteValue(this.backupEssSwitch);
			} catch (OpenemsException e) {
				this.log.error("Error on reading remote Stop Element", e);
			}
			
		} else {
			this.log.info("Remote start is not available");
		}
	}
	
	// when ess detects on grid mode
	private void onGrid() {
		if (this.isSwitchedToOffGrid) {
			this.log.info("Switch to On-Grid");
			// system detects that grid is on, but it is currently switched off grid
		    // it means that all ESS and PV needs to be switched to onGrid
			try {
				if (this.allEssDisconnected() && !this.pvOffGridSwitchChannel.value().getOrError() && !this.pvOnGridSwitchChannel.value().getOrError()) {
					if (this.waitOn + this.switchDealy <= LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
						this.primaryEssSwitch = false;
						this.pvLimit = this.pvInverter.getActivePower().value().getOrError();
						this.pvOnGridSwitch = true;
						this.activeEss = null;
						this.isSwitchedToOffGrid = false;
					} else {
						// wait for 10 seconds after switches are disconnected
					}
				} else {
					this.primaryEssSwitch = true;
					this.backupEssSwitch = false;
					this.pvOffGridSwitch = this.pvOnGridSwitch = false;
					this.waitOn =  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				}
			} catch (InvalidValueException e) {
				this.log.error("Failed to switch to OnGrid mode because there are invalid values!", e);
			}
			
		} else {
			// system detects that grid is on, and it is switched to
		    // On Grid
			try {
				int calculatedPower = this.cluster.getActivePower() + this.gridMeter.getActivePower().value().getOrError();
				int calculatedEssActivePower = calculatedPower;
				
				if (this.isRemoteControlled) {
					int maxPower = Math.abs(this.remoteActivePower);
					if (calculatedEssActivePower > maxPower) {
						calculatedEssActivePower = maxPower;
					} else if (calculatedEssActivePower < maxPower * -1){
						calculatedEssActivePower = maxPower * -1;
					}
				}
				
				int essSoc = this.cluster.getSoc();
				if (calculatedEssActivePower >= 0) {
					// discharge
					// adjust calculatedEssActivePower to max allowed discharge power
					if (this.cluster.getAllowedDischarge() < calculatedEssActivePower) {
						calculatedEssActivePower = this.cluster.getAllowedDischarge();
					}
				} else {
					// charge
					if (this.allowChargeFromAC) {
						// This is upper part of battery which is primarily used for charging during peak PV production (after 11:00h)
						int reservedSoc = 50;
						if (LocalDateTime.now(this.clock).getHour() <= 11 && essSoc > 100 - reservedSoc && this.gridMeter.getActivePower().value().getOrError() < this.maxGridFeedPower) {
							//reduced charging formula – reduction based on current SOC and reservedSoc
							calculatedEssActivePower = calculatedEssActivePower / (reservedSoc * 2) * (reservedSoc - (essSoc - (100 - reservedSoc)));
						} else {
							//full charging formula – no restrictions except max charging power that batteries can accept
							if (calculatedEssActivePower < this.cluster.getAllowedCharge()) {
								calculatedEssActivePower = this.cluster.getAllowedCharge();
							}
						}
					} else {
						// charging disallowed
						calculatedEssActivePower = 0;
					}
				}
				
				if (this.gridFeedLimitation) {
					// actual formula pvCounter.power + (calculatedEssActivePower- cluster.allowedChargePower+ maxGridFeedPower+gridCounter.power)
					this.pvLimit = this.pvMeter.getActivePower().value().getOrError() + 
							(calculatedEssActivePower - this.cluster.getAllowedCharge() + this.maxGridFeedPower + this.gridMeter.getActivePower().value().getOrError());
					if (this.pvLimit < 0) {
						this.pvLimit = 0;
					}
				} else {
					this.pvLimit = this.pvInverter.getActivePower().value().getOrError();
				}
				
				this.cluster.applyPower(calculatedEssActivePower, 0);
				
			} catch (InvalidValueException e) {
				this.log.error("An error occured on controll the storages!", e);
				this.pvLimit = 0;
				try {
					this.cluster.applyPower(0, 0);
				} catch (InvalidValueException ee) {
					log.error("Failed to stop ess!");
				}
			}
			
		}
	}
	
	// when ess detects off grid mode
	private void offGrid(){
		if (this.isSwitchedToOffGrid) {
			// the system detects that is is off grid and it is
		    // switched to off gird mode
			this.log.info("Switch to Off-Grid");
			if (this.pvInverter.getActivePower().value().get() <= 35000 && this.pvMeter.getActivePower().value().get() <= 37000) {
				if (this.lastPvOffGridDisconnected + this.pvSwitchDealy <=  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
					this.pvOffGridSwitch = true;
				} else {
					this.pvOffGridSwitch = false;
				}
			} else {
				this.pvOffGridSwitch = false;
				this.lastPvOffGridDisconnected =  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			}
			
			try {
				if (this.activeEss.getSoc().value().get() <= 5) {
					if (this.allEssDisconnected()) {
						if (this.waitOff + this.switchDealy <=  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
							this.activeEss = this.backupEss;
							this.backupEssSwitch = true;
						} else {
							// wait for 10 seconds after switches are disconnected
						}
					} else {
						this.primaryEssSwitch = true;
						this.backupEssSwitch = false;
						this.pvOffGridSwitch = false;
						this.waitOff =  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
					}
				}
				
				// disconnect PV if active soc is >= 95%
				if (this.activeEss.getSoc().value().get() >= 95) {
					this.pvOffGridSwitch = false;
				}
				// reconnect PV if active soc goes under 75%
				if (this.activeEss.getSoc().value().get() <= 75 && 
						this.lastPvOffGridDisconnected + this.pvSwitchDealy <=  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
					this.pvOffGridSwitch = true;
				}
			} catch (InvalidValueException e) {
				this.log.error("Can't switch to the next storage, because ther are invalid values", e);
			}
			
		} else {
			// the system detects that is is off grid and it is
		    // NOT switched to off gird mode (UPS is running)
		    // it means that all ESS and PV needs to be switched to offGrid
			try {
				if (this.allEssDisconnected() && !this.pvOffGridSwitchChannel.value().get() && !this.pvOnGridSwitchChannel.value().get()) {
					if (this.waitOff <=  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
						this.primaryEssSwitch = false;
						this.activeEss = this.primaryEss;
						if (this.activeEss.getSoc().value().get() < 95 && this.pvInverter.getActivePower().value().get() <= 35000) {
							this.pvOffGridSwitch = true;
						}
						this.isSwitchedToOffGrid = true;
					}
				} else {
					this.primaryEssSwitch = true;
					this.backupEssSwitch = false;
					this.pvOffGridSwitch = this.pvOnGridSwitch = false;
					this.pvLimit = 35000;
					this.waitOff =  LocalDateTime.now(this.clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				}
			} catch (InvalidValueException e) {
				this.log.error("Can't switch to OffGrid because there are invalid values!");
			}
		}
	}

	private void checkOnFristRun() {
		// check current grid state
		if (this.cluster.isOnGrid()) {
			try {
				if (this.isSwitchedToOnGrid()) {
					this.pvOnGridSwitch = true;
					this.pvOffGridSwitch = false;
					this.primaryEssSwitch = false;
					this.backupEssSwitch = false;
					this.pvLimit = this.pvInverter.getActivePower().value().get();
					this.isSwitchedToOffGrid = false;
				} else {
					this.isSwitchedToOffGrid = true;
				}
			} catch (InvalidValueException e) {
				log.error(e.getMessage());
			}
			
		} else {
			this.isSwitchedToOffGrid = false;
		}
		
		this.firstRun = true;
	}
	
	/**
	 * Checks if both ESS devices are disconnected from grid
	 * -> primaryEssSwitch is NC so it must be true to be opened <-
	 * 
	 * @return boolean
	 * */
	private boolean allEssDisconnected() throws InvalidValueException {
		if (!this.primaryEssSwitchChannel.value().getOrError()) {
			return false;
		}
		if (this.backupEssSwitchChannel.value().getOrError()) {
			return false;
		}
		return true;
	}
	
	/**
	 * Check if system is in On Grid mode: 
	 * - Q1 and Q3 are off
	 * - Q2 and Q4 are on (Q2 -> false)
	 * 
	 * @return boolean
	 * */
	private boolean isSwitchedToOnGrid() throws InvalidValueException {
		if (this.primaryEssSwitchChannel.value().getOrError()) {
			return false;
		}
		if (this.backupEssSwitchChannel.value().getOrError()) {
			return false;
		}
		if (this.pvOffGridSwitchChannel.value().getOrError()) {
			return false;
		}
		if (!this.pvOnGridSwitchChannel.value().getOrError()) {
			return false;
		}
		
		return true;
	}
}
