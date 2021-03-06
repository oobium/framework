package org.oobium.build.clients.android;

import static org.oobium.utils.CharStreamUtils.closer;
import static org.oobium.utils.CharStreamUtils.find;
import static org.oobium.utils.CharStreamUtils.findAll;
import static org.oobium.utils.CharStreamUtils.forward;
import static org.oobium.utils.CharStreamUtils.reverse;
import static org.oobium.utils.FileUtils.copy;
import static org.oobium.utils.FileUtils.readFile;
import static org.oobium.utils.FileUtils.writeFile;
import static org.oobium.utils.StringUtils.constant;
import static org.oobium.utils.StringUtils.getResourceAsString;
import static org.oobium.utils.StringUtils.plural;
import static org.oobium.utils.StringUtils.repeat;
import static org.oobium.utils.StringUtils.titleize;
import static org.oobium.utils.StringUtils.underscored;
import static org.oobium.utils.StringUtils.varName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.oobium.build.clients.android.GeneratorEvent.Type;
import org.oobium.build.gen.model.PropertyDescriptor;
import org.oobium.build.model.ModelDefinition;
import org.oobium.build.workspace.AndroidApp;
import org.oobium.build.workspace.Module;
import org.oobium.persist.Text;

public class ScaffoldingGenerator {

	private static final String buttonXml =
		"<Button\n" +
		"\tandroid:id=\"@+id/{modelsVar}\"\n" +
		"\tandroid:text=\"{modelsName}\"\n" +
	    "\tandroid:layout_width=\"fill_parent\"\n" +
	    "\tandroid:layout_height=\"wrap_content\" />";

	private static final String buttonSrc =
        "\t\t((Button) findViewById(R.id.{modelsVar})).setOnClickListener(new OnClickListener() {\n" +
		"\t\t\t@Override\n" +
		"\t\t\tpublic void onClick(View v) {\n" +
		"\t\t\t\tIntent intent = new Intent({activityName}.this, ShowAll{modelsName}.class);\n" +
		"\t\t\t\tstartActivity(intent);\n" +
		"\t\t\t}\n" +
		"\t\t});";
	
	private static final String checkBox =
		"{i}<CheckBox\n" +
		"{i}\tandroid:id=\"@+id/{field}\"\n" +
		"{i}\tandroid:text=\"{Field}\"\n" +
		"{i}\tandroid:layout_width=\"fill_parent\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\" />\n";

	private static final String editText =
		"{i}<EditText\n" +
		"{i}\tandroid:id=\"@+id/{field}\"\n" +
		"{i}\tandroid:hint=\"{Field}\"\n" +
		"{i}\tandroid:inputType=\"{type}\"\n" +
		"{i}\tandroid:layout_width=\"fill_parent\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\" />\n";

	private static final String multilineEditText =
		"{i}<EditText\n" +
		"{i}\tandroid:id=\"@+id/{field}\"\n" +
		"{i}\tandroid:hint=\"{Field}\"\n" +
		"{i}\tandroid:inputType=\"textMultiLine\"\n" +
		"{i}\tandroid:lines=\"5\"\n" +
		"{i}\tandroid:gravity=\"top\"\n" +
		"{i}\tandroid:layout_width=\"fill_parent\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\" />\n";
	
	private static final String inlineField =
		"{i}<TextView\n" +
		"{i}\tandroid:id=\"@+id/{field}\"\n" +
		"{i}\tandroid:layout_width=\"wrap_content\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\"\n" +
		"{i}\tandroid:gravity=\"center_vertical\" />\n";
	
	private static final String stackedField =
		"{i}<TextView\n" +
		"{i}\tandroid:text=\"{Field}:\"\n" +
		"{i}\tandroid:layout_width=\"wrap_content\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\" />\n" +
		"{i}<TextView\n" +
		"{i}\tandroid:id=\"@+id/{field}\"\n" +
		"{i}\tandroid:paddingLeft=\"5dip\"\n" +
		"{i}\tandroid:layout_width=\"wrap_content\"\n" +
		"{i}\tandroid:layout_height=\"wrap_content\" />\n";

	
	private Module module;
	private AndroidApp android;

	private GeneratorListener listener;
	
	public ScaffoldingGenerator(Module module, AndroidApp androidApp) {
		this.android = androidApp;
		this.module = module;
	}
	
