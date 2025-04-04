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

package nabu.services.glue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.module.services.glue.AllowTargetSwitchProvider;
import be.nabu.eai.module.services.glue.GlueServiceArtifact;
import be.nabu.eai.module.services.glue.IntelligentServiceRunner;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.executors.EvaluateExecutor;
import be.nabu.glue.core.impl.parsers.GlueParser;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.repositories.DynamicScriptRepository;
import be.nabu.glue.impl.SimpleExecutionContext;
import be.nabu.glue.services.CombinedExecutionContext;
import be.nabu.glue.services.CombinedExecutionContextImpl;
import be.nabu.glue.services.ServiceMethodProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.java.BeanInstance;

@WebService
public class Services {
	
	private ExecutionContext context;
	private static GlueParser parser;
	
	public String toSql(@WebParam(name = "rule") String rule, @WebParam(name = "tableName") String tableName) throws ParseException {
		return GlueUtils.toSql(rule, tableName);
	}
	
	@SuppressWarnings("unchecked")
	@WebResult(name = "output")
	public Object execute(@WebParam(name = "name") String name, @WebParam(name = "script") String script, @WebParam(name = "context") Object pipeline, @WebParam(name = "sandboxed") Boolean sandboxed) throws IOException, ServiceException, ParseException {
		if (script == null) {
			return null;
		}
		if (name == null) {
			name = "anonymous";
		}
		else {
			name = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.DASH.apply(name));
		}
		GlueServiceArtifact glueService = new GlueServiceArtifact("nabu.services.glue.dynamic." + name, new MemoryDirectory(), EAIResourceRepository.getInstance(), sandboxed != null && sandboxed);
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
	
	public Object evaluate(@WebParam(name = "rule") String rule, @WebParam(name = "context") List<Object> pipeline, @WebParam(name = "returnPipeline") Boolean returnPipeline) throws IOException, ParseException, ExecutionException {
		if (parser == null || EAIResourceRepository.isDevelopment()) {
			EAIResourceRepository repository = EAIResourceRepository.getInstance();
			GlueParserProvider provider = new GlueParserProvider(new ServiceMethodProvider(repository, repository, new IntelligentServiceRunner(repository.getServiceRunner(), new AllowTargetSwitchProvider() {
				@Override
				public boolean allowTargetSwitch(Service service, ExecutionContext context, ComplexContent input) {
					return !(service instanceof GlueServiceArtifact);
				}
			})));
			DynamicScriptRepository dynamicScriptRepository = new DynamicScriptRepository(provider);
			parser = new GlueParser(dynamicScriptRepository, provider.newOperationProvider(dynamicScriptRepository));
		}
		// we choose a parameter unlikely enough to be used naturally but "decent" enough to document as a feature for very special cases!
		ExecutorGroup parsed = parser.parse(new StringReader(rule));
		ExecutionEnvironment executionEnvironment = new ExecutionEnvironment() {
			@Override
			public Map<String, String> getParameters() {
				HashMap<String, String> map = new HashMap<String, String>();
				map.put(EvaluateExecutor.DEFAULT_VARIABLE_NAME_PARAMETER, "$result");
				return map;
			}
			@Override
			public String getName() {
				return "default";
			}
		};
//		SimpleExecutionContext executionContext = new SimpleExecutionContext(executionEnvironment, new LabelEvaluator() {
//			@Override
//			public boolean shouldExecute(String label, ExecutionEnvironment environment) {
//				return true;
//			}
//		}, false);
		
		
		// use the combined context rather than the execution context so we can inherit state
		CombinedExecutionContext combinedExecutionContext;
		ExecutionContext existingServiceContext = ServiceRuntime.getRuntime().getExecutionContext();
		if (existingServiceContext instanceof CombinedExecutionContext) {
			combinedExecutionContext = (CombinedExecutionContext) existingServiceContext;
		}
		else {
			combinedExecutionContext = new CombinedExecutionContextImpl(existingServiceContext, executionEnvironment, new LabelEvaluator() {
				@Override
				public boolean shouldExecute(String label, ExecutionEnvironment environment) {
					return true;
				}
			});
		}
		
		Map<String, Object> input = new HashMap<String, Object>();
		if (pipeline != null) {
			for (Object single : pipeline) {
				input.putAll(toPipeline(single));
			}
		}
		ScriptRuntime runtime = new ScriptRuntime(new Script() {
			@Override
			public Iterator<String> iterator() {
				return null;
			}
			@Override
			public ScriptRepository getRepository() {
				return parser.getRepository();
			}
			@Override
			public String getNamespace() {
				return null;
			}
			@Override
			public String getName() {
				return "$rule";
			}

			@Override
			public ExecutorGroup getRoot() throws IOException, ParseException {
				return parsed;
			}
			@Override
			public Charset getCharset() {
				return Charset.defaultCharset();
			}
			@Override
			public Parser getParser() {
				return parser;
			}
			@Override
			public InputStream getSource() throws IOException {
				return null;
			}
			@Override
			public InputStream getResource(String name) throws IOException {
				return null;
			}
		}, combinedExecutionContext, input);
		runtime.run();
		// let's return the last evaluation you did that was not particularly assigned to something
		return returnPipeline != null && returnPipeline ? combinedExecutionContext.getPipeline() : combinedExecutionContext.getPipeline().get("$result");
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Object> toPipeline(Object stuff) {
		if (stuff instanceof Map) {
			return (Map<String, Object>) stuff;
		}
		else if (stuff instanceof BeanInstance && ((BeanInstance<?>) stuff).getUnwrapped() instanceof Map) {
			return (Map<String, Object>) ((BeanInstance<?>) stuff).getUnwrapped();
		}
		Map<String, Object> pipeline = new HashMap<String, Object>();
		if (stuff != null) {
			if (!(stuff instanceof ComplexContent)) {
				stuff = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(stuff);
				if (stuff == null) {
					throw new IllegalArgumentException("Invalid pipeline object passed in");
				}
			}
			ComplexContent content = (ComplexContent) stuff;
			for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
				pipeline.put(child.getName(), content.get(child.getName()));
			}
		}
		return pipeline;
	}
}
