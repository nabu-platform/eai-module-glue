/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.glue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import be.nabu.eai.server.Server.ServiceResultFuture;
import be.nabu.libs.services.ServiceRunnable;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunnableObserver;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.types.api.ComplexContent;

public class LocalRunner implements ServiceRunner {

	@Override
	public Future<ServiceResult> run(Service service, ExecutionContext context, ComplexContent input, ServiceRunnableObserver... observers) {
		List<ServiceRunnableObserver> allObservers = new ArrayList<ServiceRunnableObserver>(observers.length + 1);
		allObservers.addAll(Arrays.asList(observers));
		ServiceRuntime serviceRuntime = new ServiceRuntime(service, context);
		final ServiceRunnable runnable = new ServiceRunnable(serviceRuntime, input, allObservers.toArray(new ServiceRunnableObserver[allObservers.size()]));
		// in the future could actually run this async in a thread pool but for now it is assumed that all originating systems have their own thread pool
		// for example the messaging system runs in its own thread pool, as does the http server etc
		// if we were going for a centralized thread pool those systems should use the central one as well but then might start to interfere with one another
		return new ServiceResultFuture(runnable.call());
	}

}
