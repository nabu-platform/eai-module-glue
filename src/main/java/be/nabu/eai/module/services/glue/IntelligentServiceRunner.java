package be.nabu.eai.module.services.glue;

import java.util.concurrent.Future;

import be.nabu.eai.server.RemoteServer;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunnableObserver;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.types.api.ComplexContent;

public class IntelligentServiceRunner implements ServiceRunner {

	private ServiceRunner defaultRunner;
	private AllowTargetSwitchProvider allowTargetSwitchProvider;

	public IntelligentServiceRunner(ServiceRunner defaultRunner, AllowTargetSwitchProvider allowTargetSwitchProvider) {
		this.defaultRunner = defaultRunner;
		this.allowTargetSwitchProvider = allowTargetSwitchProvider;
	}
	
	@Override
	public Future<ServiceResult> run(Service service, ExecutionContext context, ComplexContent input, ServiceRunnableObserver... observers) {
		// if the default runner is a remote runner, we may not want to run remotely in some specific scenarios
		// for example if we are trying to pass along a non-serializable object (e.g. a webdriver instance for local testing)
		if (defaultRunner instanceof RemoteServer && allowTargetSwitchProvider != null) {
			if (!allowTargetSwitchProvider.allowTargetSwitch(service, context, input)) {
				return new LocalRunner().run(service, context, input, observers);
			}
		}
		return defaultRunner.run(service, context, input, observers);
	}

}
