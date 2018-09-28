package io.openems.edge.common.channel.merger;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.doc.ChannelId;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public class SumInteger<C extends OpenemsComponent> extends ChannelsFunction<C, Integer> {

	private List<String> negatives = new ArrayList<String>();

	public SumInteger(OpenemsComponent parent, ChannelId targetChannelId, ChannelId sourceChannelId) {
		super(parent, targetChannelId, sourceChannelId);
	}

	protected double calculate() throws NoSuchElementException {
		double sum = 0;
		for (String k : this.valueMap.keySet()) {
			Value<Integer> v = this.valueMap.get(k);
			if (v.asOptional().isPresent()) {
				if (negatives.contains(k)) {
					sum -= v.get();
				} else {
					sum += v.get();
				}
			}
		}
		return sum;
	}

	// protected boolean isDoubleCompatible() {
	//
	// }

	public void addComponentAsNegative(C component) {
		super.addComponent(component);
		negatives.add(component.id());
	}

	public void addChannelAsNegative(Channel<Integer> channel) {
		super.addChannel(channel);
		negatives.add(channel.address().toString());
	}
}
