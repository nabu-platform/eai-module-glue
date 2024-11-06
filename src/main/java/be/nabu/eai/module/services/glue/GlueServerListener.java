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

import java.io.File;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Templater;
import be.nabu.eai.server.Server;
import be.nabu.eai.server.api.ServerListener;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.core.repositories.DynamicScriptRepository;
import be.nabu.glue.core.repositories.ScannableScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.metrics.GlueMetrics;
import be.nabu.glue.services.ServiceMethodProvider;
import be.nabu.glue.utils.DynamicScript;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.file.FileDirectory;

public class GlueServerListener implements ServerListener {

	private Charset charset = Charset.forName("UTF-8");
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	public void listen(Server server, HTTPServer httpServer) {
		GlueParserProvider parserProvider = new GlueParserProvider(new StaticJavaMethodProvider(new GlueMetrics(server.getRepository(), server.getRepository().getMetricsDispatcher(), server.getRepository().getComplexEventDispatcher())));
		
		ResourceContainer<?> scriptFolder = null;
		// we assume the classpath is the "lib" folder, we check the parent of a classpath folder for a script folder
		for (String path : System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
			File file = new File(path);
			if (file.exists()) {
				// the folder containing the jars (e.g. lib)
				if (file.isDirectory()) {
					file = file.getParentFile();
				}
				// a specific jar
				else {
					file = file.getParentFile().getParentFile();
				}
			}
			File scripts = new File(file, "scripts");
			if (scripts.exists()) {
				scriptFolder = new FileDirectory(null, scripts, false);
				break;
			}
		}
		
		if (scriptFolder != null) {
			logger.info("Loading script repository: " + scriptFolder);
			try {
				ScannableScriptRepository repository = new ScannableScriptRepository(null, scriptFolder, parserProvider, charset, true);
				SimpleExecutionEnvironment simpleExecutionEnvironment = new SimpleExecutionEnvironment("default");
				for (Script script : repository) {
					try {
						ExecutorGroup root = script.getRoot();
						if (root != null) {
							Map<String, String> annotations = root.getContext().getAnnotations();
							if (annotations != null && "startup".equals(annotations.get("hook"))) {
								logger.info("Running: " + (script.getNamespace() == null ? "" : script.getNamespace() + ".") + script.getName());
								ScriptRuntime scriptRuntime = new ScriptRuntime(script, simpleExecutionEnvironment, false, new HashMap<String, Object>());
								scriptRuntime.setFormatter(new OutputFormatter() {
									@Override
									public void start(Script script) {
										
									}
									@Override
									public void before(Executor executor) {
										
									}
									@Override
									public void after(Executor executor) {
										
									}
									@Override
									public void validated(GlueValidation... validations) {
										
									}
									@Override
									public void print(Object... messages) {
										if (messages != null) {
											for (Object message : messages) {
												if (message != null) {
													logger.info(message.toString());
												}
											}
										}
									}
									@Override
									public void end(Script script, Date started, Date stopped, Exception exception) {
										
									}
									@Override
									public boolean shouldExecute(Executor executor) {
										return true;
									}
									
								});
								scriptRuntime.run();
							}
						}
					}
					catch (Exception e) {
						logger.error("Could not parse or run script: " + script.getName(), e);
					}
				}
			}
			catch (Exception e) {
				logger.error("Could not load script repository", e);
			}
		}
		else {
			logger.warn("Could not find script repository on loading");
		}
		
		try {
			EAIResourceRepository repository = EAIResourceRepository.getInstance();
			GlueParserProvider templateProvider = new GlueParserProvider(new StaticJavaMethodProvider(new GlueTemplaterMethods(repository)), new ServiceMethodProvider(repository, repository));
			DynamicScriptRepository dynamicRepository = new DynamicScriptRepository(templateProvider);
			SimpleExecutionEnvironment simpleExecutionEnvironment = new SimpleExecutionEnvironment("default");
			repository.getTemplaters().add(new Templater() {
				@Override
				public String template(String content) {
					try {
						DynamicScript script = new DynamicScript(dynamicRepository, templateProvider.newParser(dynamicRepository, "dynamic.glue"), "templated = template(resource('content.txt'))");
						script.getResources().put("content.txt", content.getBytes(Charset.forName("UTF-8")));
						ScriptRuntime scriptRuntime = new ScriptRuntime(script, simpleExecutionEnvironment, false, new HashMap<String, Object>());
						scriptRuntime.run();
						Object object = scriptRuntime.getExecutionContext().getPipeline().get("templated");
						return object != null ? object.toString() : content;
					}
					catch (Exception e) {
						logger.error("Could not template content", e);
						return content;
					}
				}
			});
		}
		catch (Exception e) {
			logger.error("Could not register glue templater", e);
		}
	}

}