	public File generateLaunchButton(File activity) {
		String src = readFile(activity).toString();
		Pattern p = Pattern.compile("public\\s+void\\s+onCreate\\s*\\(");
		Matcher m = p.matcher(src);
		if(m.find()) {
			char[] ca = src.toCharArray();
			int start = find(ca, '{');
			int end = closer(ca, start);
			if(start != -1 && end != -1) {
				p = Pattern.compile("setContentView\\s*\\(\\s*([\\w\\.]+)\\s*\\)");
				m = p.matcher(new String(ca, start, end-start+1));
				if(m.find()) {
					File layout = android.getLayout(m.group(1));
					if(layout.isFile()) {
						String projectPackage = android.packageName(android.main);
						String activityName = activity.getName();
						activityName = activityName.substring(0, activityName.length() - 5);
	
						StringBuilder imports = new StringBuilder();
						StringBuilder buttonsXml = new StringBuilder();
						StringBuilder buttonsSrc = new StringBuilder();
						buttonsSrc.append("\n\t\t// TODO auto-generated code\n");
						for(File model : module.findModels()) {
							String modelName = module.getModelName(model);
							String modelsName = plural(modelName);
							String modelsVar = varName(modelName, true);
							imports.append("import ").append(projectPackage).append(".activities.").append(modelsVar).append(".ShowAll").append(modelsName).append(";\n");
							buttonsXml.append(buttonXml.replace("{modelsVar}", modelsVar).replace("{modelsName}", modelsName)).append('\n');
							buttonsSrc.append(buttonSrc.replace("{modelsVar}", modelsVar).replace("{activityName}", activityName).replace("{modelsName}", modelsName)).append("\n\n");
						}
						buttonsSrc.setCharAt(buttonsSrc.length()-1, '\t');
	
						StringBuilder sb = new StringBuilder(src.length() + buttonsSrc.length() + 15);
						sb.append(src);
						sb.insert(reverse(ca, end-1), buttonsSrc);
						int s = findAll(ca, 0, "package ".toCharArray());
						s = find(ca, '\n', s);
						s = forward(ca, s);
						sb.insert(s-1, '\n');
						sb.insert(s, imports);
						if(!Pattern.compile("import\\s+android\\.widget\\.Button\\s*;").matcher(src).find()) {
							sb.insert(s, "import android.widget.Button;\n");
						}
						if(!Pattern.compile("import\\s+android\\.view\\.View\\.OnClickListener\\s*;").matcher(src).find()) {
							sb.insert(s, "import android.view.View.OnClickListener;\n");
						}
						if(!Pattern.compile("import\\s+android\\.view\\.View\\s*;").matcher(src).find()) {
							sb.insert(s, "import android.view.View;\n");
						}
						if(!Pattern.compile("import\\s+android\\.content\\.Intent\\s*;").matcher(src).find()) {
							sb.insert(s, "import android.content.Intent;\n");
						}
						writeFile(activity, sb.toString());
						
						src = readFile(layout).toString();
						ca = src.toCharArray();
	
						sb = new StringBuilder(src.length() + buttonsXml.length() + 15);
						sb.append(src);
						sb.insert(reverse(ca, '<', ca.length - 1), buttonsXml);
						writeFile(layout, sb.toString());
						
						return layout;
					}
				}
			}
		}
		
		return null;
	}
	
	public List<File> generateScaffolding() {
		List<File> files = new ArrayList<File>();
		try {
			for(File model : module.findModels()) {
				notify("  generating scaffolding for " + module.getModelName(model));
				files.addAll(scaffold(model));
			}
			notify("  adding Internet permission to Android manifest file");
			android.addPermission("android.permission.INTERNET");
			
			if(module.addDiscoveryRoute("/api", true)) {
				files.add(module.activator);
			}
			
			File server = new File(android.src, "oobium.server");
			if(!server.exists()) {
				files.add(writeFile(server, "10.0.2.2:5000"));
			}
		} catch(IOException e) {
			notify(e);
		}
		return files;
	}

	private String getCheckBox(String field, int indent) {
		return checkBox.replace("{i}", repeat('\t', indent)).replace("{field}", field).replace("{Field}", titleize(field));
	}

