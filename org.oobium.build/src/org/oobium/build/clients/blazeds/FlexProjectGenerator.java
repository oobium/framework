package org.oobium.build.clients.blazeds;

import static org.oobium.utils.FileUtils.*;

import java.io.File;

import org.oobium.build.gen.model.PropertyDescriptor;
import org.oobium.build.model.ModelDefinition;
import org.oobium.build.util.MethodCreator;
import org.oobium.build.workspace.Module;

public class FlexProjectGenerator {

	private final Module module;
	private final File project;
	
	private boolean force;
	
	public FlexProjectGenerator(Module module) {
		this(module, null);
	}
	
	public FlexProjectGenerator(Module module, File project) {
		this.module = module;
		if(project == null) {
			project = module.file.getParentFile();
		}
		if(project.isDirectory()) {
			this.project = new File(project, module.name + ".blazeds.flex");
		} else {
			this.project = project;
		}
	}
	
	public File create() {
		if(force) {
			deleteContents(project);
		}
		else if(project.exists()) {
			throw new UnsupportedOperationException(project.getName() + " already exists");
		}
		
		createProjectFile();
		
		// TODO .actionsrciptProperties
		// TODO .flexProperties
		// TODO html-template directory ?

		createFolder(project, "bin");
		createFolder(project, "libs");

		File src = createFolder(project, "src");
		for(File file : module.findModels()) {
			createModel(src, file);
		}
		createUserSession(src);
		return project;
	}
	
	private void createUserSession(File srcFolder){
		ActionScriptFile as = new ActionScriptFile();
		as.packageName = module.packageName(module.models);
		//as.classMetaTags.add("RemoteClass(alias=\"" + module.packageName(module.models) +".UserSession\")");
		as.simpleName = "UserSession";
		as.staticVariables.put("ro", "public static const ro:Object = createRemoteObject()");
		
		MethodCreator m0 = new MethodCreator("0UserSession()");
		m0.addLine("public function UserSession():void{");
			m0.addLine("ro = new RemoteObject();");
			m0.addLine("ro.destination = \"UserSessionController\";");
			m0.addLine("ro.addEventListener(\"fault\", faultHandler);");
		m0.addLine("}");
		as.addMethod(m0);
		

		MethodCreator m1 = new MethodCreator("1login");
		m1.addLine("public function login(userName:String, password:String, callBack:Function):void {");
			m1.addLine("ro.login.addEventListener(\"result\", callBack);");
			m1.addLine("ro.login(userName, password);");
		m1.addLine("}");
		as.addMethod(m1);
		
		MethodCreator m2 = new MethodCreator("2logout()");
		m2.addLine("public function logout(callBack:Function):void {");
			m2.addLine("ro.logout();");
		m2.addLine("}");
		as.addMethod(m2);
		
		MethodCreator m3 = new MethodCreator("3getUserName()");
		m3.addLine("public function getUserName(callBack:Function):void {");
			m3.addLine("ro.getUserName.addEventListener(\"result\", callBack);");
			m3.addLine("ro.getUserName();");
		m3.addLine("}");
		as.addMethod(m3);
		
		MethodCreator m4 = new MethodCreator("4getPassword()");
		m4.addLine("public function getPassword(callBack:Function):void {");
			m4.addLine("ro.getPassword.addEventListener(\"result\", callBack);");
			m4.addLine("ro.getPassword();");
		m4.addLine("}");
		as.addMethod(m4);
	
		MethodCreator m5 = new MethodCreator("5getSessionId()");
		m5.addLine("public function getSessionId(callBack:Function):void {");
			m5.addLine("ro.getSessionId.addEventListener(\"result\", callBack);");
			m5.addLine("ro.getSessionId();");
		m5.addLine("}");
		as.addMethod(m5);
		
		//PRIVATE_FUNCTIONS used to make the public functions work
	
		MethodCreator m6 = new MethodCreator("6faultHandler()");
		m6.addLine("private function faultHandler (event:FaultEvent):void {");
			m6.addLine("Alert.show(event.fault.faultString, 'Error');");
		m6.addLine("}");
		as.addMethod(m6);
		
		writeFile(srcFolder, as.getFilePath(), as.toSource());
	}

