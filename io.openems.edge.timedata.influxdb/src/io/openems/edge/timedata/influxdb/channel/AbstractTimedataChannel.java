package io.openems.edge.timedata.influxdb.channel;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.AbstractReadChannel;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.doc.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timedata.api.Timedata;

public class AbstractTimedataChannel<T> extends AbstractReadChannel<T> implements Channel<T> {

	private final ChannelAddress source;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
	private volatile List<Timedata> timedatas = new CopyOnWriteArrayList<>();

	public AbstractTimedataChannel(OpenemsType type, OpenemsComponent component, ChannelId channelId,
			ChannelAddress sourceChannel, Date startInRealtime, long period, long delta) {
		super(type, component, channelId);
		this.source = sourceChannel;
		scheduleUpdates(startInRealtime, period, delta);
	}

	public AbstractTimedataChannel(OpenemsType type, OpenemsComponent component, ChannelId channelId,
			ChannelAddress sourceChannel, Date startInRealtime, long period, long delta, T initialValue) {
		super(type, component, channelId, initialValue);
		this.source = sourceChannel;
		scheduleUpdates(startInRealtime, period, delta);
	}

	private void scheduleUpdates(Date startInRealtime, long period, long delta) {
		class Updater extends TimerTask {

			public Updater() {

			}

			public void run() {
				update(delta);
			}
		}

		Timer t = new Timer();
		t.schedule(new Updater(), startInRealtime, period);
		System.out.println("======================= 0 (" + source + ")");
	}

	private void update(long delta) {
		System.out.println("======================= 1 (" + source + ")");
		if (timedatas.size() != 0) {
			System.out.println("======================= 2 (" + source + ")");
			for (Timedata t : timedatas) {
				Optional<Object> val = t.getHistoricChannelValue(source,
						ZonedDateTime.now().minus(delta, ChronoUnit.MILLIS));
				if (val.isPresent()) {
					this.setNextValue(val.get());
					break;
				}
			}
		}
	}
}
