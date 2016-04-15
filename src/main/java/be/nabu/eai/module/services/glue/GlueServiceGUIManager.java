package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import javafx.scene.layout.AnchorPane;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BasePortableGUIManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class GlueServiceGUIManager extends BasePortableGUIManager<GlueServiceArtifact, BaseArtifactGUIInstance<GlueServiceArtifact>> {

	public GlueServiceGUIManager() {
		super("Glue Service", GlueServiceArtifact.class, new GlueServiceManager());
	}

	@Override
	public void display(MainController controller, AnchorPane pane, GlueServiceArtifact artifact) throws IOException, ParseException {
		// TODO Auto-generated method stub
		// Show a text area and the input/output interface at the bottom
		// refresh the interface whenever you change focus on the textarea
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected BaseArtifactGUIInstance<GlueServiceArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<GlueServiceArtifact>(this, entry);
	}

	@Override
	protected void setEntry(BaseArtifactGUIInstance<GlueServiceArtifact> guiInstance, ResourceEntry entry) {
		guiInstance.setEntry(entry);
	}

	@Override
	protected GlueServiceArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		return new GlueServiceArtifact(entry.getId(), entry.getRepository());
	}

	@Override
	protected void setInstance(BaseArtifactGUIInstance<GlueServiceArtifact> guiInstance, GlueServiceArtifact instance) {
		guiInstance.setArtifact(instance);		
	}
	
	@Override
	public String getCategory() {
		return "Services";
	}

}
