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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.RepositoryTypeResolver;
import be.nabu.eai.repository.api.Repository;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.repositories.DynamicScript;
import be.nabu.glue.core.repositories.DynamicScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.services.GlueService;
import be.nabu.glue.services.ServiceMethodProvider;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.MultipleDefinedTypeResolver;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.utils.io.IOUtils;

public class GlueServiceArtifact implements DefinedService {

	private GlueService service;
	private String id;
	private ScriptRepository scriptRepository;
	private DynamicScript script;
	private ExecutionEnvironment executionEnvironment;
	private ResourceContainer<?> directory;
	private ResourceContainer<?> resourceDirectory;
	private GlueParserProvider provider;
	private Repository repository;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public GlueServiceArtifact(String id, ResourceContainer<?> directory, Repository repository) throws IOException {
		this(id, directory, repository, false);
	}
	
	public GlueServiceArtifact(String id, ResourceContainer<?> directory, Repository repository, boolean sandboxed) throws IOException {
		this(id, directory, repository, new AllowTargetSwitchProvider() {
			@Override
			public boolean allowTargetSwitch(Service service, ExecutionContext context, ComplexContent input) {
				return !(service instanceof GlueServiceArtifact);
			}
		}, sandboxed);
	}
	
	public GlueServiceArtifact(String id, ResourceContainer<?> directory, Repository repository, AllowTargetSwitchProvider allowTargetSwitchProvider) throws IOException {
		this(id, directory, repository, allowTargetSwitchProvider, false);
	}
	
	public GlueServiceArtifact(String id, ResourceContainer<?> directory, Repository repository, AllowTargetSwitchProvider allowTargetSwitchProvider, boolean sandboxed) throws IOException {
		this.directory = directory;
		this.repository = repository;
		provider = sandboxed ? new GlueParserProvider() : new GlueParserProvider(new ServiceMethodProvider(repository, repository, new IntelligentServiceRunner(repository.getServiceRunner(), allowTargetSwitchProvider)));
		if (sandboxed) {
			provider.setSandboxed(true);
		}
		scriptRepository = new DynamicScriptRepository(provider);
		resourceDirectory = (ResourceContainer<?>) directory.getChild(EAIResourceRepository.PRIVATE);
		if (resourceDirectory == null && directory instanceof ManageableContainer) {
			resourceDirectory = (ResourceContainer<?>) ((ManageableContainer<?>) directory).create(EAIResourceRepository.PRIVATE, Resource.CONTENT_TYPE_DIRECTORY);
		}
		script = new DynamicScript(
			id.indexOf('.') > 0 ? id.substring(0, id.lastIndexOf('.')) : null,
			id.indexOf('.') > 0 ? id.substring(id.lastIndexOf('.') + 1) : id,
			scriptRepository,
			Charset.defaultCharset(),
			(ResourceContainer<?>) resourceDirectory
		);
		executionEnvironment = new SimpleExecutionEnvironment(repository.getName());
		if (sandboxed) {
			executionEnvironment.getParameters().put("sandboxed", "true");
		}
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	protected GlueService getService() {
		if (service == null) {
			synchronized(this) {
				if (service == null) {
					service = new GlueService(script, executionEnvironment, null);
					MultipleDefinedTypeResolver typeResolver = new MultipleDefinedTypeResolver(Arrays.asList(
						new RepositoryTypeResolver(getRepository()),
						DefinedTypeResolverFactory.getInstance().getResolver(),
						// this works as a fallback, if no one else could resolve it, we don't want an error which would block the entire script
						// instead we want to use a java object
						new DefinedTypeResolver() {
							@Override
							public DefinedType resolve(String id) {
								logger.warn("Could not resolve type '" + id + "', falling back to Object");
								return DefinedTypeResolverFactory.getInstance().getResolver().resolve(Object.class.getName());
							}
						}
					));
					service.setTypeResolver(typeResolver);
					String iface = getInterface();
					service.setImplementedInterface(iface == null ? null : DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(iface));
				}
			}
		}
		return service;
	}

	public String getInterface() {
		String iface = null;
		try {
			iface = script.getRoot() == null || script.getRoot().getContext() == null || script.getRoot().getContext().getAnnotations() == null 
				? null
				: script.getRoot().getContext().getAnnotations().get("interface");
		}
		catch (Exception e) {
			// can not get iface, it's ok
		}
		return iface;
	}
	
	protected void reset() {
		service = null;
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		return getService().getServiceInterface();
	}

	@Override
	public ServiceInstance newInstance() {
		return getService().newInstance();
	}

	@Override
	public Set<String> getReferences() {
		return getService().getReferences();
	}
	
	public String getContent() {
		InputStream content = script.getSource();
		if (content != null) {
			try {
				try {
					return new String(IOUtils.toBytes(IOUtils.wrap(content)), Charset.defaultCharset());
				}
				finally {
					content.close();
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}
	
	public void setContent(String content) throws IOException, ParseException {
		script.setContent(content);
		reset();
	}

	public DynamicScript getScript() {
		return script;
	}

	public ResourceContainer<?> getDirectory() {
		return directory;
	}
	
	public ResourceContainer<?> getResourceDirectory() {
		return resourceDirectory;
	}

	public ExecutionEnvironment getExecutionEnvironment() {
		return executionEnvironment;
	}
	
	public GlueParserProvider getParserProvider() {
		return provider;
	}

	public Repository getRepository() {
		return repository;
	}

	public ScriptRepository getScriptRepository() {
		return scriptRepository;
	}
	
}
