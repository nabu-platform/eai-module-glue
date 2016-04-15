package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BasePortableGUIManager;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.util.ElementSelectionListener;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;

public class GlueServiceGUIManager extends BasePortableGUIManager<GlueServiceArtifact, BaseArtifactGUIInstance<GlueServiceArtifact>> {

	public GlueServiceGUIManager() {
		super("Glue Service", GlueServiceArtifact.class, new GlueServiceManager());
	}

	@Override
	public void display(MainController controller, AnchorPane pane, GlueServiceArtifact artifact) throws IOException, ParseException {
		// TODO Auto-generated method stub
		// Show a text area and the input/output interface at the bottom
		// refresh the interface whenever you change focus on the textarea
		SplitPane split = new SplitPane();
		split.setOrientation(Orientation.VERTICAL);
		final TextArea text = new TextArea();
		InputStream content = artifact.getContent();
		if (content != null) {
			try {
				text.setText(new String(IOUtils.toBytes(IOUtils.wrap(content)), Charset.defaultCharset()));
			}
			finally {
				content.close();
			}
		}
		HBox iface = new HBox();
		
		ScrollPane right = new ScrollPane();
		ElementSelectionListener elementSelectionListener = new ElementSelectionListener(controller, false, true);
		final Tree<Element<?>> output = new Tree<Element<?>>(new ElementMarshallable());
		output.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getOutputDefinition()), null, false, false));
		output.prefWidthProperty().bind(right.widthProperty());
		output.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		right.setContent(output);
		
		ScrollPane left = new ScrollPane();
		final Tree<Element<?>> input = new Tree<Element<?>>(new ElementMarshallable());
		input.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getInputDefinition()), null, false, false));
		left.setContent(input);
		input.prefWidthProperty().bind(left.widthProperty());
		input.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		
		HBox.setHgrow(left, Priority.ALWAYS);
		HBox.setHgrow(right, Priority.ALWAYS);
		iface.getChildren().addAll(left, right);
		
		final String original = text.getText();
		text.focusedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				if (!text.getText().equals(original)) {
					try {
						artifact.setContent(text.getText());
						input.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getInputDefinition()), null, false, false));
						output.rootProperty().set(new ElementTreeItem(new RootElement(artifact.getServiceInterface().getOutputDefinition()), null, false, false));
						MainController.getInstance().setChanged();
					}
					catch (Exception e) {
						MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "Could not parse content: " + e.getMessage()));
					}
				}
			}
		});

		split.getItems().addAll(text, iface);
		pane.getChildren().add(split);
		
		AnchorPane.setBottomAnchor(split, 0d);
		AnchorPane.setTopAnchor(split, 0d);
		AnchorPane.setLeftAnchor(split, 0d);
		AnchorPane.setRightAnchor(split, 0d);
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
