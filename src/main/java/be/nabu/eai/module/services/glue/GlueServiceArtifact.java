package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Set;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.services.GlueService;
import be.nabu.glue.services.ServiceMethodProvider;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.utils.io.IOUtils;

public class GlueServiceArtifact implements DefinedService {

	private GlueService service;
	private String id;
	private ScriptRepository scriptRepository;
	private DynamicScript script;
	private ExecutionEnvironment executionEnvironment;
	private ResourceContainer<?> directory;

	public GlueServiceArtifact(String id, ResourceContainer<?> directory, Repository repository) throws IOException {
		this.directory = directory;
		// TODO: default service run uses local service runtime instead of an actual runner, this should probably be updated
		scriptRepository = new DynamicScriptRepository(new GlueParserProvider(new ServiceMethodProvider(repository, repository)));
		Resource child = directory.getChild(EAIResourceRepository.PRIVATE);
		if (child == null) {
			child = ((ManageableContainer<?>) directory).create(EAIResourceRepository.PRIVATE, Resource.CONTENT_TYPE_DIRECTORY);
		}
		script = new DynamicScript(
			id.indexOf('.') > 0 ? id.substring(0, id.lastIndexOf('.')) : null,
			id.indexOf('.') > 0 ? id.substring(id.lastIndexOf('.') + 1) : id,
			scriptRepository,
			Charset.defaultCharset(),
			(ResourceContainer<?>) child
		);
		executionEnvironment = new SimpleExecutionEnvironment("local");
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	private GlueService getService() {
		if (service == null) {
			synchronized(this) {
				if (service == null) {
					service = new GlueService(script, executionEnvironment, null);
				}
			}
		}
		return service;
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
		service = null;
	}

	public DynamicScript getScript() {
		return script;
	}

	public ResourceContainer<?> getDirectory() {
		return directory;
	}
	
}