	private void createModel(File srcFolder, File modelFile) {
		ModelDefinition model = new ModelDefinition(modelFile);
		ActionScriptFile as = new ActionScriptFile();
		as.packageName = model.getPackageName();
		as.imports.add("mx.rpc.remoting.RemoteObject");
		as.imports.add("mx.rpc.events.ResultEvent");
		as.imports.add("mx.rpc.events.FaultEvent");
		as.classMetaTags.add("RemoteClass(alias=\"" + model.getCanonicalName() + "\")");
		as.simpleName = model.getSimpleName();
		
		as.staticVariables.put("ro", "public static const ro:Object = createRemoteObject()");
		
		as.staticMethods.put("createRemoteObject", 
				"private static function createRemoteObject():RemoteObject {\n" +
				"\tvar ro:RemoteObject = new RemoteObject();\n" +
				"\tro.destination = \"" + as.simpleName + "Controller\";\n" +
				"\treturn ro;\n" +
				"}"
			);

		as.variables.put("", "public var id:int");
		for(PropertyDescriptor prop : model.getProperties().values()) {
			as.variables.put(prop.variable(), "public var " + prop.variable() + ":" + prop.castType());
		}

		as.staticMethods.put("find",
				"public static function find(o:Object, callback:Function):void {\n" +
				"\t" + as.simpleName + ".ro.find.addEventListener(\"result\", callback);\n" +
				"\tif(typeof(o) == \"number\") {\n" +
				"\t\t" + as.simpleName + ".ro.find(o as int);\n" +
				"\t} else if(typeof(o) == \"string\") {\n" +
				"\t\t" + as.simpleName + ".ro.find(o as String);\n" +
				"\t} else if(type != null) {\n" +
				"\t\t" + as.simpleName + ".ro.find(o.toString());\n" +
				"\t} else {\n" +
				"\t\tthrow new Error(\"o cannot be null\");\n" +
				"\t}\n" +
				"}"
			);

		as.staticMethods.put("findAll",
				"public static function findAll(o:Object, callback:Function):void {\n" +
				"\t" + as.simpleName + ".ro.findAll.addEventListener(\"result\", callback);\n" +
				"\tif(o == \"*\") {\n" +
				"\t\t" + as.simpleName + ".ro.findAll();\n" +
				"\t} else if(typeof(o) == \"string\") {\n" +
				"\t\t" + as.simpleName + ".ro.findAll(o as String);\n" +
				"\t} else if(type != null) {\n" +
				"\t\t" + as.simpleName + ".ro.findAll(o.toString());\n" +
				"\t} else {\n" +
				"\t\tthrow new Error(\"o cannot be null\");\n" +
				"\t}\n" +
				"}"
			);
		
		as.methods.put("create",
				"public function create():void {\n" +
				"\t" + as.simpleName + ".ro.create(this);\n" +
				"}"
			);
		
		as.methods.put("update",
				"public function update():void {\n" +
				"\t" + as.simpleName + ".ro.update(this);\n" +
				"}"
			);
		
		as.methods.put("save",
				"public function save():void {\n" +
				"\tif(id < 1) {\n" +
				"\t\t" + as.simpleName + ".ro.create(this);\n" +
				"\t} else {\n" +
				"\t\t" + as.simpleName + ".ro.update(this);\n" +
				"\t}\n" +
				"}"
			);
		
		as.methods.put("destroy",
				"public function destroy():void {\n" +
				"\t" + as.simpleName + ".ro.destroy(this);\n" +
				"}"
			);

		writeFile(srcFolder, as.getFilePath(), as.toSource());
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
				"\t\t\t<name>com.adobe.flexbuilder.project.flexbuilder</name>\n" +
				"\t\t\t<arguments>\n" +
				"\t\t\t</arguments>\n" +
				"\t\t</buildCommand>\n" +
				"\t</buildSpec>\n" +
				"\t<natures>\n" +
				"\t\t<nature>com.adobe.flexbuilder.project.flexnature</nature>\n" +
				"\t\t<nature>com.adobe.flexbuilder.project.actionscriptnature</nature>\n" +
//				TODO project file: linked resource (bin-debug)
				"\t<linkedResources>\n" +
				"\t\t<link>\n" +
				"\t\t<name>bin-debug</name>\n" +
				"\t\t\t<type>2</type>\n" +
				"\t\t<location>C:/Users/jeremyd/BlazeDS/tomcat/webapps/dn2k/dn2k-flex-debug</location>\n" +
				"\t</link>\n" +
				"\t</linkedResources>\n" +
				"\t</natures>\n" +
				"</projectDescription>\n"
			);
	}
	
	public void setForce(boolean force) {
		this.force = force;
	}

}
