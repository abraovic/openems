package io.openems.edge.timedata.influxdb.channel;

import java.util.Date;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.doc.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;

public class IntegerTimedataChannel extends AbstractTimedataChannel<Integer> {

	public IntegerTimedataChannel(OpenemsComponent component, ChannelId channelId, ChannelAddress sourceChannel,
			Date startInRealtime, long period, long delta) {
		super(OpenemsType.INTEGER, component, channelId, sourceChannel, startInRealtime, period, delta);
	}

	public IntegerTimedataChannel(OpenemsComponent component, ChannelId channelId, ChannelAddress sourceChannel,
			Date startInRealtime, long period, long delta, Integer initialValue) {
		super(OpenemsType.INTEGER, component, channelId, sourceChannel, startInRealtime, period, delta, initialValue);
	}
}
