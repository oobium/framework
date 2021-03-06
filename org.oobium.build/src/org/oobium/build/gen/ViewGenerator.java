/*******************************************************************************
 * Copyright (c) 2010 Oobium, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Jeremy Dowdall <jeremy@oobium.com> - initial API and implementation
 ******************************************************************************/
package org.oobium.build.gen;

import static org.oobium.utils.FileUtils.writeFile;
import static org.oobium.utils.StringUtils.camelCase;
import static org.oobium.utils.StringUtils.titleize;
import static org.oobium.utils.StringUtils.varName;

import java.io.File;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;

import org.oobium.build.gen.model.PropertyDescriptor;
import org.oobium.build.model.ModelDefinition;
import org.oobium.persist.Paginator;
import org.oobium.persist.Text;
import org.oobium.utils.StringUtils;

public class ViewGenerator {

	public static File createScript(File folder, String name, String content) {
		String path = name;
		if(!path.endsWith(".ejs")) {
			path = path + ".ejs";
		}
		
		return writeFile(new File(folder, path), content);
	}
	
	public static File createStyle(File folder, String name, String content) {
		String path = name;
		if(!path.endsWith(".ess")) {
			path = path + ".ess";
		}
		
		return writeFile(new File(folder, path), content);
	}
	
	public static File createView(File folder, String name, String content) {
		String path = name;
		if(!path.endsWith(".esp")) {
			path = path + ".esp";
		}
		
		return writeFile(new File(folder, path), content);
	}
	

	public LinkedHashMap<String, PropertyDescriptor> properties;
	private String mPkg;
	private String mType;
	private String mVar;
	private String mVarPlural;

	public ViewGenerator(ModelDefinition model) {
		properties = model.getProperties();

		mPkg = model.getPackageName();
		mType = model.getSimpleName();
		mVar = varName(mType);
		mVarPlural = varName(mType, true);
	}

	public ViewGenerator(String mType) {
		this.mType = mType;
	}

