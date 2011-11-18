package org.oobium.eclipse.wizards.model.forms;

import static org.oobium.persist.Attribute.*;
import static org.oobium.utils.coercion.TypeCoercer.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.oobium.build.model.ModelAttribute;
import org.oobium.build.model.ModelDefinition;
import org.oobium.eclipse.OobiumPlugin;
import org.oobium.eclipse.wizards.model.NewModelWizardPage;

public class AttributesForm extends TableEditorForm {

	private static final String[][] types = {
		{ "String", "java.lang.String" },
		{ "BigDecimal", "java.math.BigDecimal" },
		{ "Binary", "org.oobium.persist.Binary" },
		{ "boolean", "boolean" },
		{ "Boolean", "java.lang.Boolean" },
		{ "byte[]", "byte[]" },
		{ "char[]", "char[]" },
		{ "Date (java.util.Date)", "java.util.Date" },
		{ "Date (java.sql.Date)", "java.sql.Date" },
		{ "double", "double" },
		{ "Double", "java.lang.Double" },
		{ "int", "int" },
		{ "Integer", "java.lang.Integer" },
		{ "long", "long" },
		{ "Long", "java.lang.Long" },
		{ "Map<String, String>", "java.util.Map" },
		{ "Password", "org.oobium.persist.Password" },
		{ "Text", "org.oobium.persist.Text" },
		{ "Time", "java.sql.Time" },
		{ "Timestamp", "java.sql.Timestamp" },
	};

	
	private ModelDefinition def;
	
	private Composite propsComposite;
	private Button datestampsBtn;
	private Button timestampsBtn;

	public AttributesForm(NewModelWizardPage dlg, Composite parent) {
		super(dlg, parent);
	}

	@Override
	protected void validate(TableItem item) {
		String field = item.getText(0);
		if(field.length() != 0) { // otherwise, not a field at all...
			if(field.charAt(0) == ' ' || field.charAt(field.length()-1) == ' ') {
				page.setErrorMessage("Type name is not valid. A Java type name must not start or end with a blank");
				return;
			}
			if(!Character.isJavaIdentifierStart(field.charAt(0))) {
				page.setErrorMessage("Type name is not valid. The type name '"+field+"' is not a valid identifier");
				return;
			}
			for(char ch : field.toCharArray()) {
				if(!Character.isJavaIdentifierPart(ch)) {
					page.setErrorMessage("Type name is not valid. The type name '"+field+"' is not a valid identifier");
					return;
				}
			}
			for(TableItem i : table.getItems()) {
				if(i != item && field.equals(i.getText(0))) {
					page.setErrorMessage("Duplicate field: '" + field + "'");
					return;
				}
			}
			if(def != null) {
				if(def.hasOne(field)) {
					page.setErrorMessage("Duplicate field: '" + field + "' is already a hasOne relationship");
					return;
				}
				else if(def.hasMany(field)) {
					page.setErrorMessage("Duplicate field: '" + field + "' is already a hasMany relationship");
					return;
				}
			}
		}
	}
	
