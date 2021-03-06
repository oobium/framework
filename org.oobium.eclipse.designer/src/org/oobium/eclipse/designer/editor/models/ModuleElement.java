package org.oobium.eclipse.designer.editor.models;

import static org.oobium.utils.StringUtils.blank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.oobium.build.model.ModelDefinition;
import org.oobium.build.model.ModelRelation;
import org.oobium.build.workspace.Module;

public class ModuleElement extends Element {

	public static final String PROP_MODELS = "Module.Models";
	
	private SiteElement site;
	private Module module;
	private Map<String, ModelElement> models;
	
	public ModuleElement(SiteElement site, Module module, Map<?,?> modMap) {
		this.site = site;
		this.module = module;
		this.models = new HashMap<String, ModelElement>();

		Map<?,?> modelsMap = (modMap != null) ? (Map<?,?>) modMap.get("models") : null;
		
		ModelDefinition[] definitions = ModelDefinition.getModelDefinitions(module.findModels());
		for(ModelDefinition definition : definitions) {
			ModelElement model = new ModelElement(this, definition);
			if(modelsMap != null) {
				model.setProperties((Map<?,?>) modelsMap.get(model.getName()));
			}
			addModel(model);
		}
		
		for(ModelElement model : models.values()) {
			for(ModelRelation relation : model.getDefinition().getRelations()) {
				String sourceField = relation.name();
				String targetField = relation.opposite();
				if(blank(targetField) || sourceField.compareTo(targetField) < 0) {
					ModelElement sourceModel = model;
					ModelElement targetModel = models.get(relation.type());
					try {
						Connection connection = new Connection(sourceModel, sourceField, targetModel, targetField);
						model.addConnection(connection);
					} catch(IllegalArgumentException e) {
						System.err.println("error creating connection: " + sourceModel + ", " + sourceField + ", " + targetModel + ", " + targetField);
					}
				}
			}
		}
	}

	public SiteElement getSite() {
		return site;
	}
	
	public Module getModule() {
		return module;
	}
	
	public String getName() {
		return module.name;
	}
	
	public void addModel(ModelElement model) {
		models.put(model.getType(), model);
		firePropertyChanged(PROP_MODELS, null, models);
	}
	
	public void removeModel(ModelElement model) {
		models.remove(model.getType());
		firePropertyChanged(PROP_MODELS, null, models);
	}
	
	public boolean hasModels() {
		return models != null && !models.isEmpty();
	}
	
	public List<ModelElement> getModels() {
		return new ArrayList<ModelElement>(models.values());
	}
	
	@Override
	public IPropertyDescriptor[] getPropertyDescriptors() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPropertyValue(Object id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isPropertySet(Object id) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void resetPropertyValue(Object id) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setPropertyValue(Object id, Object value) {
		// TODO Auto-generated method stub

	}

}