	private String getEditResField(PropertyDescriptor p, int indent) {
		String var = p.variable();
		String ftype = p.fullType();
		if(is(ftype, boolean.class, Boolean.class)) {
			return getCheckBox(var, indent);
		} else {
			if(is(ftype, String.class)) {
				if("password".equalsIgnoreCase(var)) {
					return getEditText(var, indent, "textPassword");
				} else if(is(p.rawType(), Text.class)) {
					return getEditText(var, indent, "textMultiLine");
				} else {
					return getEditText(var, indent, "text");
				}
			} else if(p.hasOne()) {
				return getEditText(var, indent, "number");
			} else if(is(ftype, int.class, Integer.class, long.class, Long.class)) {
				return getEditText(var, indent, "number|numberSigned");
			} else if(is(ftype, double.class, Double.class, short.class, Short.class)) {
				return getEditText(var, indent, "number|numberSigned|numberDecimal");
			} else if(p.hasMany()) {
				return "";
			} else if(is(ftype, Date.class, Timestamp.class)) {
				if(var.equals("createdAt") || var.equals("updatedAt")) {
					return "";
				}
				return getEditText(var, indent, "datetime");
			} else if(is(ftype, java.sql.Date.class)) {
				if(var.equals("createdOn") || var.equals("updatedOn")) {
					return "";
				}
				return getEditText(var, indent, "date");
			} else {
				return getEditText(var, indent, "text");
			}
		}
	}

	private String getEditText(String field, int indent, String type) {
		if("textMultiLine".equals(type)) {
			return multilineEditText.replace("{i}", repeat('\t', indent)).replace("{field}", field).replace("{Field}", titleize(field));
		}
		return editText.replace("{i}", repeat('\t', indent)).replace("{field}", field).replace("{Field}", titleize(field)).replace("{type}", type);
	}

	private String getImports(Collection<PropertyDescriptor> properties) {
		Set<String> imports = new TreeSet<String>();
		for(PropertyDescriptor property : properties) {
			String ftype = property.fullType();
			if(is(ftype, boolean.class, Boolean.class)) {
				imports.add("android.widget.CheckBox");
			} else {
				if(is(ftype, String.class)) {
					imports.add("android.widget.EditText");
				} else if(property.hasOne()) {
					// TODO hasOne
				} else if(is(ftype, int.class, long.class, double.class, short.class, Double.class, Integer.class, Long.class, Short.class)) {
					imports.add("android.widget.EditText");
				} else if(property.hasMany()) {
					// TODO hasMany
				} else if(is(ftype, Date.class, Timestamp.class)) {
					String var = property.variable();
					if(!var.equals("createdAt") && !var.equals("updatedAt")) {
						imports.add("android.widget.EditText");
						imports.add("java.text.DateFormat");
						imports.add("java.text.ParseException");
					}
				} else if(is(ftype, Date.class, Timestamp.class, java.sql.Date.class)) {
					String var = property.variable();
					if(!var.equals("createdOn") && !var.equals("updatedOn")) {
						imports.add("android.widget.EditText");
						imports.add("java.text.DateFormat");
						imports.add("java.text.ParseException");
					}
				} else {
					imports.add("android.widget.EditText");
				}
			}
		}
		if(imports.isEmpty()) {
			return "";
		} else {
			StringBuilder sb = new StringBuilder();
			for(String imp : imports) {
				sb.append("import ").append(imp).append(";\n");
			}
			return sb.toString();
		}
	}

	private String getInits(Collection<PropertyDescriptor> properties) {
		Set<String> inits = new TreeSet<String>();
		for(PropertyDescriptor property : properties) {
			String var = property.variable();
			String ftype = property.fullType();
			if(is(ftype, Date.class, Timestamp.class)) {
				if(!var.equals("createdAt") && !var.equals("updatedAt")) {
					inits.add("\tprivate DateFormat __dtf = DateFormat.getDateTimeInstance();\n");
				}
			} else if(is(ftype, java.sql.Date.class)) {
				if(!var.equals("createdOn") && !var.equals("updatedOn")) {
					inits.add("\tprivate DateFormat __df = DateFormat.getDateInstance();\n");
				}
			}
		}
		if(inits.isEmpty()) {
			return "";
		} else {
			StringBuilder sb = new StringBuilder();
			for(String init : inits) {
				sb.append(init);
			}
			return sb.delete(0, 1).delete(sb.length()-1, sb.length()).toString();
		}
	}