	private void createAttrTypeEditor(final TableEditor editor, final TableItem item) {
		final CCombo combo = new CCombo(item.getParent(), SWT.DROP_DOWN | SWT.READ_ONLY);
		editControl = combo;
		for(String[] type : types) {
			combo.add(type[0]);
		}
		combo.setText(item.getText(1));
		Listener listener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch(event.type) {
				case SWT.FocusOut:
					item.setText(1, combo.getText());
					combo.dispose();
					break;
				case SWT.KeyDown:
					handleKeydown(combo, event.character);
					break;
				case SWT.Traverse:
					switch(event.detail) {
					case SWT.TRAVERSE_RETURN:
						item.setText(1, combo.getText());
						combo.dispose();
						TableItem newItem = new TableItem(item.getParent(), SWT.NONE);
						newItem.setText(new String[] { "", types[0][0] });
						createAttrNameEditor(editor, newItem);
						event.doit = false;
						break;
					case SWT.TRAVERSE_ESCAPE:
						combo.dispose();
						event.doit = false;
						break;
					}
				}
			}
		};
		combo.addListener(SWT.FocusOut, listener);
		combo.addListener(SWT.KeyDown, listener);
		combo.addListener(SWT.Traverse, listener);
		
		combo.setFocus();
		
		editor.setEditor(combo, item, 1);
	}

	private Map<String, Map<String, Object>> properties;

	private Object getProperty(String field, String name) {
		if(properties != null) {
			Map<String, Object> props = properties.get(field);
			if(props != null) {
				return props.get(name);
			}
		}
		return null;
	}
	
	private void setProperty(String field, String name, Object value) {
		if(properties == null) {
			properties = new HashMap<String, Map<String,Object>>();
		}
		Map<String, Object> props = properties.get(field);
		if(props == null) {
			properties.put(field, props = new HashMap<String, Object>());
		}
		props.put(name, value);
	}
	
	private void setSelection(TableItem item) {
		for(Control child : propsComposite.getChildren()) {
			child.dispose();
		}
		
		if(item == null) {
			// set null selection
		}
		else {
			final String name = item.getText(0);

			Button b = new Button(propsComposite, SWT.CHECK);
			b.setText("Read Only");
			b.setToolTipText("This field is read only");
			b.setSelection(coerce(getProperty(name, "readOnly"), DEFAULT_READONLY));
			b.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
			b.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					setProperty(name, "readOnly", ((Button) event.widget).getSelection());
				}
			});

			b = new Button(propsComposite, SWT.CHECK);
			b.setText("Unique");
			b.setToolTipText("Values for this field must be Unique");
			b.setSelection(coerce(getProperty(name, "unique"), DEFAULT_UNIQUE));
			b.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
			b.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					setProperty(name, "unique", ((Button) event.widget).getSelection());
				}
			});

			b = new Button(propsComposite, SWT.CHECK);
			b.setText("JSON");
			b.setToolTipText("Include this field in JSON");
			b.setSelection(coerce(getProperty(name, "json"), DEFAULT_JSON));
			b.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
			b.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					setProperty(name, "json", ((Button) event.widget).getSelection());
				}
			});
			
			if("BigDecimal".equals(item.getText(1))) {
				Composite comp = new Composite(propsComposite, SWT.NONE);
				GridLayout layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				comp.setLayout(layout);
				comp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
				
				Label lbl = new Label(comp, SWT.NONE);
				lbl.setText("Precision");
				lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
				
				Spinner spnr = new Spinner(comp, SWT.BORDER);
				spnr.setSelection(DEFAULT_PRECISION);
				spnr.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
				spnr.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						setProperty(name, "precision", ((Spinner) event.widget).getSelection());
					}
				});
				
				lbl = new Label(comp, SWT.NONE);
				lbl.setText("Scale");
				lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
				
				spnr = new Spinner(comp, SWT.BORDER);
				spnr.setSelection(DEFAULT_SCALE);
				spnr.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
				spnr.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						setProperty(name, "scale", ((Spinner) event.widget).getSelection());
					}
				});
			}
		}
		
		propsComposite.getParent().layout(true, true);
	}
	
	@Override
	protected void createForm() {
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		Composite left = new Composite(this, SWT.NONE);
		layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		left.setLayout(layout);
		left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		Composite right = new Composite(this, SWT.BORDER);
		right.setLayout(new GridLayout());
		right.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Label lbl = new Label(right, SWT.NONE);
		lbl.setText("Properties:");
		lbl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

		propsComposite = new Composite(right, SWT.NONE);
		propsComposite.setLayout(new GridLayout());
		propsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		

		Composite tcomp = new Composite(left, SWT.BORDER);
		layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		tcomp.setLayout(layout);
		tcomp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		ToolBar tb = new ToolBar(tcomp, SWT.BORDER);
		tb.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

		table = new Table(tcomp, SWT.SINGLE | SWT.FULL_SELECTION);
		table.setLinesVisible(true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.widthHint = 300;
		data.heightHint = 150;
		table.setLayoutData(data);
		
		for(String cname : new String[] { "Name", "Type" }) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(cname);
		}

		final TableEditor editor = new TableEditor(table);
		editor.grabHorizontal = true;
		editor.horizontalAlignment = SWT.LEFT;
		
		table.addListener(SWT.MouseDoubleClick, new Listener() {
			@Override
			public void handleEvent(Event event) {
				final TableItem item = table.getItem(new Point(event.x, event.y));
				if(item == null) return;
				if(readonly.contains(item.getText(0))) return;

				if(item.getBounds(0).contains(event.x, event.y)) {
					createAttrNameEditor(editor, item);
				}
				else if(item.getBounds(1).contains(event.x, event.y)) {
					createAttrTypeEditor(editor, item);
				}
			}
		});
		table.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem[] items = table.getSelection();
				if(items.length == 1) {
					setSelection(items[0]);
				} else {
					setSelection(null);
				}
			}
		});


		packColumns();
		
		ToolItem item = new ToolItem(tb, SWT.PUSH);
		item.setToolTipText("Add a new attribute");
		item.setImage(OobiumPlugin.getImage(OobiumPlugin.IMG_ADD));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				cancelEdit();
				TableItem item = addItem("", types[0][0]);
				createAttrNameEditor(editor, item);
			}
		});

		final ToolItem del = new ToolItem(tb, SWT.PUSH);
		item.setToolTipText("Remove selected attribute");
		del.setImage(OobiumPlugin.getImage(OobiumPlugin.IMG_DELETE));
		del.setEnabled(false);
		del.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				cancelEdit();
				TableItem[] ia = table.getSelection();
				for(TableItem ti : ia) {
					ti.dispose();
				}
			}
		});

		up = new ToolItem(tb, SWT.PUSH);
		up.setToolTipText("Move selected attribute up one position");
		up.setImage(OobiumPlugin.getImage(OobiumPlugin.IMG_ARROW_UP));
		up.setEnabled(false);
		up.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveSelectedItemUp(table, up, dn);
			}
		});

		dn = new ToolItem(tb, SWT.PUSH);
		dn.setToolTipText("Move selected attribute down one position");
		dn.setImage(OobiumPlugin.getImage(OobiumPlugin.IMG_ARROW_DOWN));
		dn.setEnabled(false);
		dn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveSelectedItemDown(table, up, dn);
			}
		});

		table.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if(table.getSelectionCount() > 0) {
					boolean enabled = true;
					if(!readonly.isEmpty()) {
						for(TableItem item : table.getSelection()) {
							if(readonly.contains(item.getText(0))) {
								enabled = false;
								break;
							}
						}
					}
					del.setEnabled(enabled);
				} else {
					del.setEnabled(false);
				}
				up.setEnabled(table.getSelectionCount() == 1 && table.getSelectionIndex() > 0);
				dn.setEnabled(table.getSelectionCount() == 1 && table.getSelectionIndex() < table.getItemCount()-1);
			}
		});

		
		Composite acomp = new Composite(left, SWT.NONE);
		layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		acomp.setLayout(layout);
		acomp.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, false));
		
		Composite bcomp = new Composite(acomp, SWT.NONE);
		layout = new GridLayout(2, true);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		bcomp.setLayout(layout);
		bcomp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		timestampsBtn = new Button(bcomp, SWT.CHECK);
		timestampsBtn.setText("Timestamps");
		timestampsBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		datestampsBtn = new Button(bcomp, SWT.CHECK);
		datestampsBtn.setText("Datestamps");
		data = new GridData(SWT.FILL, SWT.CENTER, false, false);
		data.horizontalIndent = 20;
		datestampsBtn.setLayoutData(data);
	}

	private String getFullType(String simpleType) {
		for(String[] type : types) {
			if(type[0].equals(simpleType)) {
				return type[1];
			}
		}
		throw new IllegalArgumentException("unknown type: " + simpleType);
	}

	private String getSimpleType(String fullType) {
		for(String[] type : types) {
			if(type[1].equals(fullType)) {
				return type[0];
			}
		}
		throw new IllegalArgumentException("unknown type: " + fullType);
	}

	protected void handleKeydown(CCombo combo, char character) {
		String txt = (String) combo.getData();
		if(character == 8) { // backspace
			if(txt == null) {
				return;
			} else if(txt.length() == 1) {
				combo.setData(null);
				combo.removeAll();
				for(String[] type : types) {
					combo.add(type[0]);
				}
				combo.select(0);
				return;
			} else {
				txt = txt.substring(0, txt.length() - 1);
			}
		} else {
			if(txt == null) {
				txt = String.valueOf(character);
			} else {
				txt = txt + character;
			}
		}
		txt = txt.toLowerCase();
		combo.setData(txt);
		combo.removeAll();
		for(String[] type : types) {
			if(type[0].toLowerCase().startsWith(txt)) {
				combo.add(type[0]);
			}
		}
		if(combo.getItemCount() == 0) {
			for(String[] type : types) {
				combo.add(type[0]);
			}
		}
		combo.select(0);
	}
	
	@Override
	public void setModel(Object model) {
		clearTable();
		def = (ModelDefinition) model;
		if(def != null) {
			ModelDefinition sysDef = page.getSystemModels().get(def.getSimpleName());
			for(ModelAttribute attribute : def.getAttributes(false)) {
				boolean sysField = sysDef != null && sysDef.hasAttribute(attribute.name());
				String name = attribute.name();
				addItem(name, getSimpleType(attribute.type()), sysField);
				Map<String, Object> props = attribute.getCustomProperties();
				if(!props.isEmpty()) {
					for(Entry<String, Object> entry : props.entrySet()) {
						setProperty(name, entry.getKey(), entry.getValue());
					}
				}
			}
			timestampsBtn.setSelection(def.timestamps());
			datestampsBtn.setSelection(def.datestamps());
			if(table.getItemCount() > 0) {
				table.select(0);
				setSelection(table.getItem(0));
			}
		}
		packColumns();
	}

	private Map<String, Object> getProperties(String name, ModelAttribute attr) {
		Map<String, Object> props = (attr != null) ? attr.getCustomProperties() : new HashMap<String, Object>();
		if(properties != null && properties.containsKey(name)) {
			props.putAll(properties.get(name));
		}
		return props;
	}
	
	@Override
	public void updateModel(Object model) {
		if(model != null) {
			ModelDefinition def = (ModelDefinition) model;
			Set<String> names = new HashSet<String>();
			for(TableItem item : table.getItems()) {
				String name = item.getText(0);
				names.add(name);
				ModelAttribute a = def.getAttribute(name);
				def.addAttribute(name, getFullType(item.getText(1)), getProperties(name, a));
			}
			for(ModelAttribute a : def.getAttributes(false)) {
				if(!names.contains(a.name())) {
					def.remove(a.name());
				}
			}
			def.timestamps(timestampsBtn.getSelection());
			def.datestamps(datestampsBtn.getSelection());
		}
	}
	
}