	private void generateLabelAndField(StringBuilder sb, PropertyDescriptor property, String var) {
		String ftype = property.fullType();
		if(is(ftype, boolean.class)) {
			sb.append("\t\tcheck(\"").append(var).append("\")\n");
			sb.append("\t\tlabel(\"").append(var).append("\")\n");
		}
		else if(is(ftype, Boolean.class)) {
			sb.append("\t\tlabel(\"").append(var).append("\")\n");
			sb.append("\t\tselect(\"").append(var).append("\")" +
					" <- options(new String[][] { " +
									"{\"<none>\",null}, " +
									"{\"True\",1}, " +
									"{\"False\",0}" +
								" })\n");
		}
		else {
			sb.append("\t\tdiv <- label(\"").append(var).append("\")\n");
			sb.append("\t\tdiv <- ");
			if(is(ftype, String.class)) {
				if("password".equalsIgnoreCase(var)) {
					sb.append("password");
				} else if(is(property.rawType(), Text.class)) {
					sb.append("textArea");
				} else {
					sb.append("text");
				}
			} else if(property.hasOne()) {
				sb.append("select");
			} else if(is(ftype, int.class, Integer.class, double.class, Double.class, long.class, Long.class)) {
				sb.append("number");
			} else if(property.hasMany()) {
				sb.append("span hasMany");
			} else if(is(ftype, Date.class, java.sql.Date.class, Timestamp.class)) {
				sb.append("date");
			} else {
				sb.append("input");
			}
			sb.append("(\"").append(var).append("\")");
			if(property.hasOne()) {
				String type = StringUtils.simpleName(property.relatedType());
				sb.append(" <- options(").append(type).append(".findAll())\n");
			} else {
				sb.append('\n');
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
	
	public String generateForm() {
		StringBuilder sb = new StringBuilder();

		sb.append("import ").append(mPkg).append(".*\n");
		sb.append('\n');
		sb.append(mType).append("Form(").append(mType).append(' ').append(mVar).append(')').append('\n');
		sb.append('\n');
		sb.append("form(").append(mVar).append(")\n");
		sb.append("\terrors\n");
		for(PropertyDescriptor property : properties.values()) {
			String var = property.variable();
			if("createdAt".equals(var) || "updatedAt".equals(var) || "createdOn".equals(var) || "updatedOn".equals(var)) {
				continue; // not normally user-editable fields
			}
			sb.append("\tdiv.field\n");
			generateLabelAndField(sb, property, var);
		}
		sb.append("\tdiv.actions\n");
		sb.append("\t\tsubmit\n");

		return sb.toString();
	}

	public String generateLayout() {
		StringBuilder sb = new StringBuilder();

		sb.append("title ").append(mType).append(": { titleize(getChild().getClass().getSimpleName()) }\n");
		sb.append('\n');
		sb.append("head\n");
		sb.append("\tscript(defaults)\n");
		sb.append("\tstyle(defaults)\n");
		sb.append('\n');
		sb.append("div <- yield\n");

		return sb.toString();
	}

	public String generateLoginForm() {
		return
		"import " + mPkg + ".*\n" + 
		"Login(String email = \"login\")\n" + 
		"\n" + 
		"title Log In\n" + 
		"\n" + 
		"div#logPage\n" + 
		"\tmessages\n" + 
		"\tdiv.Login\n" + 
		"\t\tform(action: pathTo(Session.class, Action.create), method: \"post\")\n" + 
		"\t\t\t- if(hasParam(\"goto\")) {\n" + 
		"\t\t\t\t\thidden(name: \"goto\", value: param(\"goto\"))\n" + 
		"\t\t\t- }\n" + 
		"\t\t\ttext.name(name: \"email\", value: email, onfocus: \"enterText(this, 'login');\", onblur: \"exitText(this, 'login');\")\n" + 
		"\t\t\tpassword.pass(name: \"password\")\n" + 
		"\t\t\tbutton#login Submit";
	}

	public String generateShowAllView() {
		StringBuilder sb = new StringBuilder();

		sb.append("import java.util.List\n");
		sb.append("import ").append(Paginator.class.getCanonicalName()).append('\n');
		sb.append("import ").append(mPkg).append(".*\n");
		sb.append('\n');
		sb.append("ShowAll").append(camelCase(mVarPlural)).append("(List<").append(mType).append("> ").append(mVarPlural).append(")\n");
		sb.append("ShowAll").append(camelCase(mVarPlural)).append("(Paginator<").append(mType).append("> paginator)\n");
		sb.append('\n');
		sb.append("h1 Listing ").append(titleize(mVarPlural)).append('\n');
		sb.append('\n');
		sb.append("p <- a(").append(mType).append(".class, showNew) New ").append(mType).append('\n');
		sb.append('\n');
		sb.append("table\n");
		sb.append("\ttr\n");
		sb.append("\t\tth ").append("id").append('\n');
		for(PropertyDescriptor property : properties.values()) {
			sb.append("\t\tth ").append(titleize(property.variable())).append('\n');
		}
		sb.append("\t- for(").append(mType).append(" ").append(mVar).append(" : ").append(mVarPlural).append(") {\n");
		sb.append("\t\ttr\n");
		sb.append("\t\t\ttd { ").append(mVar).append(".getId() }\n");
		for(PropertyDescriptor property : properties.values()) {
			sb.append("\t\t\ttd { ").append(mVar).append(".").append(property.getterName()).append("() }\n");
		}
		sb.append("\t\t\ttd <- a(").append(mVar).append(", show) Show\n");
		sb.append("\t\t\ttd <- a(").append(mVar).append(", showEdit) Edit\n");
		sb.append("\t\t\ttd <- a(").append(mVar).append(", destroy, confirm: \"Are you sure?\") Delete\n");
		sb.append("\t- }\n");

		return sb.toString();
	}
	
	public String generateShowEditView() {
		StringBuilder sb = new StringBuilder();

		sb.append("import ").append(mPkg).append(".*\n");
		sb.append('\n');
		sb.append("ShowEdit").append(mType).append('(').append(mType).append(' ').append(mVar).append(')').append('\n');
		sb.append('\n');
		sb.append("h1 Editing ").append(mVar).append('\n');
		sb.append('\n');
		sb.append("view<").append(mType).append("Form>(").append(mVar).append(")\n");
		sb.append('\n');
		sb.append("a(").append(mVar).append(", show) Show\n");
		sb.append("span  | \n");
		sb.append("a(").append(mType).append(".class, showAll) Back\n");

		return sb.toString();
	}
	
	public String generateShowNewView() {
		StringBuilder sb = new StringBuilder();

		sb.append("import ").append(mPkg).append(".*\n");
		sb.append('\n');
		sb.append("ShowNew").append(mType).append('(').append(mType).append(' ').append(mVar).append(')').append('\n');
		sb.append('\n');
		sb.append("h1 New ").append(mVar).append('\n');
		sb.append('\n');
		sb.append("view<").append(mType).append("Form>(").append(mVar).append(")\n");
		sb.append('\n');
		sb.append("a(").append(mType).append(".class, showAll) Back\n");

		return sb.toString();
	}

	public String generateShowView() {
		StringBuilder sb = new StringBuilder();

		sb.append("import ").append(mPkg).append(".*\n");
		sb.append('\n');
		sb.append("Show").append(mType).append('(').append(mType).append(' ').append(mVar).append(')').append('\n');
		sb.append('\n');
		for(PropertyDescriptor property : properties.values()) {
			sb.append("p\n");
			sb.append("\tb ").append(titleize(property.variable())).append(":\n");
			sb.append("\t+  { ").append(mVar).append(".").append(property.getterName()).append("() }\n");
		}
		sb.append('\n');
		sb.append("a(").append(mVar).append(", showEdit) Edit\n");
		sb.append("span  | \n");
		sb.append("a(").append(mType).append(".class, showAll) Back\n");

		return sb.toString();
	}

}