	private String getInlineField(String field, int indent) {
		return inlineField.replace("{i}", repeat('\t', indent)).replace("{field}", field);
	}
	
	private String getModelFieldSetter(String modelName, String modelVar, PropertyDescriptor p) {
		String var = p.variable();
		String ftype = p.fullType();
		if(is(ftype, boolean.class, Boolean.class)) {
			return	"\t\tboolean " + var + " = ((CheckBox) findViewById(R.id." + var + ")).isChecked();\n" +
					"\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", " + var + ");\n";
		} else {
			if(is(ftype, String.class)) {
				return	"\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", " + var + ");\n";
			} else if(p.hasOne()) {
				return	"\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", " + var + ");\n";
			} else if(is(ftype, int.class, long.class, double.class, short.class)) {
				return	"\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", " + var + ");\n";
			} else if(is(ftype, Double.class, Integer.class, Long.class, Short.class)) {
				return	"\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", " + var + ");\n";
			} else if(p.hasMany()) {
				return "\t\t// TODO: hasMany\n";
			} else if(is(ftype, Date.class, Timestamp.class)) {
				if(var.equals("createdAt") || var.equals("updatedAt")) {
					return "";
				}
				return	"\t\ttry {\n" +
						"\t\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", __dtf.parse(" + var + "));\n" +
						"\t\t} catch (ParseException e) {\n" +
						"\t\t\te.printStackTrace();\n" +
						"\t\t}\n";
			} else if(is(ftype, java.sql.Date.class)) {
				if(var.equals("createdOn") || var.equals("updatedOn")) {
					return "";
				}
				return	"\t\ttry {\n" +
						"\t\t\tString " + var + " = ((EditText) findViewById(R.id." + var + ")).getText().toString();\n" +
						"\t\t\t" + modelVar + ".set(" + modelName + "." + constant(var) + ", __df.parse(" + var + "));\n" +
						"\t\t} catch (ParseException e) {\n" +
						"\t\t\te.printStackTrace();\n" +
						"\t\t}\n";
			} else {
				return "\t\t((EditText) view.findViewById(R.id." + var + ")).setText(String.valueOf(" + modelVar + "." + p.getterName() + "()));\n";
			}
		}
	}

	private String getStackedField(String field, int indent) {
		return stackedField.replace("{i}", repeat('\t', indent)).replace("{field}", field).replace("{Field}", titleize(field));
	}
	
	private String getViewEditFieldSetter(String modelVar, PropertyDescriptor p) {
		String var = p.variable();
		String ftype = p.fullType();
		if(is(ftype, boolean.class, Boolean.class)) {
			return "\t\t((CheckBox) view.findViewById(R.id." + var + ")).setChecked(" + modelVar + "." + p.getterName() + "());\n";
		} else {
			if(is(ftype, String.class)) {
				return "\t\t((EditText) view.findViewById(R.id." + var + ")).setText(" + modelVar + "." + p.getterName() + "());\n";
			} else if(p.hasOne()) {
				return	"\t\tif(" + modelVar + "." + p.hasserName() + "()) {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(String.valueOf(" + modelVar + "." + p.getterName() + "().getId()));\n" +
						"\t\t} else {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(null);\n" +
						"\t\t}\n";
			} else if(is(ftype, int.class, long.class, double.class, short.class)) {
				return "\t\t((EditText) view.findViewById(R.id." + var + ")).setText(String.valueOf(" + modelVar + "." + p.getterName() + "()));\n";
			} else if(is(ftype, Double.class, Integer.class, Long.class, Short.class)) {
				return	"\t\tif(" + modelVar + "." + p.hasserName() + "()) {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(String.valueOf(" + modelVar + "." + p.getterName() + "()));\n" +
						"\t\t} else {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(null);\n" +
						"\t\t}\n";
			} else if(p.hasMany()) {
				return "\t\t// TODO: hasMany\n";
			} else if(is(ftype, Date.class, Timestamp.class)) {
				if(var.equals("createdAt") || var.equals("updatedAt")) {
					return "";
				}
				return	"\t\tif(" + modelVar + "." + p.hasserName() + "()) {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(__dtf.format(" + modelVar + "." + p.getterName() + "()));\n" +
						"\t\t} else {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(null);\n" +
						"\t\t}\n";
			} else if(is(ftype, java.sql.Date.class)) {
				if(var.equals("createdOn") || var.equals("updatedOn")) {
					return "";
				}
				return	"\t\tif(" + modelVar + "." + p.hasserName() + "()) {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(__df.format(" + modelVar + "." + p.getterName() + "()));\n" +
						"\t\t} else {\n" +
						"\t\t\t((EditText) view.findViewById(R.id." + var + ")).setText(null);\n" +
						"\t\t}\n";
			} else {
				return "\t\t((EditText) view.findViewById(R.id." + var + ")).setText(String.valueOf(" + modelVar + "." + p.getterName() + "()));\n";
			}
		}
	}
	
	private boolean is(String fullType, Class<?>...classes) {
		for(Class<?> clazz : classes) {
			if(fullType.equals(clazz.getCanonicalName())) {
				return true;
			}
		}
		return false;
	}
	
	private void notify(Object data) {
		if(listener != null) {
			GeneratorEvent event = new GeneratorEvent(Type.Logging, data);
			listener.handleEvent(event);
		}
	}
	
	private List<File> scaffold(File file) throws IOException {
		List<File> files = new ArrayList<File>();
		
		String projectPackage = android.packageName(android.main);
		String modelPackage = module.packageName(file);
		String modelName = module.getModelName(file);
		String modelsName = plural(modelName);
		String modelVar = varName(modelName);
		String modelsVar = varName(modelName, true);
		String umodelsVar = underscored(modelsVar);
		String umodelVar = underscored(modelVar);
		
		ModelDefinition model = new ModelDefinition(file);
		Collection<PropertyDescriptor> properties = model.getProperties().values();
		
		StringBuilder sb = new StringBuilder();
		List<String> includes = new ArrayList<String>();
		for(PropertyDescriptor property : properties) {
			if(property.hasMany()) {
				includes.add(property.variable());
			}
		}
		if(includes.size() == 1) {
			sb.append("\"include:").append(includes.get(0)).append('"');
		} else if(!includes.isEmpty()) {
			sb.append("\"include:[");
			for(String include : includes) {
				sb.append(include).append(',');
			}
			sb.deleteCharAt(sb.length()-1).append("]\"");
		}
		String include = sb.toString();

		sb = new StringBuilder();
		for(PropertyDescriptor property : properties) {
			sb.append(getModelFieldSetter(modelName, modelVar, property));
		}
		String setModelFields = sb.delete(0, 2).deleteCharAt(sb.length()-1).toString();
		
		sb = new StringBuilder();
		for(PropertyDescriptor property : properties) {
			sb.append(getViewEditFieldSetter(modelVar, property));
		}
		String setViewEditFields = sb.delete(0, 2).deleteCharAt(sb.length()-1).toString();

		sb = new StringBuilder();
		for(PropertyDescriptor p : properties) {
			String var = p.variable();
			sb.append("\t\t((TextView) view.findViewById(R.id.").append(var).append(")).setText(\"");
			sb.append(titleize(var)).append(": \" + ").append(modelVar).append(".").append(p.getterName()).append("());\n");
		}
		String setViewFields = sb.delete(0, 2).deleteCharAt(sb.length()-1).toString();

		sb = new StringBuilder();
		for(PropertyDescriptor p : properties) {
			String var = p.variable();
			if(p.hasMany()) {
				sb.append("\t\t((TextView) view.findViewById(R.id.").append(var).append(")).setText(");
				sb.append(modelVar).append(".").append(p.getterName()).append("().toString());\n");
			} else {
				sb.append("\t\t((TextView) view.findViewById(R.id.").append(var).append(")).setText(");
				sb.append(modelVar).append(".").append(p.hasserName()).append("() ? ");
				sb.append(modelVar).append(".").append(p.getterName()).append("().toString() : null);\n");
			}
		}
		String setViewStackedFields = sb.delete(0, 2).deleteCharAt(sb.length()-1).toString();

		File dstFolder = new File(android.main, "activities" + File.separator + modelsVar);

		String[] resources = new String[] {
				"src/ModelAdapter.java.android",
				"src/ShowModel.java.android",
				"src/ShowAllModels.java.android",
				"src/ShowEditModel.java.android",
				"src/ShowNewModel.java.android",
		};

		for(String resource : resources) {
			String src = getResourceAsString(getClass(), resource);
			src = src.replace("{projectPackage}", projectPackage);
			src = src.replace("{modelPackage}", modelPackage);
			src = src.replace("{modelName}", modelName);
			src = src.replace("{modelsName}", modelsName);
			src = src.replace("{modelVar}", modelVar);
			src = src.replace("{modelsVar}", modelsVar);
			src = src.replace("{umodelVar}", umodelVar);
			src = src.replace("{umodelsVar}", umodelsVar);
			src = src.replace("{setModelFields}", setModelFields);
			if(resource.equals("src/ShowModel.java.android")) {
				src = src.replace("{include}", include.isEmpty() ? "" : (", " + include));
			} else if(resource.equals("src/ShowAllModels.java.android")) {
				src = src.replace("{include}", include);
			}
			if(resource.contains("Edit")) {
				src = src.replace("{setViewFields}", setViewEditFields);
			} else if(resource.contains("Show")) {
				src = src.replace("{setViewFields}", setViewStackedFields);
			} else {
				src = src.replace("{setViewFields}", setViewFields);
			}
			src = src.replace("{imports}", getImports(properties));
			src = src.replace("{inits}", getInits(properties));
			if(resource.contains("Models")) {
				resource = resource.replace("Models", modelsName);
			} else {
				resource = resource.replace("Model", modelName);
			}
			files.add(writeFile(dstFolder, resource.substring(4, resource.lastIndexOf('.')), src));
		}
		
		// add image resources
		dstFolder = new File(android.file, "res" + File.separator + "drawable");
		if(!dstFolder.exists()) {
			dstFolder.mkdirs();
		}
		
		File dst = new File(dstFolder, "destroy.gif");
		if(!dst.isFile()) {
			InputStream in = getClass().getResourceAsStream("res/destroy.gif");
			files.add(copy(in, dst));
		}
		
		dst = new File(dstFolder, "edit.png");
		if(!dst.isFile()) {
			InputStream in = getClass().getResourceAsStream("res/edit.png");
			files.add(copy(in, dst));
		}
		
		// add layout resources
		dstFolder = new File(android.file, "res" + File.separator + "layout");
		if(!dstFolder.exists()) {
			dstFolder.mkdirs();
		}
		
		int indent = 3;
		sb = new StringBuilder();
		for(PropertyDescriptor property : properties) {
			sb.append(getEditResField(property, indent));
		}
		String editFields = sb.delete(0, indent).deleteCharAt(sb.length()-1).toString();

		sb = new StringBuilder();
		for(PropertyDescriptor p : properties) {
			sb.append(getInlineField(p.variable(), indent));
		}
		String inlineFields = sb.delete(0, indent).deleteCharAt(sb.length()-1).toString();

		sb = new StringBuilder();
		for(PropertyDescriptor p : properties) {
			sb.append(getStackedField(p.variable(), indent));
		}
		String stackedFields = sb.delete(0, indent).deleteCharAt(sb.length()-1).toString();

		resources = new String[] {
				"res/model_item.xml",
				"res/show_model.xml",
				"res/show_all_models.xml",
				"res/show_edit_model.xml",
				"res/show_new_model.xml",
		};

		for(String resource : resources) {
			String src = getResourceAsString(getClass(), resource);
			src = src.replace("{modelName}", modelName);
			src = src.replace("{modelsName}", modelsName);
			src = src.replace("{editFields}", editFields);
			src = src.replace("{inlineFields}", inlineFields);
			src = src.replace("{stackedFields}", stackedFields);
			if(resource.contains("models")) {
				resource = resource.replace("models", umodelsVar);
			} else {
				resource = resource.replace("model", umodelVar);
			}
			files.add(writeFile(dstFolder, resource.substring(4), src));
		}

		// add activities to manifest file
		notify("  adding activities to Android manifest file");
		android.addActivity(".activities." + modelsVar + ".Show" + modelName);
		android.addActivity(".activities." + modelsVar + ".ShowAll" + modelsName);
		android.addActivity(".activities." + modelsVar + ".ShowEdit" + modelName);
		android.addActivity(".activities." + modelsVar + ".ShowNew" + modelName);
		files.add(android.manifest);
		
		return files;
	}
	
	public void setListener(GeneratorListener listener) {
		this.listener = listener;
	}
	
}
