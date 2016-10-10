package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ModifiableNodeEntry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.core.impl.executors.BaseExecutor;
import be.nabu.glue.core.impl.executors.EvaluateExecutor;
import be.nabu.glue.core.impl.executors.ForEachExecutor;
import be.nabu.glue.core.impl.executors.SwitchExecutor;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class GlueServiceManager implements ArtifactManager<GlueServiceArtifact> {

	public GlueServiceArtifact newArtifact(ResourceEntry entry) throws IOException {
		return new GlueServiceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}
	
	@Override
	public GlueServiceArtifact load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		GlueServiceArtifact service = newArtifact(entry);
		Resource child = entry.getContainer().getChild("script.glue");
		if (child != null) {
			ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
			try {
				byte[] bytes = IOUtils.toBytes(readable);
				service.setContent(new String(bytes, Charset.defaultCharset()));
			}
			finally {
				readable.close();
			}
		}
		return service;
	}

	@Override
	public List<Validation<?>> save(ResourceEntry entry, GlueServiceArtifact artifact) throws IOException {
		Resource child = entry.getContainer().getChild("script.glue");
		String content = artifact.getContent();
		if (content != null) {
			if (child == null) {
				child = ((ManageableContainer<?>) entry.getContainer()).create("script.glue", "text/plain");
			}
			WritableContainer<ByteBuffer> writable = ((WritableResource) child).getWritable();
			try {
				IOUtils.copyBytes(IOUtils.wrap(content.getBytes(), true), writable);
			}
			finally {
				writable.close();
			}
		}
		// if the child exists but there is no source, you wiped it or something, remove it
		else if (child != null) {
			((ManageableContainer<?>) entry.getContainer()).delete("script.glue");
		}
		if (entry instanceof ModifiableNodeEntry) {
			((ModifiableNodeEntry) entry).updateNode(getReferences(artifact));
		}
		return new ArrayList<Validation<?>>();
	}

	@Override
	public Class<GlueServiceArtifact> getArtifactClass() {
		return GlueServiceArtifact.class;
	}

	@Override
	public List<String> getReferences(GlueServiceArtifact artifact) throws IOException {
		Set<String> references = new HashSet<String>();
		try {
			getReferences(artifact.getScript().getRoot(), references);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
		Iterator<String> iterator = references.iterator();
		while(iterator.hasNext()) {
			Entry entry = artifact.getRepository().getEntry(iterator.next());
			if (entry == null || !entry.isNode()) {
				iterator.remove();
			}
		}
		return new ArrayList<String>(references);
	}
	
	public static void getReferences(ExecutorGroup group, Set<String> references) {
		for (Executor executor : group.getChildren()) {
			if (executor instanceof AssignmentExecutor) {
				String optionalType = ((AssignmentExecutor) executor).getOptionalType();
				if (optionalType != null) {
					references.add(optionalType);
				}
			}
			if (executor instanceof BaseExecutor) {
				getReferences(((BaseExecutor) executor).getCondition(), references);
			}
			if (executor instanceof SwitchExecutor) {
				getReferences(((SwitchExecutor) executor).getToMatch(), references);
			}
			if (executor instanceof ForEachExecutor) {
				getReferences(((ForEachExecutor) executor).getForEach(), references);
			}
			if (executor instanceof EvaluateExecutor) {
				getReferences(((EvaluateExecutor) executor).getOperation(), references);
			}
			if (executor instanceof ExecutorGroup) {
				getReferences((ExecutorGroup) executor, references);
			}
		}
	}
	
	public static void getReferences(Operation<?> operation, Set<String> references) {
		if (operation != null) {
			if (operation.getType() == OperationType.METHOD) {
				QueryPart queryPart = operation.getParts().get(0);
				// contains the name of the method
				if (queryPart.getContent() instanceof String) {
					references.add((String) queryPart.getContent());
				}
			}
			for (QueryPart part : operation.getParts()) {
				if (part.getType() == QueryPart.Type.OPERATION) {
					getReferences((Operation<?>) part.getContent(), references);
				}
			}
		}
	}

	@Override
	public List<Validation<?>> updateReference(GlueServiceArtifact artifact, String from, String to) throws IOException {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		try {
			artifact.setContent(artifact.getContent().replaceAll("\\b" + Pattern.quote(from) + "\\b", Matcher.quoteReplacement(to)));
		}
		catch (ParseException e) {
			messages.add(new ValidationMessage(Severity.ERROR, e.getMessage()));
		}
		return messages;
	}

}
