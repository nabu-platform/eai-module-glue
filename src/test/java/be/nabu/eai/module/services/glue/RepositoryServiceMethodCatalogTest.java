package be.nabu.eai.module.services.glue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.events.NodeEvent;
import be.nabu.eai.repository.events.RepositoryCatalogChangedEvent;
import be.nabu.eai.repository.events.RepositoryEvent;
import be.nabu.eai.repository.events.RepositoryEvent.RepositoryState;
import be.nabu.glue.services.ServiceMethodCatalog;
import be.nabu.libs.events.impl.EventDispatcherImpl;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.events.api.EventSubscription;

public class RepositoryServiceMethodCatalogTest {
	@Test
	public void completionEventsInvalidateAfterMutation() {
		EventDispatcherImpl dispatcher = new EventDispatcherImpl();
		Repository repository = (Repository) Proxy.newProxyInstance(Repository.class.getClassLoader(),
			new Class<?>[] { Repository.class }, (proxy, method, args) -> {
				if (method.getName().equals("getEventDispatcher")) {
					return dispatcher;
				}
				return null;
			});
		ServiceMethodCatalog catalog = new ServiceMethodCatalog(() -> new ArrayList<DefinedService>());
		EventSubscription<RepositoryEvent, Void> subscription = RepositoryServiceMethodCatalog.subscribeRepository(repository, catalog);
		EventSubscription<RepositoryCatalogChangedEvent, Void> changes = RepositoryServiceMethodCatalog.subscribeCatalog(repository, catalog);
		EventSubscription<NodeEvent, Void> nodes = RepositoryServiceMethodCatalog.subscribeNodes(repository, catalog);
		AtomicInteger repositoryEvents = new AtomicInteger();
		AtomicInteger doneEvents = new AtomicInteger();
		EventSubscription<RepositoryEvent, Void> observer = dispatcher.subscribe(RepositoryEvent.class, event -> {
			repositoryEvents.incrementAndGet();
			if (event.isDone()) {
				doneEvents.incrementAndGet();
			}
			return null;
		});
		try {
			Object original = catalog.getAvailableMethods();
			dispatcher.fire(new RepositoryEvent(RepositoryState.RELOAD, true), this);
			assertSame(original, catalog.getAvailableMethods());
			assertEquals(1, repositoryEvents.get());
			assertEquals(1, doneEvents.get());
			for (RepositoryState state : new RepositoryState[] { RepositoryState.LOAD, RepositoryState.UNLOAD, RepositoryState.RELOAD }) {
				dispatcher.fire(new RepositoryEvent(state, false), this);
				Object during = catalog.getAvailableMethods();
				assertNotSame(original, during);
				int before = repositoryEvents.get();
				dispatcher.fire(new RepositoryCatalogChangedEvent(), this);
				assertEquals(before, repositoryEvents.get());
				assertEquals(1, doneEvents.get());
				original = catalog.getAvailableMethods();
				assertNotSame(during, original);
			}
			// Deletion has no repository start event: its completion event alone must invalidate.
			int before = repositoryEvents.get();
			dispatcher.fire(new RepositoryCatalogChangedEvent(), this);
			assertEquals(before, repositoryEvents.get());
			assertNotSame(original, catalog.getAvailableMethods());
		}
		finally {
			subscription.unsubscribe();
			changes.unsubscribe();
			nodes.unsubscribe();
			observer.unsubscribe();
		}
	}
}
