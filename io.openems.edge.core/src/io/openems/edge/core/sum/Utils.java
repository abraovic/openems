package io.openems.edge.core.sum;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.AbstractReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.StateCollectorChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timedata.influxdb.channel.IntegerTimedataChannel;

public class Utils {
	public static Stream<? extends AbstractReadChannel<?>> initializeChannels(Sum c) {
		return Stream.of( //
				Arrays.stream(OpenemsComponent.ChannelId.values()).map(channelId -> {
					switch (channelId) {
					case STATE:
						return new StateCollectorChannel(c, channelId);
					}
					return null;
				}), Arrays.stream(Sum.ChannelId.values()).map(channelId -> {
					switch (channelId) {
					case ESS_SOC:
					case ESS_ACTIVE_POWER:
					case ESS_ACTIVE_CHARGE_ENERGY:
					case ESS_ACTIVE_DISCHARGE_ENERGY:
					case GRID_ACTIVE_POWER:
					case GRID_MAX_ACTIVE_POWER:
					case GRID_MIN_ACTIVE_POWER:
					case PRODUCTION_ACTIVE_POWER:
					case PRODUCTION_MAX_ACTIVE_POWER:
					case PRODUCTION_AC_ACTIVE_POWER:
					case PRODUCTION_MAX_AC_ACTIVE_POWER:
					case PRODUCTION_DC_ACTUAL_POWER:
					case PRODUCTION_MAX_DC_ACTUAL_POWER:
					case CONSUMPTION_ACTIVE_POWER:
					case CONSUMPTION_MAX_ACTIVE_POWER:
					case ESS_ACTIVE_CHARGE_ENERGY_DAILY:
					case ESS_ACTIVE_CHARGE_ENERGY_MONTHLY:
					case ESS_ACTIVE_CHARGE_ENERGY_YEARLY:
					case ESS_ACTIVE_DISCHARGE_ENERGY_DAILY:
					case ESS_ACTIVE_DISCHARGE_ENERGY_MONTHLY:
					case ESS_ACTIVE_DISCHARGE_ENERGY_YEARLY:
						return new IntegerReadChannel(c, channelId, 0);
					case ESS_ACTIVE_CHARGE_ENERGY_LAST_DAY:
						Calendar cal = Calendar.getInstance();
						cal.setTime(new Date(System.currentTimeMillis()));
						// cal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH) + 1);
						// cal.clear(Calendar.HOUR_OF_DAY);
						// cal.clear(Calendar.MINUTE);
						// cal.clear(Calendar.SECOND);
						// cal.clear(Calendar.MILLISECOND);
						return new IntegerTimedataChannel(c, channelId,
								new ChannelAddress("_sum", "EssActiveChargeEnergy"), cal.getTime(), 60000, 60000);
					// new ChannelAddress("_sum", "EssActiveChargeEnergy"), cal.getTime(), 86400000,
					// 86400000);
					}
					return null;
				}) //
		).flatMap(channel -> channel);
	}
}
