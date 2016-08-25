package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BasePortableGUIManager;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.util.ElementSelectionListener;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.ace.AceEditor;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class GlueServiceGUIManager extends BasePortableGUIManager<GlueServiceArtifact, BaseArtifactGUIInstance<GlueServiceArtifact>> {

	private Tree<Element<?>> output;
	private Tree<Element<?>> input;

	public GlueServiceGUIManager() {
		super("Glue Service", GlueServiceArtifact.class, new GlueServiceManager());
	}
	
	public GlueServiceGUIManager(String name, Class<GlueServiceArtifact> artifactClass, ArtifactManager<GlueServiceArtifact> artifactManager) {
		super(name, artifactClass, artifactManager);
	}

	@Override
	public void display(MainController controller, AnchorPane pane, final GlueServiceArtifact artifact) throws IOException, ParseException {
		// TODO Auto-generated method stub
		// Show a text area and the input/output interface at the bottom
		// refresh the interface whenever you change focus on the textarea
		SplitPane split = new SplitPane();
		split.setOrientation(Orientation.VERTICAL);
		final AceEditor ace = getEditor(artifact);
		SplitPane iface = getIface(controller, artifact);

		split.getItems().addAll(ace.getWebView(), iface);
		pane.getChildren().add(split);
		
		AnchorPane.setBottomAnchor(split, 0d);
		AnchorPane.setTopAnchor(split, 0d);
		AnchorPane.setLeftAnchor(split, 0d);
		AnchorPane.setRightAnchor(split, 0d);
	}

	protected SplitPane getIface(MainController controller, final GlueServiceArtifact artifact) {
		SplitPane iface = new SplitPane();
		iface.setOrientation(Orientation.HORIZONTAL);
		
		ScrollPane right = new ScrollPane();
		ElementSelectionListener elementSelectionListener = new ElementSelectionListener(controller, false, true);
		output = new Tree<Element<?>>(new ElementMarshallable());
		output.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getOutputDefinition()), null, false, false));
		output.prefWidthProperty().bind(right.widthProperty());
		output.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		right.setContent(output);
		
		ScrollPane left = new ScrollPane();
		input = new Tree<Element<?>>(new ElementMarshallable());
		input.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getInputDefinition()), null, false, false));
		left.setContent(input);
		input.prefWidthProperty().bind(left.widthProperty());
		input.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		
		iface.getItems().addAll(left, right);
		return iface;
	}
	
	public AceEditor getEditor(final GlueServiceArtifact artifact) {
		AceEditor ace = new AceEditor();
		ace.setContent("text/x-glue", artifact.getContent());
		ace.getWebView().focusedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				if (arg2 && (artifact.getContent() == null || !artifact.getContent().equals(ace.getContent()))) {
					updateContent(artifact, ace);
				}
			}
		});
		ace.subscribe(AceEditor.CHANGE, new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				MainController.getInstance().setChanged();				
			}
		});
		ace.subscribe(AceEditor.SAVE, new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				try {
					updateContent(artifact, ace);
					MainController.getInstance().save(artifact.getId());
				}
				catch (Exception e) {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "Could not save: " + e.getMessage()));
				}
			}
		});
		ace.subscribe(AceEditor.CLOSE, new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				MainController.getInstance().close(artifact.getId());
			}
		});
		return ace;
	}

	private void updateContent(final GlueServiceArtifact artifact, final AceEditor ace) {
		try {
			artifact.setContent(ace.getContent());
			if (input != null) {
				input.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getInputDefinition()), null, false, false));
			}
			if (output != null) {
				output.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getOutputDefinition()), null, false, false));
			}
		}
		catch (Exception e) {
			MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "Could not parse content: " + e.getMessage()));
		}
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
		return new GlueServiceArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
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
