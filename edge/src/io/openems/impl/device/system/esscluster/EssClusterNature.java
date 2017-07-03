package io.openems.impl.device.system.esscluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;

import io.openems.api.channel.Channel;
import io.openems.api.channel.ChannelChangeListener;
import io.openems.api.channel.ConfigChannel;
import io.openems.api.channel.FunctionalReadChannel;
import io.openems.api.channel.FunctionalReadChannelFunction;
import io.openems.api.channel.FunctionalWriteChannel;
import io.openems.api.channel.FunctionalWriteChannelFunction;
import io.openems.api.channel.ReadChannel;
import io.openems.api.channel.StatusBitChannels;
import io.openems.api.channel.WriteChannel;
import io.openems.api.device.nature.DeviceNature;
import io.openems.api.device.nature.ess.EssNature;
import io.openems.api.device.nature.ess.SymmetricEssNature;
import io.openems.api.doc.ConfigInfo;
import io.openems.api.exception.ConfigException;
import io.openems.api.exception.InvalidValueException;
import io.openems.api.exception.WriteChannelException;
import io.openems.api.thing.ThingChannelsUpdatedListener;
import io.openems.core.ThingRepository;
import io.openems.impl.protocol.system.SystemDeviceNature;

public class EssClusterNature extends SystemDeviceNature implements SymmetricEssNature, ChannelChangeListener {

	private final Logger log;
	private List<ThingChannelsUpdatedListener> listeners;

	private static ThingRepository repo = ThingRepository.getInstance();

