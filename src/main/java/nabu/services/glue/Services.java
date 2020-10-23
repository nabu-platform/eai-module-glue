package nabu.services.glue;

import java.io.IOException;
import java.text.ParseException;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.module.services.glue.GlueServiceArtifact;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;

@WebService
public class Services {
	
	private ExecutionContext context;
	
	@SuppressWarnings("unchecked")
	@WebResult(name = "output")
	public Object execute(@WebParam(name = "name") String name, @WebParam(name = "script") String script, @WebParam(name = "context") Object pipeline) throws IOException, ServiceException, ParseException {
		if (script == null) {
			return null;
		}
		if (name == null) {
			name = "anonymous";
		}
		else {
			name = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.DASH.apply(name));
		}
		GlueServiceArtifact glueService = new GlueServiceArtifact("nabu.services.glue.dynamic." + name, new MemoryDirectory(), EAIResourceRepository.getInstance());
		glueService.setContent(script);
		ServiceRuntime runtime = new ServiceRuntime(glueService, context);
		ComplexContent input;
		if (pipeline instanceof ComplexContent) {
			input = (ComplexContent) pipeline;
		}
		else if (pipeline != null) {
			input = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(pipeline);
		}
		else {
			input = glueService.getServiceInterface().getInputDefinition().newInstance();
		}
		return runtime.run(input);
	}
	
}
