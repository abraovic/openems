package io.openems.edge.pvinverter.solarlog;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.channel.doc.Unit;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.pvinverter.api.SymmetricPvInverter;


@Designate( ocd = Config.class, factory = true)
@Component(	name = "Meter.Solarlog", 
			immediate = true, 
			configurationPolicy = ConfigurationPolicy.REQUIRE)
public class SolarLog extends AbstractOpenemsModbusComponent 
		implements SymmetricPvInverter, SymmetricMeter, OpenemsComponent {
	
	private final Logger log = LoggerFactory.getLogger(SolarLog.class);
	
	@Reference
	protected ConfigurationAdmin cm;
	
	private MeterType meterType = MeterType.PRODUCTION;
	private ModbusProtocol protocol = null;
	int maxActivePower;
	
	public SolarLog()
	{
	    Utils.initializeChannels(this).forEach(channel -> this.addChannel(channel));
	}
	
	@Reference(	policy = ReferencePolicy.STATIC, 
				policyOption = ReferencePolicyOption.GREEDY, 
				cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus)
	{
	    super.setModbus(modbus);
	}
	
	@Activate
	void activate(ComponentContext context, Config config)
	{
		this.meterType = config.type();
		this._initializeMinMaxActivePower(
				this.cm, 
				config.service_pid(), 
				config.minActivePower(), 
				config.maxActivePower()
		);
		super.activate(
				context, 
				config.service_pid(), 
				config.id(), 
				config.enabled(), 
				config.modbusUnitId(), 
				this.cm,
				"Modbus", 
				config.modbus_id()
		);
		
		this.maxActivePower = config.maxActivePower();
	}

	@Deactivate
	protected void deactivate()
	{
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol()
	{
		this.protocol = new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(RegisterAddress.LAST_UPDATE_TIME.get(), Priority.HIGH,
						m(SolarLog.ChannelId.LAST_UPDATE_TIME, 
								new SignedDoublewordElement(RegisterAddress.LAST_UPDATE_TIME.get()).wordOrder(WordOrder.LSWMSW), 
										ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.PAC, 
								new SignedDoublewordElement(RegisterAddress.PAC.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.PDC, 
								new SignedDoublewordElement(RegisterAddress.PDC.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.UAC, 
								new SignedWordElement(RegisterAddress.UAC.get()), 
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(SolarLog.ChannelId.UDC, 
								new SignedWordElement(RegisterAddress.UDC.get()), 
								ElementToChannelConverter.SCALE_FACTOR_2)),
				new FC4ReadInputRegistersTask(RegisterAddress.DAILY_YIELD.get(), Priority.LOW,
						m(SolarLog.ChannelId.DAILY_YIELD, 
								new SignedDoublewordElement(RegisterAddress.DAILY_YIELD.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.YESTERDAY_YIELD, 
								new SignedDoublewordElement(RegisterAddress.YESTERDAY_YIELD.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.MONTHLY_YIELD, 
								new SignedDoublewordElement(RegisterAddress.MONTHLY_YIELD.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.YEARLY_YIELD, 
								new SignedDoublewordElement(RegisterAddress.YEARLY_YIELD.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.TOTAL_YIELD, 
								new SignedDoublewordElement(RegisterAddress.TOTAL_YIELD.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.PAC_CONSUMPTION, 
								new SignedDoublewordElement(RegisterAddress.PAC_CONSUMPTION.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.DAILY_YIELD_CONS, 
								new SignedDoublewordElement(RegisterAddress.DAILY_YIELD_CONS.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.YESTERDAY_YIELD_CONS, 
								new SignedDoublewordElement(RegisterAddress.YESTERDAY_YIELD_CONS.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.MONTHLY_YIELD_CONS, 
								new SignedDoublewordElement(RegisterAddress.MONTHLY_YIELD_CONS.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.YEARLY_YIELD_CONS, 
								new SignedDoublewordElement(RegisterAddress.YEARLY_YIELD_CONS.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.TOTAL_YIELD_CONS, 
								new SignedDoublewordElement(RegisterAddress.TOTAL_YIELD_CONS.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SolarLog.ChannelId.TOTAL_POWER, 
								new SignedDoublewordElement(RegisterAddress.TOTAL_POWER.get()).wordOrder(WordOrder.LSWMSW), 
								ElementToChannelConverter.DIRECT_1_TO_1)),
				//PV
				new FC6WriteRegisterTask(RegisterAddress.P_LIMIT_TYPE.get(),
						m(SolarLog.ChannelId.P_LIMIT_TYPE, 
								new UnsignedWordElement(RegisterAddress.P_LIMIT_TYPE.get()))),
				new FC6WriteRegisterTask(RegisterAddress.P_LIMIT_PERC.get(),
						m(SolarLog.ChannelId.P_LIMIT_PERC, 
								new UnsignedWordElement(RegisterAddress.P_LIMIT_PERC.get())))/*,
				new FC6WriteRegisterTask(RegisterAddress.WATCH_DOG_TAG.get(),
						m(SolarLog.ChannelId.WATCH_DOG_TAG, 
								new UnsignedDoublewordElement(RegisterAddress.WATCH_DOG_TAG.get()).wordOrder(WordOrder.LSWMSW)))*/
		);
		return this.protocol;
	}

	@Override
	public MeterType getMeterType()
	{
		return this.meterType;
	}
	
	@Override
	public String debugLog()
	{
		return "Max Active Power: " + String.valueOf(this.maxActivePower + " Current PAC: " + String.valueOf(this.channel(ChannelId.PAC)));
	}
	
	@Override
	public void setPVLimit(int power)
	{
		int pLimitPerc = (int) ((double) power / (double) this.maxActivePower * 100.0);
		
		// keep percentage in range [0, 100]
		if (pLimitPerc > 100) {
			pLimitPerc = 100;
		}
		if (pLimitPerc < 0) {
			pLimitPerc = 0;
		}
		
		IntegerWriteChannel pLimitPercCh = this.channel(ChannelId.P_LIMIT_PERC);
		IntegerWriteChannel pLimitTypeCh = this.channel(ChannelId.P_LIMIT_TYPE);
		//IntegerWriteChannel watchDogTagCh = this.channel(ChannelId.WATCH_DOG_TAG);
		
		try {
			pLimitPercCh.setNextWriteValue(pLimitPerc);
		} catch (OpenemsException e) {
			log.error("Unable to set pLimitPerc: " + e.getMessage());
		}
		
		try {
			pLimitTypeCh.setNextWriteValue(2);
		} catch (OpenemsException e) {
			log.error("Unable to set pLimitTypeCh: " + e.getMessage());
		}
		
		/*try {
			watchDogTagCh.setNextWriteValue(pLimitPerc);
		} catch (OpenemsException e) {
			log.error("Unable to set watchDogTagCh: " + e.getMessage());
		}*/
		
	}

	public enum RegisterAddress
	{
		LAST_UPDATE_TIME(3500),
		PAC(3502),
		PDC(3504),
		UAC(3506),
		UDC(3507),
		DAILY_YIELD(3508),
		YESTERDAY_YIELD(3510),
		MONTHLY_YIELD(3512),
		YEARLY_YIELD(3514),
		TOTAL_YIELD(3516),
		PAC_CONSUMPTION(3518),
		DAILY_YIELD_CONS(3520),
		YESTERDAY_YIELD_CONS(3522),
		MONTHLY_YIELD_CONS(3524),
		YEARLY_YIELD_CONS(3526),
		TOTAL_YIELD_CONS(3528),
		TOTAL_POWER(3530),
		
		// PV
		
		P_LIMIT_TYPE(10200),
		P_LIMIT_PERC(10201),
		WATCH_DOG_TAG(10211)
		;
		
		private final int address;

	    RegisterAddress(int address) {
	        this.address = address;
	    }
	    
	    public int get() {
	        return this.address;
	    }
	}
	
	public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId
	{ 
		LAST_UPDATE_TIME(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.SECONDS)),
		PAC(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT)),
		PDC(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT)),
		UAC(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.VOLT)),
		UDC(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.VOLT)),
		DAILY_YIELD(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		YESTERDAY_YIELD(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		MONTHLY_YIELD(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		YEARLY_YIELD(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		TOTAL_YIELD(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		PAC_CONSUMPTION(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT)),
		DAILY_YIELD_CONS(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		YESTERDAY_YIELD_CONS(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		MONTHLY_YIELD_CONS(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		YEARLY_YIELD_CONS(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		TOTAL_YIELD_CONS(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS)),
		TOTAL_POWER(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.WATT_HOURS_BY_WATT_PEAK)),
		
		// PV
		
		P_LIMIT_TYPE(new Doc()
				.type(OpenemsType.INTEGER)),
		P_LIMIT_PERC(new Doc()
				.type(OpenemsType.INTEGER)
				.unit(Unit.PERCENT)),
		WATCH_DOG_TAG(new Doc()
				.type(OpenemsType.INTEGER))
		; 
	    
		private final Doc doc;

	    private ChannelId(Doc doc) { 
	        this.doc = doc;
	    }

	    public Doc doc() {
	        return this.doc;
	    }
	}
}