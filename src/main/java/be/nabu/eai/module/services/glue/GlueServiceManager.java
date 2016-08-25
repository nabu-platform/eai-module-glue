package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
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
		return new ArrayList<Validation<?>>();
	}

	@Override
	public Class<GlueServiceArtifact> getArtifactClass() {
		return GlueServiceArtifact.class;
	}

	@Override
	public List<String> getReferences(GlueServiceArtifact artifact) throws IOException {
		// TODO: calculate types & services used
		return new ArrayList<String>();
	}

	@Override
	public List<Validation<?>> updateReference(GlueServiceArtifact artifact, String from, String to) throws IOException {
		// TODO: implement
		return new ArrayList<Validation<?>>();
	}

}
