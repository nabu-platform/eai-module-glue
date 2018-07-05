package be.nabu.eai.module.services.glue;

import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.types.api.ComplexContent;

public interface AllowTargetSwitchProvider {
	// we want to call this service but by default we want to run it on another target, can we do this?
	public boolean allowTargetSwitch(Service service, ExecutionContext context, ComplexContent input);
}
