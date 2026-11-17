package be.nabu.eai.module.services.glue;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.events.RepositoryCatalogChangedEvent;
import be.nabu.eai.repository.events.RepositoryEvent;
import be.nabu.eai.repository.events.NodeEvent;
import be.nabu.glue.services.ServiceMethodCatalog;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.api.EventSubscription;

/** One catalog and one lifecycle subscription for the singleton repository. */
public final class RepositoryServiceMethodCatalog {
	private static EAIResourceRepository repository;
	private static ServiceMethodCatalog catalog;
	private static EventSubscription<RepositoryEvent, Void> subscription;
	private static EventSubscription<RepositoryCatalogChangedEvent, Void> catalogSubscription;
	private static EventSubscription<NodeEvent, Void> nodeSubscription;

	private RepositoryServiceMethodCatalog() {
	}

	public static synchronized ServiceMethodCatalog forRepository(Repository current) {
		if (current != EAIResourceRepository.getInstance()) {
			return new ServiceMethodCatalog(current);
		}
		EAIResourceRepository instance = (EAIResourceRepository) current;
		if (catalog == null || repository != instance) {
			if (subscription != null) {
				subscription.unsubscribe();
				catalogSubscription.unsubscribe();
				nodeSubscription.unsubscribe();
			}
			repository = instance;
			catalog = new ServiceMethodCatalog(instance);
			subscription = subscribeRepository(instance, catalog);
			catalogSubscription = subscribeCatalog(instance, catalog);
			nodeSubscription = subscribeNodes(instance, catalog);
		}
		return catalog;
	}

	static EventSubscription<RepositoryEvent, Void> subscribeRepository(Repository instance, ServiceMethodCatalog subscribed) {
		return instance.getEventDispatcher().subscribe(RepositoryEvent.class, new EventHandler<RepositoryEvent, Void>() {
				@Override
				public Void handle(RepositoryEvent event) {
					if (!event.isDone()) {
						subscribed.reset();
					}
					return null;
				}
			});
	}

	static EventSubscription<RepositoryCatalogChangedEvent, Void> subscribeCatalog(Repository instance, ServiceMethodCatalog subscribed) {
		return instance.getEventDispatcher().subscribe(RepositoryCatalogChangedEvent.class, new EventHandler<RepositoryCatalogChangedEvent, Void>() {
			@Override
			public Void handle(RepositoryCatalogChangedEvent event) {
				subscribed.reset();
				return null;
			}
		});
	}

	static EventSubscription<NodeEvent, Void> subscribeNodes(Repository instance, ServiceMethodCatalog subscribed) {
		return instance.getEventDispatcher().subscribe(NodeEvent.class, new EventHandler<NodeEvent, Void>() {
				@Override
				public Void handle(NodeEvent event) {
					if (!event.isDone() && event.getState() != NodeEvent.State.LOAD) {
						subscribed.reset();
					}
					return null;
				}
			});
	}
}
