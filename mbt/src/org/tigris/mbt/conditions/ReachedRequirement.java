package org.tigris.mbt.conditions;

import java.util.Arrays;
import java.util.Collection;

import org.tigris.mbt.FiniteStateMachine;

public class ReachedRequirement implements StopCondition {
	
	private FiniteStateMachine machine;
	private Collection requirements;

	public ReachedRequirement(FiniteStateMachine machine, String requirements)
	{
		this.machine = machine;
		this.requirements =  Arrays.asList(requirements.split(","));
	}
	
	public boolean isFulfilled() {
		return machine.getCoveredRequirements().containsAll(this.requirements);
	}

}