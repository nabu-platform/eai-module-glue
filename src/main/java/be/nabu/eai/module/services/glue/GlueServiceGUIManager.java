package be.nabu.eai.module.services.glue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BasePortableGUIManager;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
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
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class GlueServiceGUIManager extends BasePortableGUIManager<GlueServiceArtifact, BaseArtifactGUIInstance<GlueServiceArtifact>> {

	private Tree<Element<?>> output;
	private Tree<Element<?>> input;
	private Map<Resource, TextArea> resources = new HashMap<Resource, TextArea>(); 

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

		TabPane tabs = new TabPane();
		
		Tab tabIface = new Tab("Interface");
		tabIface.setContent(getIface(controller, artifact));
		
		Tab tabResources = new Tab("Resources");
		tabResources.setContent(getResources(controller, artifact));
		
		tabs.getTabs().addAll(tabIface, tabResources);

		split.getItems().addAll(ace.getWebView(), tabs);
		pane.getChildren().add(split);

		
		AnchorPane.setBottomAnchor(split, 0d);
		AnchorPane.setTopAnchor(split, 0d);
		AnchorPane.setLeftAnchor(split, 0d);
		AnchorPane.setRightAnchor(split, 0d);
	}
	
	protected Pane getResources(MainController controller, GlueServiceArtifact artifact) {
		ListView<String> resources = new ListView<String>();
		HBox box = new HBox();
		
		HBox buttons = new HBox();
		Button create = new Button("New");
		
		create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(ActionEvent arg0) {
				Set properties = new LinkedHashSet(Arrays.asList(new Property [] {
					new SimpleProperty<String>("Name", String.class, false)
				}));
				final SimplePropertyUpdater updater = new SimplePropertyUpdater(true, properties);
				EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Create Resource", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						String name = updater.getValue("Name");
						if (name != null) {
							if (artifact.getResourceDirectory().getChild(name) == null) {
								try {
									Resource created = ((ManageableContainer<?>) artifact.getResourceDirectory()).create(name, URLConnection.guessContentTypeFromName(name));
									if (created != null) {
										resources.getItems().add(created.getName());
										resources.getSelectionModel().select(created.getName());
									}
								}
								catch (IOException e) {
									MainController.getInstance().notify(e);
									throw new RuntimeException(e);
								}
							}
							else {
								MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "A resource with the name '" + name + "' already exists"));
							}
						}
					}
				});
			}
		});
		
		Button upload = new Button("Upload");
		
		upload.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(ActionEvent arg0) {
				SimpleProperty<File> mainProperty = new SimpleProperty<File>("File", File.class, true);
				mainProperty.setInput(true);
				Set properties = new LinkedHashSet(Arrays.asList(new Property [] {
					mainProperty
				}));
				final SimplePropertyUpdater updater = new SimplePropertyUpdater(true, properties);
				EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Upload Resource", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						File file = updater.getValue("File");
						if (file != null) {
							if (artifact.getResourceDirectory().getChild(file.getName()) == null) {
								try {
									Resource created = ((ManageableContainer<?>) artifact.getResourceDirectory()).create(file.getName(), URLConnection.guessContentTypeFromName(file.getName()));
									if (created != null) {
										WritableContainer<ByteBuffer> writable = ((WritableResource) created).getWritable();
										try {
											BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
											try {
												IOUtils.copyBytes(IOUtils.wrap(bufferedInputStream), writable);
											}
											finally {
												bufferedInputStream.close();
											}
										}
										finally {
											writable.close();
										}
										resources.getItems().add(created.getName());
										resources.getSelectionModel().select(created.getName());
									}
								}
								catch (IOException e) {
									MainController.getInstance().notify(e);
									throw new RuntimeException(e);
								}
							}
							else {
								MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "A resource with the name '" + file + "' already exists"));
							}
						}
					}
				});
			}
		});
		
		Button delete = new Button("Delete");
		upload.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String selectedItem = resources.getSelectionModel().getSelectedItem();
				if (selectedItem != null) {
					try {
						((ManageableContainer<?>) artifact.getResourceDirectory()).delete(selectedItem);
					}
					catch (IOException e) {
						MainController.getInstance().notify(e);
						throw new RuntimeException(e);
					}
					resources.getItems().remove(selectedItem);
				}
			}
		});
		
		buttons.getChildren().addAll(create, upload, delete);
		
		VBox vbox = new VBox();
		vbox.getChildren().addAll(buttons, resources);
		
		final TabPane tabs = new TabPane();
		
		for (Resource resource : artifact.getResourceDirectory()) {
			if (resource instanceof ReadableResource) {
				resources.getItems().add(resource.getName());
			}
		}
		
		box.getChildren().addAll(vbox, tabs);
		return box;
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
		return new BaseArtifactGUIInstance<GlueServiceArtifact>(this, entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				List<Validation<?>> save = super.save();
				// TODO: save open resources as well
				return save;
			}
		};
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
