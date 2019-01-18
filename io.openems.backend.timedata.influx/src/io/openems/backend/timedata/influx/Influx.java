package io.openems.backend.timedata.influx;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ObjectArrays;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.backend.metadata.api.Edge;
import io.openems.backend.metadata.api.Metadata;
import io.openems.backend.timedata.api.Timedata;
import io.openems.backend.timedata.core.EdgeCache;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.shared.influxdb.InfluxConnector;
import io.openems.shared.influxdb.InfluxConstants;

@Designate(ocd = Config.class, factory = false)
@Component(name = "Timedata.InfluxDB", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Influx extends AbstractOpenemsBackendComponent implements Timedata {

	private static final String TMP_MINI_MEASUREMENT = "minies";
	private static final Pattern NAME_NUMBER_PATTERN = Pattern.compile("[^0-9]+([0-9]+)$");

	private final Logger log = LoggerFactory.getLogger(Influx.class);
	private final Map<String, EdgeCache> edgeCacheMap = new HashMap<>();

	private InfluxConnector influxConnector = null;

	public Influx() {
		super("Timedata.InfluxDB");
	}

	@Reference
	protected volatile Metadata metadata;

	@Activate
	void activate(Config config) throws OpenemsException {
		this.logInfo(this.log, "Activate [url=" + config.url() + ";port=" + config.port() + ";database="
				+ config.database() + ";username=" + config.username() + ";password="
				+ (config.password() != null ? "ok" : "NOT_SET") + ";measurement=" + config.measurement() + "]");

		this.influxConnector = new InfluxConnector(config.url(), config.port(), config.username(), config.password(),
				config.database());
	}

	@Deactivate
	void deactivate() {
		this.logInfo(this.log, "Deactivate");
		if (this.influxConnector != null) {
			this.influxConnector.deactivate();
		}
	}

	@Override
	public void write(String edgeId, TreeBasedTable<Long, ChannelAddress, JsonElement> data) throws OpenemsException {
		// parse the numeric EdgeId
		int influxEdgeId = Influx.parseNumberFromName(edgeId);

		// get existing or create new DeviceCache
		EdgeCache edgeCache = this.edgeCacheMap.get(edgeId);
		if (edgeCache == null) {
			edgeCache = new EdgeCache();
			this.edgeCacheMap.put(edgeId, edgeCache);
		}

		/*
		 * Prepare data table. Takes entries starting with eldest timestamp (ascending
		 * order)
		 */
		for (Entry<Long, Map<ChannelAddress, JsonElement>> entry : data.rowMap().entrySet()) {
			Long timestamp = entry.getKey();

			// Check if cache is valid (it is not elder than 5 minutes compared to this
			// timestamp)
			long cacheTimestamp = edgeCache.getTimestamp();
			if (timestamp < cacheTimestamp) {
				// incoming data is older than cache -> do not apply cache

			} else {
				// incoming data is more recent than cache
				if (timestamp < cacheTimestamp + 5 * 60 * 1000) {
					// cache is valid (not elder than 5 minutes)
					for (Entry<ChannelAddress, JsonElement> cacheEntry : edgeCache.getChannelCacheEntries()
							.entrySet()) {
						ChannelAddress channel = cacheEntry.getKey();
						// check if there is a current value for this timestamp + channel
						JsonElement existingValue = data.get(timestamp, channel);
						if (existingValue == null) {
							// if not -> add cache data to write data
							data.put(timestamp, channel, cacheEntry.getValue());
						}
					}
				} else {
					// cache is not anymore valid (elder than 5 minutes)
					if (cacheTimestamp != 0L) {
						this.logInfo(this.log, "Edge [" + edgeId + "]: invalidate cache for influxId [" + influxEdgeId
								+ "]. This timestamp [" + timestamp + "]. Cache timestamp [" + cacheTimestamp + "]");
					}
					// clear cache
					edgeCache.clear();
				}

				// update cache
				edgeCache.setTimestamp(timestamp);
				for (Entry<ChannelAddress, JsonElement> channelEntry : entry.getValue().entrySet()) {
					edgeCache.putToChannelCache(channelEntry.getKey(), channelEntry.getValue());
				}
			}
		}

		// Write data to default location
		this.writeData(influxEdgeId, data);

		// Hook to continue writing data to old Mini monitoring
		this.writeDataToOldMiniMonitoring(edgeId, influxEdgeId, data);
	}

	/**
	 * Actually writes the data to InfluxDB.
	 * 
	 * @param influxEdgeId the unique, numeric identifier of the Edge
	 * @param data         the data
	 */
	private void writeData(int influxEdgeId, TreeBasedTable<Long, ChannelAddress, JsonElement> data) {
		InfluxDB influxDb = this.influxConnector.getConnection();

		BatchPoints batchPoints = BatchPoints.database(this.influxConnector.getDatabase()) //
				.tag(InfluxConstants.TAG, String.valueOf(influxEdgeId)) //
				.build();

		for (Entry<Long, Map<ChannelAddress, JsonElement>> entry : data.rowMap().entrySet()) {
			Long timestamp = entry.getKey();
			// this builds an InfluxDB record ("point") for a given timestamp
			Builder builder = Point.measurement(InfluxConnector.MEASUREMENT).time(timestamp, TimeUnit.MILLISECONDS);
			for (Entry<ChannelAddress, JsonElement> channelEntry : entry.getValue().entrySet()) {
				Influx.addValue(builder, channelEntry.getKey().toString(), channelEntry.getValue());
			}
			batchPoints.point(builder.build());
		}

		// write to DB
		try {
			influxDb.write(batchPoints);
		} catch (InfluxDBIOException e) {
			this.logError(this.log, "Unable to write data: " + e.getMessage());
		}
	}

	public static Integer parseNumberFromName(String name) throws OpenemsException {
		try {
			Matcher matcher = NAME_NUMBER_PATTERN.matcher(name);
			if (matcher.find()) {
				String nameNumberString = matcher.group(1);
				return Integer.parseInt(nameNumberString);
			}
		} catch (NullPointerException e) {
			/* ignore */
		}
		throw new OpenemsException("Unable to parse number from name [" + name + "]");
	}

	@Override
	public TreeBasedTable<ZonedDateTime, ChannelAddress, JsonElement> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, int resolution)
			throws OpenemsNamedException {
		// parse the numeric EdgeId
		Optional<Integer> influxEdgeId = Optional.of(Influx.parseNumberFromName(edgeId));

		return this.influxConnector.queryHistoricData(influxEdgeId, fromDate, toDate, channels, resolution);
	}

	/**
	 * Adds the value in the correct data format for InfluxDB.
	 *
	 * @param builder the Influx PointBuilder
	 * @param field   the field name
	 * @param element the value
	 * @return
	 */
	private static void addValue(Builder builder, String field, JsonElement element) {
		if (element == null || element.isJsonNull()) {
			// do not add
			return;
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive value = element.getAsJsonPrimitive();
			if (value.isNumber()) {
				try {
					builder.addField(field, Long.parseLong(value.toString()));
				} catch (NumberFormatException e1) {
					try {
						builder.addField(field, Double.parseDouble(value.toString()));
					} catch (NumberFormatException e2) {
						builder.addField(field, value.getAsNumber());
					}
				}
			} else if (value.isBoolean()) {
				builder.addField(field, value.getAsBoolean());
			} else if (value.isString()) {
				builder.addField(field, value.getAsString());
			} else {
				builder.addField(field, value.toString());
			}
		} else {
			builder.addField(field, element.toString());
		}
	}

	/**
	 * Writes data to old database for old Mini monitoring.
	 * 
	 * </p>
	 * XXX remove after full migration
	 *
	 * @param edgeId   the Edge-ID
	 * @param influxId the Influx-Edge-ID
	 * @param data     the received data
	 * @throws OpenemsException on error
	 */
	private void writeDataToOldMiniMonitoring(String edgeId, int influxId,
			TreeBasedTable<Long, ChannelAddress, JsonElement> data) throws OpenemsException {
		Edge edge = this.metadata.getEdgeOrError(edgeId);
		if (!edge.getProducttype().equals("MiniES 3-3")) {
			return;
		}

		InfluxDB influxDb = this.influxConnector.getConnection();

		BatchPoints batchPoints = BatchPoints.database(this.influxConnector.getDatabase()) //
				.tag(InfluxConstants.TAG, String.valueOf(influxId)) //
				.build();

		for (Entry<Long, Map<ChannelAddress, JsonElement>> entry : data.rowMap().entrySet()) {
			Long timestamp = entry.getKey();
			Builder builder = Point.measurement(TMP_MINI_MEASUREMENT).time(timestamp, TimeUnit.MILLISECONDS);

			Map<String, Object> fields = new HashMap<>();

			for (Entry<ChannelAddress, JsonElement> valueEntry : entry.getValue().entrySet()) {
				String channel = valueEntry.getKey().toString();
				JsonElement element = valueEntry.getValue();
				if (!element.isJsonPrimitive()) {
					continue;
				}
				JsonPrimitive jValue = element.getAsJsonPrimitive();
				if (!jValue.isNumber()) {
					continue;
				}
				long value = jValue.getAsNumber().longValue();

				// convert channel ids to old identifiers
				if (channel.equals("ess0/Soc")) {
					fields.put("Stack_SOC", value);
					edge.setSoc((int) value);
				} else if (channel.equals("meter0/ActivePower")) {
					fields.put("PCS_Grid_Power_Total", value * -1);
				} else if (channel.equals("meter1/ActivePower")) {
					fields.put("PCS_PV_Power_Total", value);
				} else if (channel.equals("meter2/ActivePower")) {
					fields.put("PCS_Load_Power_Total", value);
				}

				// from here value needs to be divided by 10 for backwards compatibility
				value = value / 10;
				if (channel.equals("meter2/Energy")) {
					fields.put("PCS_Summary_Consumption_Accumulative_cor", value);
					fields.put("PCS_Summary_Consumption_Accumulative", value);
				} else if (channel.equals("meter0/BuyFromGridEnergy")) {
					fields.put("PCS_Summary_Grid_Buy_Accumulative_cor", value);
					fields.put("PCS_Summary_Grid_Buy_Accumulative", value);
				} else if (channel.equals("meter0/SellToGridEnergy")) {
					fields.put("PCS_Summary_Grid_Sell_Accumulative_cor", value);
					fields.put("PCS_Summary_Grid_Sell_Accumulative", value);
				} else if (channel.equals("meter1/EnergyL1")) {
					fields.put("PCS_Summary_PV_Accumulative_cor", value);
					fields.put("PCS_Summary_PV_Accumulative", value);
				}
			}

			if (fields.size() > 0) {
				builder.fields(fields);
				batchPoints.point(builder.build());
			}
		}

		// write to DB
		influxDb.write(batchPoints);
	}

	@Override
	public Optional<JsonElement> getChannelValue(String edgeId, ChannelAddress address) {
		EdgeCache cache = this.edgeCacheMap.get(edgeId);
		if (cache != null) {
			Optional<Edge> edgeOpt = this.metadata.getEdge(edgeId);
			if (!edgeOpt.isPresent()) {
				return cache.getChannelValue(address);
			}
			Edge edge = edgeOpt.get();
			ChannelFormula[] compatibility = this.getCompatibilityFormula(edge, address);
			if (compatibility.length == 0) {
				return cache.getChannelValue(address);
			}
			// handle compatibility with elder OpenEMS Edge version
			return this.getCompatibilityChannelValue(compatibility, cache);
		} else {
			return Optional.empty();
		}
	}

	/**
	 * Handles compatibility with elder OpenEMS Edge version, e.g. calculate the
	 * '_sum' Channels.
	 * 
	 * @param compatibility the formula to calculate the channel value
	 * @param cache         the EdgeCache
	 * @return the value as an Optional
	 */
	@Deprecated
	private Optional<JsonElement> getCompatibilityChannelValue(ChannelFormula[] compatibility, EdgeCache cache) {
		int value = 0;
		for (ChannelFormula formula : compatibility) {
			switch (formula.getFunction()) {
			case PLUS:
				value += formula.getValue(cache);
			}
		}
		return Optional.of(new JsonPrimitive(value));
	}

	/**
	 * Gets the formula to calculate a '_sum' Channel value.
	 * 
	 * @param edge    the Edge
	 * @param address the ChannelAddress
	 * @return the formula to calculate the channel value
	 */
	@Deprecated
	private ChannelFormula[] getCompatibilityFormula(Edge edge, ChannelAddress address) {
		if (address.getComponentId().equals("_sum")) {
			switch (address.getChannelId()) {
			case "EssSoc":
				return new ChannelFormula[] { //
						new ChannelFormula(Function.PLUS, new ChannelAddress("ess0", "Soc")) };

			case "EssActivePower":
				switch (edge.getProducttype()) {
				case "Pro 9-12":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, new ChannelAddress("ess0", "ActivePowerL1")), //
							new ChannelFormula(Function.PLUS, new ChannelAddress("ess0", "ActivePowerL2")), //
							new ChannelFormula(Function.PLUS, new ChannelAddress("ess0", "ActivePowerL3")) //
					};
				default:
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, new ChannelAddress("ess0", "ActivePower")) //
					};
				}

			case "EssMaxApparentPower":
				switch (edge.getProducttype()) {
				case "Pro 9-12":
				case "PRO Hybrid 9-10":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 9_000), //
					};
				case "Pro Hybrid 10-Serie":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 10_000), //
					};
				case "MiniES 3-3":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 3_000), //
					};
				case "Commercial 50-Serie":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 50_000), //
					};
				case "COMMERCIAL 40-45":
				case "INDUSTRIAL":
				case "":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 40_000), //
					};
				default:
					this.logWarn(this.log,
							"No formula for EssMaxApparentPower [" + edge.getId() + "|" + edge.getProducttype() + "]");
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, 40_000) //
					};
				}

			case "GridActivePower":
				return new ChannelFormula[] { //
						new ChannelFormula(Function.PLUS, new ChannelAddress("meter0", "ActivePower")) //
				};

			case "GridMinActivePower":
				return new ChannelFormula[] { //
						new ChannelFormula(Function.PLUS, new ChannelAddress("meter0", "minActivePower")) //
				};

			case "GridMaxActivePower":
				return new ChannelFormula[] { //
						new ChannelFormula(Function.PLUS, new ChannelAddress("meter0", "maxActivePower")) //
				};

			case "ProductionActivePower":
				return ObjectArrays.concat(//
						this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "ProductionAcActivePower")), //
						this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "ProductionDcActualPower")), //
						ChannelFormula.class);

			case "ProductionAcActivePower":
				switch (edge.getProducttype()) {
				case "Pro 9-12":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, new ChannelAddress("meter1", "ActivePowerL1")), //
							new ChannelFormula(Function.PLUS, new ChannelAddress("meter1", "ActivePowerL2")), //
							new ChannelFormula(Function.PLUS, new ChannelAddress("meter1", "ActivePowerL3")) //
					};
				default:
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, new ChannelAddress("meter1", "ActivePower")) //
					};
				}

			case "ProductionDcActualPower":
				switch (edge.getProducttype()) {
				case "COMMERCIAL 40-45":
					return new ChannelFormula[] { //
							new ChannelFormula(Function.PLUS, new ChannelAddress("charger0", "ActualPower")) //
					};
				default:
					return new ChannelFormula[] { //
					};
				}

			case "ProductionMaxActivePower":
				return new ChannelFormula[] { //
						new ChannelFormula(Function.PLUS, new ChannelAddress("meter1", "maxActivePower")) //
				};

			case "ConsumptionActivePower":
				return ObjectArrays.concat(//
						ObjectArrays.concat(//
								this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "EssActivePower")), //
								this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "GridActivePower")), //
								ChannelFormula.class),
						this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "ProductionAcActivePower")), //
						ChannelFormula.class);

			case "ConsumptionMaxActivePower":
				return ObjectArrays.concat(//
						ObjectArrays.concat(//
								this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "EssMaxApparentPower")), //
								this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "GridMaxActivePower")), //
								ChannelFormula.class),
						this.getCompatibilityFormula(edge, new ChannelAddress("_sum", "ProductionMaxActivePower")), //
						ChannelFormula.class);
			}
		}
		return new ChannelFormula[0];
	}

}
