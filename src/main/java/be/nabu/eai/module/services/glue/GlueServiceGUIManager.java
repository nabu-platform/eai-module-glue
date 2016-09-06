package be.nabu.eai.module.services.glue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
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
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.ResourceManagerFactory;
import be.nabu.eai.developer.api.ResourceManagerInstance;
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
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.jfx.control.ace.AceEditor;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceUtils;
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
	private Map<Resource, ResourceManagerInstance> managers = new HashMap<Resource, ResourceManagerInstance>(); 

	public GlueServiceGUIManager() {
		super("Glue Service", GlueServiceArtifact.class, new GlueServiceManager());
	}
	
	public GlueServiceGUIManager(String name, Class<GlueServiceArtifact> artifactClass, ArtifactManager<GlueServiceArtifact> artifactManager) {
		super(name, artifactClass, artifactManager);
	}
	
	public List<String> getAutocomplete(GlueServiceArtifact artifact) {
		List<String> methods = new ArrayList<String>();
		MethodProvider[] methodProviders = artifact.getParserProvider().getMethodProviders(artifact.getScriptRepository());
		for (MethodProvider provider : methodProviders) {
			for (MethodDescription description : provider.getAvailableMethods()) {
				StringBuilder builder = new StringBuilder();
				builder.append((description.getNamespace() == null ? "" : description.getNamespace() + ".") + description.getName());
				builder.append("(");
				boolean first = true;
				for (ParameterDescription parameter : description.getParameters()) {
					if (first) {
						first = false;
					}
					else {
						builder.append(", ");
					}
					builder.append(parameter.getName());
				}
				builder.append(")");
				methods.add(builder.toString());
			}
		}
		return methods;
	}

	protected void addAutocomplete(GlueServiceArtifact artifact, final AceEditor ace) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				List<String> autocomplete = getAutocomplete(artifact);
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						ace.addContains("Methods", autocomplete);
					}
				});
			}
		}).start();
	}
	
	@Override
	public void display(MainController controller, AnchorPane pane, final GlueServiceArtifact artifact) throws IOException, ParseException {
		// TODO Auto-generated method stub
		// Show a text area and the input/output interface at the bottom
		// refresh the interface whenever you change focus on the textarea
		SplitPane split = new SplitPane();
		split.setOrientation(Orientation.VERTICAL);
		final AceEditor ace = getEditor(artifact);
		ace.setLiveAutocompletion(false);
		addAutocomplete(artifact, ace);

		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
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
		final TabPane tabs = new TabPane();
		
		Button rename = new Button("Rename");
		rename.disableProperty().bind(resources.getSelectionModel().selectedItemProperty().isNull());
		rename.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(ActionEvent arg0) {
				SimpleProperty<String> mainProperty = new SimpleProperty<String>("New Name", String.class, true);
				Set properties = new LinkedHashSet(Arrays.asList(new Property [] {
					mainProperty
				}));
				final SimplePropertyUpdater updater = new SimplePropertyUpdater(true, properties);
				EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Rename Resource", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						String newName = updater.getValue("New Name");
						String selectedItem = resources.getSelectionModel().getSelectedItem();
						if (newName != null && selectedItem != null) {
							if (artifact.getResourceDirectory().getChild(newName) == null) {
								// close any tab related to selected item
								for (int i = tabs.getTabs().size() - 1; i >= 0; i--) {
									if (tabs.getTabs().get(i).getId().equals(selectedItem)) {
										tabs.getTabs().remove(i);
									}
								}
								try {
									ResourceUtils.rename(artifact.getResourceDirectory().getChild(selectedItem), newName);
									resources.getItems().remove(selectedItem);
									resources.getItems().add(newName);
									resources.getSelectionModel().select(newName);
								}
								catch (IOException e) {
									MainController.getInstance().notify(e);
									throw new RuntimeException(e);
								}
							}
							else {
								MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "A resource with the name '" + newName + "' already exists"));
							}
						}
					}
				});
			}
		});
		
		Button delete = new Button("Delete");
		delete.disableProperty().bind(resources.getSelectionModel().selectedItemProperty().isNull());
		delete.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String selectedItem = resources.getSelectionModel().getSelectedItem();
				if (selectedItem != null) {
					// close any tab related to it
					for (int i = tabs.getTabs().size() - 1; i >= 0; i--) {
						if (tabs.getTabs().get(i).getId().equals(selectedItem)) {
							tabs.getTabs().remove(i);
						}
					}
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
		
		buttons.getChildren().addAll(create, upload, rename, delete);
		
		for (Resource resource : artifact.getResourceDirectory()) {
			if (resource instanceof ReadableResource) {
				resources.getItems().add(resource.getName());
			}
		}
		
		resources.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				Resource resource = artifact.getResourceDirectory().getChild(arg2);
				if (resource != null && !managers.containsKey(resource)) {
					ResourceManagerInstance instance = ResourceManagerFactory.getInstance().manage(resource);
					if (instance != null) {
						Tab tab = new Tab(resource.getName());
						tab.setId(resource.getName());
						tab.setContent(instance.getView());
						tabs.getTabs().add(tab);
						tabs.getSelectionModel().select(tab);
						managers.put(resource, instance);
					}
					else {
						MainController.getInstance().notify(new ValidationMessage(Severity.WARNING, "No viewer is associated with this resource"));
					}
				}
			}
		});
		
		tabs.getTabs().addListener(new ListChangeListener<Tab>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends Tab> change) {
				while (change.next()) {
					if (change.wasRemoved()) {
						for (Tab tab : change.getRemoved()) {
							Resource resource = artifact.getResourceDirectory().getChild(tab.getId());
							managers.remove(resource);
						}
					}
				}
			}
		});
		
		VBox vbox = new VBox();
		vbox.getChildren().addAll(buttons, resources);
		vbox.setMinWidth(buttons.getPrefWidth());
		box.getChildren().addAll(vbox, tabs);
		SplitPane pane = new SplitPane();
		pane.setOrientation(Orientation.HORIZONTAL);
		pane.getItems().addAll(vbox, tabs);
//		HBox.setHgrow(tabs, Priority.SOMETIMES);
//		HBox.setHgrow(vbox, Priority.NEVER);
		AnchorPane root = new AnchorPane();
		root.getChildren().add(pane);
		AnchorPane.setBottomAnchor(pane, 0d);
		AnchorPane.setLeftAnchor(pane, 0d);
		AnchorPane.setTopAnchor(pane, 0d);
		AnchorPane.setRightAnchor(pane, 0d);
		return root;
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
			public void handle(Event event) {
				try {
					updateContent(artifact, ace);
					MainController.getInstance().save(artifact.getId());
					MainController.getInstance().notify(new ValidationMessage(Severity.INFO, "Glue service saved"));
					event.consume();
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
				for (ResourceManagerInstance instance : managers.values()) {
					instance.save();
				}
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
