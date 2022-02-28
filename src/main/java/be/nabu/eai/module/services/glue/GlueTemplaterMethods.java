package be.nabu.eai.module.services.glue;

import be.nabu.eai.repository.api.Repository;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "templater")
public class GlueTemplaterMethods {
	private Repository repository;

	public GlueTemplaterMethods(Repository repository) {
		this.repository = repository;
	}
	
	public Repository repository() {
		return repository;
	}
	
	public Artifact artifact(String id) {
		return repository.resolve(id);
	}
}