	@ConfigInfo(title = "Ess", description = "Sets the Ess devices for the cluster.", type = JsonArray.class)
	public ConfigChannel<JsonArray> esss = new ConfigChannel<JsonArray>("esss", this).addChangeListener(this);
	private ConfigChannel<Integer> minSoc = new ConfigChannel<>("minSoc", this);
	private ConfigChannel<Integer> chargeSoc = new ConfigChannel<Integer>("chargeSoc", this);
	private List<SymmetricEssNature> essList = new ArrayList<>();
	private FunctionalReadChannel<Long> soc = new FunctionalReadChannel<Long>("Soc", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					double nominalKWhSum = 0;
					double actualCapacity = 0;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						long capacity;
						try {
							capacity = ess.capacity().value();
							nominalKWhSum += capacity;
							actualCapacity += (capacity / 100.0) * ess.soc().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return (long) (actualCapacity / nominalKWhSum * 100.0);
				}

			}).unit("%");
	private FunctionalReadChannel<Long> allowedCharge = new FunctionalReadChannel<Long>("AllowedCharge", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.allowedCharge().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("W");
	private FunctionalReadChannel<Long> allowedDischarge = new FunctionalReadChannel<Long>("AllowedDischarge", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.allowedDischarge().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("W");
	private FunctionalReadChannel<Long> allowedApparent = new FunctionalReadChannel<Long>("AllowedApparent", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.allowedApparent().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("VA");
	private FunctionalReadChannel<Long> activePower = new FunctionalReadChannel<Long>("ActivePower", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.activePower().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("W");
	private FunctionalReadChannel<Long> reactivePower = new FunctionalReadChannel<Long>("ReactivePower", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.reactivePower().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("Var");
	private FunctionalReadChannel<Long> apparentPower = new FunctionalReadChannel<Long>("ApparentPower", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.apparentPower().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("VA");
	private FunctionalReadChannel<Long> maxNominalPower = new FunctionalReadChannel<Long>("MaxNominalPower", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.maxNominalPower().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("VA");
	private FunctionalReadChannel<Long> capacity = new FunctionalReadChannel<Long>("Capacity", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (SymmetricEssNature ess : EssClusterNature.this.essList) {
						try {
							sum += ess.capacity().value();
						} catch (InvalidValueException e) {
							log.debug("Can't read values of " + ess.id(), e);
						}
					}
					return sum;
				}

			}).unit("Wh");

	private FunctionalReadChannel<Long> gridMode = new FunctionalReadChannel<Long>("GridMode", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					for (SymmetricEssNature ess : essList) {
						if (ess.gridMode().labelOptional().equals(Optional.of(EssNature.ON_GRID))) {
							return 1L;
						}
					}
					return 0L;
				}

			}).label(0L, EssNature.OFF_GRID).label(1L, EssNature.ON_GRID);
	private FunctionalReadChannel<Long> systemState = new FunctionalReadChannel<Long>("SystemState", this,
			new FunctionalReadChannelFunction<Long>() {

				@Override
				public Long handle(ReadChannel<Long>... channels) {
					for (SymmetricEssNature ess : essList) {
						if (!ess.systemState().labelOptional().equals(Optional.of(EssNature.ON))) {
							if (ess.systemState().labelOptional().equals(Optional.of(EssNature.OFF))) {
								return 0L;
							} else if (ess.systemState().labelOptional().equals(Optional.of(EssNature.FAULT))) {
								return 2L;
							} else {
								return 3L;
							}

						}
					}
					return 1L;
				}

			}).label(0L, EssNature.STOP).label(1L, EssNature.START).label(2L, EssNature.FAULT).label(3L, "UNDEFINED");
	private StatusBitChannels warning = new StatusBitChannels("Warning", this);

	private FunctionalWriteChannel<Long> setWorkState = new FunctionalWriteChannel<Long>("SetWorkState", this,
			new FunctionalWriteChannelFunction<Long>() {

				@Override
				public void setValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteFromLabel(newLabel);
						} catch (WriteChannelException e) {
							log.error("Can't set value for channel " + channel.address(), e);
						}
					}
				}

				@Override
				public Long getValue(ReadChannel<Long>... channels) {
					for (ReadChannel<Long> state : channels) {
						if (state.labelOptional().equals(Optional.of(EssNature.START))) {
							return 1L;
						}
					}
					return 0L;
				}

				@Override
				public Long getMinValue(WriteChannel<Long>... channels) {
					long min = Long.MIN_VALUE;
					for (WriteChannel<Long> channelMin : channels) {
						if (channelMin.writeMin().isPresent() && channelMin.writeMin().get() > min) {
							min = channelMin.writeMin().get();
						}
					}
					if (min == Long.MIN_VALUE) {
						return null;
					} else {
						return min;
					}
				}

				@Override
				public Long getMaxValue(WriteChannel<Long>... channels) {
					long max = Long.MAX_VALUE;
					for (WriteChannel<Long> channelMax : channels) {
						if (channelMax.writeMax().isPresent() && channelMax.writeMax().get() < max) {
							max = channelMax.writeMax().get();
						}
					}
					if (max == Long.MAX_VALUE) {
						return null;
					} else {
						return max;
					}
				}

				@Override
				public void setMinValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMin(newValue);
						} catch (WriteChannelException e) {
							log.error("Can't set value for channel " + channel.address(), e);
						}
					}
				}

				@Override
				public void setMaxValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMax(newValue);
						} catch (WriteChannelException e) {
							log.error("Can't set value for channel " + channel.address(), e);
						}
					}
				}

			}).label(0L, EssNature.STOP).label(1L, EssNature.START);

	private FunctionalWriteChannel<Long> setActivePower = new FunctionalWriteChannel<Long>("SetActivePower", this,
			new FunctionalWriteChannelFunction<Long>() {

				@Override
				public void setValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWrite(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

				@Override
				public Long getValue(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (ReadChannel<Long> channel : channels) {
						try {
							sum += channel.value();
						} catch (InvalidValueException e) {
							log.error("Can't read ActivePower from " + channel.address());
						}
					}
					return sum;
				}

				@Override
				public Long getMinValue(WriteChannel<Long>... channels) {
					long min = 0L;
					boolean isPresent = false;
					for (WriteChannel<Long> channelMin : channels) {
						if (channelMin.writeMin().isPresent()) {
							min += channelMin.writeMin().get();
							isPresent = true;
						}
					}
					if (isPresent) {
						return min;
					}
					return null;
				}

				@Override
				public Long getMaxValue(WriteChannel<Long>... channels) {
					long max = 0L;
					boolean isPresent = false;
					for (WriteChannel<Long> channelMax : channels) {
						if (channelMax.writeMax().isPresent()) {
							max += channelMax.writeMax().get();
							isPresent = true;
						}
					}
					if (isPresent) {
						return max;
					}
					return null;
				}

				@Override
				public void setMinValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMin(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

				@Override
				public void setMaxValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMax(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

			});
	private FunctionalWriteChannel<Long> setReactivePower = new FunctionalWriteChannel<Long>("SetReactivePower", this,
			new FunctionalWriteChannelFunction<Long>() {

				@Override
				public void setValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWrite(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

				@Override
				public Long getValue(ReadChannel<Long>... channels) {
					long sum = 0L;
					for (ReadChannel<Long> channel : channels) {
						try {
							sum += channel.value();
						} catch (InvalidValueException e) {
							log.error("Can't read ReactivePower from " + channel.address());
						}
					}
					return sum;
				}

				@Override
				public Long getMinValue(WriteChannel<Long>... channels) {
					long min = 0L;
					boolean isPresent = false;
					for (WriteChannel<Long> channelMin : channels) {
						if (channelMin.writeMin().isPresent()) {
							min += channelMin.writeMin().get();
							isPresent = true;
						}
					}
					if (isPresent) {
						return min;
					}
					return null;
				}

				@Override
				public Long getMaxValue(WriteChannel<Long>... channels) {
					long max = 0L;
					boolean isPresent = false;
					for (WriteChannel<Long> channelMax : channels) {
						if (channelMax.writeMax().isPresent()) {
							max += channelMax.writeMax().get();
							isPresent = true;
						}
					}
					if (isPresent) {
						return max;
					}
					return null;

				}

				@Override
				public void setMinValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMin(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

				@Override
				public void setMaxValue(Long newValue, String newLabel, WriteChannel<Long>... channels) {
					long power = 0L;
					if (channels.length > 0) {
						power = newValue / channels.length;
					}
					for (WriteChannel<Long> channel : channels) {
						try {
							channel.pushWriteMax(power);
						} catch (WriteChannelException e) {
							log.error("Failed to write " + power + " to " + channel.address(), e);
						}
					}
				}

			});

	public EssClusterNature(String id) throws ConfigException {
		super(id);
		log = LoggerFactory.getLogger(this.getClass());
		this.listeners = new ArrayList<>();
	}

	@Override
	public void setAsRequired(Channel channel) {
		// unused
	}

	@Override
	public ConfigChannel<Integer> minSoc() {
		return minSoc;
	}

	@Override
	public ConfigChannel<Integer> chargeSoc() {
		return chargeSoc;
	}

	@Override
	public ReadChannel<Long> gridMode() {
		return gridMode;
	}

	@Override
	public ReadChannel<Long> soc() {
		return soc;
	}

	@Override
	public ReadChannel<Long> systemState() {
		return systemState;
	}

	@Override
	public ReadChannel<Long> allowedCharge() {
		return allowedCharge;
	}

	@Override
	public ReadChannel<Long> allowedDischarge() {
		return allowedDischarge;
	}

	@Override
	public ReadChannel<Long> allowedApparent() {
		return allowedApparent;
	}

	@Override
	public StatusBitChannels warning() {
		return warning;
	}

	@Override
	public WriteChannel<Long> setWorkState() {
		return setWorkState;
	}

	@Override
	public ReadChannel<Long> activePower() {
		return activePower;
	}

	@Override
	public ReadChannel<Long> apparentPower() {
		return apparentPower;
	}

	@Override
	public ReadChannel<Long> reactivePower() {
		return reactivePower;
	}

	@Override
	public ReadChannel<Long> maxNominalPower() {
		return maxNominalPower;
	}

	@Override
	public WriteChannel<Long> setActivePower() {
		return setActivePower;
	}

	@Override
	public WriteChannel<Long> setReactivePower() {
		return setReactivePower;
	}

	@Override
	public ReadChannel<Long> capacity() {
		return capacity;
	}

	@Override
	public void addListener(ThingChannelsUpdatedListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ThingChannelsUpdatedListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void channelChanged(Channel channel, Optional<?> newValue, Optional<?> oldValue) {
		if (channel.equals(esss)) {
			loadEss();
		}
	}

	private void loadEss() {
		Set<DeviceNature> natures = repo.getDeviceNatures();
		JsonArray essIds;
		try {
			essIds = esss.value();
			// remove old ess
			for (SymmetricEssNature ess : this.essList) {
				soc.removeChannel(ess.soc());
				gridMode.removeChannel(ess.gridMode());
				systemState.removeChannel(ess.systemState());
				allowedCharge.removeChannel(ess.allowedCharge());
				allowedDischarge.removeChannel(ess.allowedDischarge());
				allowedApparent.removeChannel(ess.allowedApparent());
				activePower.removeChannel(ess.activePower());
				reactivePower.removeChannel(ess.reactivePower());
				apparentPower.removeChannel(ess.apparentPower());
				maxNominalPower.removeChannel(ess.maxNominalPower());
				capacity.removeChannel(ess.capacity());
				setWorkState.removeChannel(ess.setWorkState());
				setActivePower.removeChannel(ess.setActivePower());
				setReactivePower.removeChannel(ess.setReactivePower());
			}
			essList.clear();
			if (essIds != null) {
				for (DeviceNature nature : natures) {
					if (nature instanceof SymmetricEssNature) {
						if (essIds.toString().contains(nature.id())) {
							SymmetricEssNature ess = (SymmetricEssNature) nature;
							essList.add(ess);
							soc.addChannel(ess.soc());
							gridMode.addChannel(ess.gridMode());
							systemState.addChannel(ess.systemState());
							allowedCharge.addChannel(ess.allowedCharge());
							allowedDischarge.addChannel(ess.allowedDischarge());
							allowedApparent.addChannel(ess.allowedApparent());
							activePower.addChannel(ess.activePower());
							reactivePower.addChannel(ess.reactivePower());
							apparentPower.addChannel(ess.apparentPower());
							maxNominalPower.addChannel(ess.maxNominalPower());
							capacity.addChannel(ess.capacity());
							setWorkState.addChannel(ess.setWorkState());
							setActivePower.addChannel(ess.setActivePower());
							setReactivePower.addChannel(ess.setReactivePower());
						}
					}
				}
				// capacity.channelUpdated(null, null);
			}
		} catch (InvalidValueException e) {
			log.error("esss value is invalid!", e);
		}
	}

	@Override
	protected void update() {
		try {
			if (esss.value().size() != essList.size()) {
				loadEss();
			}
		} catch (InvalidValueException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void init() {
		for (ThingChannelsUpdatedListener listener : this.listeners) {
			listener.thingChannelsUpdated(this);
		}
	}

}