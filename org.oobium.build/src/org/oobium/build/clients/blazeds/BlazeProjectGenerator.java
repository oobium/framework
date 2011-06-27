package org.oobium.build.clients.blazeds;

import static org.oobium.utils.FileUtils.*;
import static org.oobium.utils.StringUtils.*;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.oobium.build.clients.JavaClientExporter;
import org.oobium.build.gen.model.PropertyDescriptor;
import org.oobium.build.model.ModelDefinition;
import org.oobium.build.util.SourceFile;
import org.oobium.build.workspace.Module;
import org.oobium.build.workspace.Project;
import org.oobium.build.workspace.Workspace;

public class BlazeProjectGenerator {

	private final Workspace workspace;
	private final Module module;
	private final File project;
	
	private boolean exportFlex;
	private File flexProject;
	
	private boolean force;
	
	public BlazeProjectGenerator(Workspace workspace, Module module) {
		this(workspace, module, null);
	}
	
	public BlazeProjectGenerator(Workspace workspace, Module module, File project) {
		this.workspace = workspace;
		this.module = module;
		if(project == null) {
			project = module.file.getParentFile();
		}
		if(project.isDirectory()) {
			this.project = new File(project, module.name + ".blazeds");
		} else {
			this.project = project;
		}
	}
	
	public void create() throws IOException {
		if(force) {
			deleteContents(project);
		}
		else if(project.exists()) {
			throw new UnsupportedOperationException(project.getName() + " already exists");
		}

		createClassPathFile();
		createProjectFile();
		createPrefsFile();
		
		createFolder(project, "bin");
		File src = createFolder(project, "src");

		Project target = workspace.load(project);
		JavaClientExporter javaExporter = new JavaClientExporter(workspace, module);
		javaExporter.setTarget(target);
		javaExporter.export();
		
		for(File file : module.findModels()) {
			ModelDefinition model = new ModelDefinition(file);
			createController(src, model);
			createModel(src, model);
		}

		if(exportFlex) {
			FlexProjectGenerator flex = new FlexProjectGenerator(module, flexProject);
			flex.setForce(force);
			flexProject = flex.create();
		}
	}

	private void createClassPathFile() {
		writeFile(project, ".classpath",
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<classpath>\n" +
				"\t<classpathentry kind=\"src\" path=\"src\"/>\n" +
				"\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6\"/>\n" +
				"\t<classpathentry kind=\"output\" path=\"bin\"/>\n" +
				"</classpath>"
			);
	}
	
	private void createProjectFile() {
		writeFile(project, ".project",
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<projectDescription>\n" +
				"\t<name>" + project.getName() + "</name>\n" +
				"\t<comment></comment>\n" +
				"\t<projects>\n" +
				"\t</projects>\n" +
				"\t<buildSpec>\n" +
				"\t\t<buildCommand>\n" +
				"\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n" +
				"\t\t\t<arguments>\n" +
				"\t\t\t</arguments>\n" +
				"\t\t</buildCommand>\n" +
				"\t</buildSpec>\n" +
				"\t<natures>\n" +
				"\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n" +
				"\t</natures>\n" +
				"</projectDescription>\n"
			);
	}
	
	private void createPrefsFile() {
		writeFile(createFolder(project, ".settings"), "org.eclipse.jdt.core.prefs",
				"#Sat Jun 25 15:21:51 MDT 2011\n" +
				"eclipse.preferences.version=1\n" +
				"org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled\n" +
				"org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.6\n" +
				"org.eclipse.jdt.core.compiler.codegen.unusedLocal=preserve\n" +
				"org.eclipse.jdt.core.compiler.compliance=1.6\n" +
				"org.eclipse.jdt.core.compiler.debug.lineNumber=generate\n" +
				"org.eclipse.jdt.core.compiler.debug.localVariable=generate\n" +
				"org.eclipse.jdt.core.compiler.debug.sourceFile=generate\n" +
				"org.eclipse.jdt.core.compiler.problem.assertIdentifier=error\n" +
				"org.eclipse.jdt.core.compiler.problem.enumIdentifier=error\n" +
				"org.eclipse.jdt.core.compiler.source=1.6"
			);
	}
	
	private void createController(File srcFolder, ModelDefinition model) {
		String mType = model.getSimpleType();
		String mVar = varName(mType);

		SourceFile sf = new SourceFile();
		sf.packageName = module.packageName(module.controllers);
		sf.simpleName = mType + "Controller";

		sf.imports.add(List.class.getCanonicalName());
		sf.imports.add(SQLException.class.getCanonicalName());
		sf.imports.add(module.packageName(module.models) + "." + mType);

		sf.methods.put("0find(int id)",
				"public " + mType + " find(int id) throws SQLException {\n" +
				"\treturn " + mType + ".find(id);\n" +
				"}"
			);
		
		sf.methods.put("1find(String where)",
				"public " + mType + " find(String where) throws SQLException {\n" +
				"\treturn " + mType + ".find(where);\n" +
				"}"
			);
		
		sf.methods.put("2findAll()",
				"public List<" + mType + "> findAll() throws SQLException {\n" +
				"\treturn " + mType + ".findAll();\n" +
				"}"
			);
		
		sf.methods.put("3findAll(String where)",
				"public List<" + mType + "> findAll(String where) throws SQLException {\n" +
				"\treturn " + mType + ".findAll(where);\n" +
				"}"
			);
		
		sf.methods.put("4create",
				"public void create(" + mType + " " + mVar + ") throws SQLException {\n" +
				"\t" + mVar + ".create();\n" +
				"}"
			);

		sf.methods.put("5destroy",
				"public void destroy(" + mType + " " + mVar + ") throws SQLException {\n" +
				"\t" + mVar + ".destroy();\n" +
				"}"
			);

		sf.methods.put("6update",
				"public void update(" + mType + " " + mVar + ") throws SQLException {\n" +
				"\t" + mVar + ".update();\n" +
				"}"
			);

		writeFile(srcFolder, sf.getFilePath(), sf.toSource());
	}
	
	private void createModel(File srcFolder, ModelDefinition model) throws IOException {
		SourceFile sf = new SourceFile();
		sf.packageName = model.getPackageName();
		sf.simpleName = model.getSimpleType();
		sf.superName = sf.simpleName + "Model";

		sf.variables.put("", "public int id");
		for(PropertyDescriptor prop : model.getProperties().values()) {
			if(!prop.fullType().startsWith("java.lang")) {
				sf.imports.add(prop.fullType());
			}
			sf.variables.put(prop.variable(), "public " + prop.castType() + " " + prop.variable());
		}

		writeFile(srcFolder, sf.getFilePath(), sf.toSource());
	}

	public File getProject() {
		return project;
	}
	
	public File getFlexProject() {
		return flexProject;
	}
	
	public void setExportFlex(boolean exportFlex) {
		this.exportFlex = exportFlex;
	}

	public void setFlexProject(File project) {
		this.flexProject = project;
	}
	
	public void setForce(boolean force) {
		this.force = force;
	}

}
