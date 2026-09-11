package be.nabu.eai.module.services.glue;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.DynamicArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.ResourceWritableContainer;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.utils.io.IOUtils;

public class GlueServiceArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<GlueServiceArtifact> implements CreatableArtifactFragmentManager<GlueServiceArtifact>, DynamicArtifactFragmentManager<GlueServiceArtifact> {

	private static final String SCRIPT_PATH = "script.glue";
	protected static final String RESOURCES_PATH = "resources";
	private static final String CONTENT_TYPE = "text/x-glue";
	private static final String ARTIFACT_TYPE = "glueService";
	private static final String ARTIFACT_CATEGORY = "service";
	private static final String RESOURCE_FRAGMENT_TYPE = "resource";
	private static final String GUIDELINES_PATH = "/guidelines/glue-service.md";

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new GlueServiceManager(), true);
			GlueServiceArtifact artifact = new GlueServiceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
			artifact.setContent("");
			new GlueServiceManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(final GlueServiceArtifact artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(super.listFragments(artifact));
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		boolean editable = entry instanceof ResourceEntry && entry.isEditable();
		fragments.add(new ScriptFragment(artifact, editable));
		ResourceContainer<?> resources = artifact.getResourceDirectory();
		if (resources != null) {
			addResourceFragments(artifact, fragments, resources, RESOURCES_PATH, editable);
		}
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(GlueServiceArtifact artifact, String path, String oldContent, String newContent) {
		if (SCRIPT_PATH.equals(path)) {
			return updateScript(artifact, newContent);
		}
		if (isManagedResourcePath(path)) {
			return updateResourceFragment(artifact, path, newContent);
		}
		return super.updateFragment(artifact, path, oldContent, newContent);
	}

	protected List<Validation<?>> updateScript(GlueServiceArtifact artifact, String content) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		ResourceEntry entry = getEditableEntry(artifact, validations, "Updating the Glue script");
		if (entry == null) {
			return validations;
		}
		try {
			GlueServiceArtifact candidate = newArtifact(entry);
			candidate.setContent(content == null ? "" : content);
			candidate.getScript().getRoot();
			copyAdditionalState(artifact, candidate);
			validations.addAll(saveArtifact(entry, candidate));
			if (!hasErrors(validations)) {
				artifact.setContent(candidate.getContent());
			}
		}
		catch (Exception e) {
			validations.add(error(e));
		}
		return validations;
	}

	protected GlueServiceArtifact newArtifact(ResourceEntry entry) throws Exception {
		return new GlueServiceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	protected List<Validation<?>> saveArtifact(ResourceEntry entry, GlueServiceArtifact artifact) throws Exception {
		return new GlueServiceManager().save(entry, artifact);
	}

	protected void copyAdditionalState(GlueServiceArtifact source, GlueServiceArtifact target) {
	}

	@Override
	public List<Validation<?>> createFragment(GlueServiceArtifact artifact, String path, String initialContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		if (!isManagedResourcePath(path)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Creating fragments is only supported in 'resources/'"));
			return validations;
		}
		ResourceEntry entry = getEditableEntry(artifact, validations, "Creating resource fragments");
		if (entry == null) {
			return validations;
		}
		try {
			if (ResourceUtils.resolve(entry.getContainer(), toRepositoryPath(path)) != null) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' already exists"));
				return validations;
			}
			WritableResource resource = createWritableResource(entry, path);
			write(resource, initialContent == null ? "" : initialContent);
		}
		catch (Exception e) {
			validations.add(error(e));
		}
		return validations;
	}

	@Override
	public List<Validation<?>> deleteFragment(GlueServiceArtifact artifact, String path) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		if (!isManagedResourcePath(path)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Deleting fragments is only supported in 'resources/'"));
			return validations;
		}
		ResourceEntry entry = getEditableEntry(artifact, validations, "Deleting resource fragments");
		if (entry == null) {
			return validations;
		}
		try {
			int separator = path.lastIndexOf('/');
			String parentPath = toRepositoryPath(path.substring(0, separator));
			String name = path.substring(separator + 1);
			Resource parentResource = ResourceUtils.resolve(entry.getContainer(), parentPath);
			if (!(parentResource instanceof ManageableContainer)) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Parent for fragment '" + path + "' is not manageable"));
				return validations;
			}
			Resource child = ((ResourceContainer<?>) parentResource).getChild(name);
			if (child == null) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' does not exist"));
				return validations;
			}
			if (child instanceof ResourceContainer) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' is a directory"));
				return validations;
			}
			((ManageableContainer<?>) parentResource).delete(name);
		}
		catch (Exception e) {
			validations.add(error(e));
		}
		return validations;
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> sections = new ArrayList<String>();
		if (fragmentTypes == null || fragmentTypes.isEmpty() || fragmentTypes.contains(ARTIFACT_TYPE) || fragmentTypes.contains(SCRIPT_PATH) || fragmentTypes.contains(RESOURCE_FRAGMENT_TYPE)) {
			String guidelines = EAIRepositoryUtils.loadCachedClasspathResource(GlueServiceArtifactFragmentManager.class, GUIDELINES_PATH);
			if (guidelines != null && !guidelines.trim().isEmpty()) {
				sections.add(guidelines.trim());
			}
		}
		String shared = super.getGuidelines(fragmentTypes == null || fragmentTypes.isEmpty() ? null : Arrays.asList("metadata"));
		if (shared != null && !shared.trim().isEmpty()) {
			sections.add(shared.trim());
		}
		return sections.isEmpty() ? null : String.join("\n\n", sections);
	}

	@Override
	public boolean shouldReloadAfterChange(String fragment) {
		return !isManagedResourcePath(fragment);
	}

	@Override
	public Class<GlueServiceArtifact> getArtifactClass() {
		return GlueServiceArtifact.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return ARTIFACT_CATEGORY;
	}

	protected List<Validation<?>> updateResourceFragment(GlueServiceArtifact artifact, String path, String content) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		ResourceEntry entry = getEditableEntry(artifact, validations, "Updating resource fragments");
		if (entry == null) {
			return validations;
		}
		try {
			Resource resource = ResourceUtils.resolve(entry.getContainer(), toRepositoryPath(path));
			if (!(resource instanceof WritableResource) || resource instanceof ResourceContainer) {
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Fragment '" + path + "' is not a writable file"));
				return validations;
			}
			write((WritableResource) resource, content == null ? "" : content);
		}
		catch (Exception e) {
			validations.add(error(e));
		}
		return validations;
	}

	private ResourceEntry getEditableEntry(GlueServiceArtifact artifact, List<Validation<?>> validations, String operation) {
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		if (!(entry instanceof ResourceEntry)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, operation + " requires a resource-backed Glue service"));
			return null;
		}
		if (!entry.isEditable()) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Glue service '" + artifact.getId() + "' is not editable"));
			return null;
		}
		return (ResourceEntry) entry;
	}

	private WritableResource createWritableResource(ResourceEntry entry, String path) throws Exception {
		int separator = path.lastIndexOf('/');
		String parentPath = toRepositoryPath(path.substring(0, separator));
		String name = path.substring(separator + 1);
		ResourceContainer<?> parent = ResourceUtils.mkdirs(entry.getContainer(), parentPath);
		if (!(parent instanceof ManageableContainer)) {
			throw new IllegalStateException("Parent for fragment '" + path + "' is not manageable");
		}
		Resource created = ((ManageableContainer<?>) parent).create(name, null);
		if (!(created instanceof WritableResource)) {
			throw new IllegalStateException("Fragment '" + path + "' is not writable");
		}
		return (WritableResource) created;
	}

	private void write(WritableResource resource, String content) throws Exception {
		try (OutputStream output = IOUtils.toOutputStream(new ResourceWritableContainer(resource))) {
			output.write(content.getBytes(StandardCharsets.UTF_8));
		}
	}

	protected void addResourceFragments(GlueServiceArtifact artifact, List<ArtifactFragment> fragments, ResourceContainer<?> container, String prefix, boolean editable) {
		for (Resource child : container) {
			if (child instanceof ResourceContainer) {
				addResourceFragments(artifact, fragments, (ResourceContainer<?>) child, prefix + "/" + child.getName(), editable);
			}
			else if (child instanceof ReadableResource) {
				fragments.add(new ResourceFragment(artifact, prefix + "/" + child.getName(), editable));
			}
		}
	}

	protected boolean isManagedResourcePath(String path) {
		if (path == null || !path.startsWith(RESOURCES_PATH + "/") || path.endsWith("/") || path.indexOf('\\') >= 0) {
			return false;
		}
		String[] parts = path.split("/", -1);
		if (parts.length < 2) {
			return false;
		}
		for (String part : parts) {
			if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
				return false;
			}
		}
		return true;
	}

	private String toRepositoryPath(String path) {
		return EAIResourceRepository.PRIVATE + path.substring(RESOURCES_PATH.length());
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private ValidationMessage error(Exception e) {
		return new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
	}

	private class ScriptFragment implements ArtifactFragment {

		private final GlueServiceArtifact artifact;
		private final boolean editable;

		private ScriptFragment(GlueServiceArtifact artifact, boolean editable) {
			this.artifact = artifact;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return false;
		}

		@Override
		public String getPath() {
			return SCRIPT_PATH;
		}

		@Override
		public String getContent() {
			return artifact.getContent();
		}

		@Override
		public String getContentType() {
			return CONTENT_TYPE;
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return ARTIFACT_TYPE;
		}

		@Override
		public Map<String, String> getProperties() {
			return new LinkedHashMap<String, String>();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), SCRIPT_PATH);
		}
	}

	private class ResourceFragment implements ArtifactFragment {

		private final GlueServiceArtifact artifact;
		private final String path;
		private final boolean editable;

		private ResourceFragment(GlueServiceArtifact artifact, String path, boolean editable) {
			this.artifact = artifact;
			this.path = path;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return editable;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
			if (!(entry instanceof ResourceEntry)) {
				throw new RuntimeException("Could not resolve resource-backed entry for: " + artifact.getId());
			}
			try {
				Resource resource = ResourceUtils.resolve(((ResourceEntry) entry).getContainer(), toRepositoryPath(path));
				if (!(resource instanceof ReadableResource)) {
					throw new FileNotFoundException("Can not find " + path);
				}
				try (InputStream input = IOUtils.toInputStream(new ResourceReadableContainer((ReadableResource) resource))) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					byte[] buffer = new byte[4096];
					int read;
					while ((read = input.read(buffer)) >= 0) {
						output.write(buffer, 0, read);
					}
					return new String(output.toByteArray(), StandardCharsets.UTF_8);
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return null;
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return RESOURCE_FRAGMENT_TYPE;
		}

		@Override
		public Map<String, String> getProperties() {
			return new LinkedHashMap<String, String>();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), toRepositoryPath(path));
		}
	}
}
