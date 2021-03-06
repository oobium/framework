package org.oobium.build.clients;

import static org.oobium.utils.FileUtils.copyJarEntry;
import static org.oobium.utils.FileUtils.createJar;
import static org.oobium.utils.FileUtils.extract;
import static org.oobium.utils.FileUtils.writeFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.oobium.build.workspace.AndroidApp;
import org.oobium.build.workspace.Bundle;
import org.oobium.build.workspace.Module;
import org.oobium.build.workspace.Project;
import org.oobium.build.workspace.Workspace;
import org.oobium.utils.Config.Mode;
import org.oobium.utils.FileUtils;

public class JavaClientExporter {

//	private final Logger logger;
	private final Workspace workspace;
	private final Module module;
	private final File exportDir;
	private final File tmpDir;

	private Project target;
	private Mode mode;
	private boolean full;
	private boolean clean;
	private boolean includeSource;
	
	public JavaClientExporter(Workspace workspace, Module module) {
//		this.logger = LogProvider.getLogger(BuildBundle.class);
		this.workspace = workspace;
		this.module = module;
		this.exportDir = workspace.getExportDir();
		this.tmpDir = new File(exportDir, "tmp");
		
		this.mode = Mode.PROD;
	}

	public void setFull(boolean fullExport) {
		this.full = fullExport;
	}
	
	public void setTarget(Project project) {
		this.target = project;
	}
	
	private String relativePath(File binFile, int binPathLength) {
		String relativePath = binFile.getAbsolutePath().substring(binPathLength);
		if('\\' == File.separatorChar) {
			relativePath = relativePath.replace('\\', '/');
		}
		return relativePath;
	}

	private String relativeSrcPath(File srcFile, File binFile, int binPathLength) {
		String relativePath = binFile.getParent().substring(binPathLength) + File.separator + srcFile.getName();
		if('\\' == File.separatorChar) {
			relativePath = relativePath.replace('\\', '/');
		}
		return relativePath;
	}
	
	public void includeSource(boolean include) {
		this.includeSource = include;
	}
	
	/**
	 * Export the application, configured for the given mode.
	 * @return a File object for the exported jar.
	 * @throws IOException
	 */
	public File[] export() throws IOException {
		if(exportDir.exists()) {
			if(clean) {
				FileUtils.deleteContents(exportDir);
			}
		} else {
			exportDir.mkdirs();
		}
		
		if(tmpDir.exists()) {
			FileUtils.deleteContents(tmpDir);
		} else {
			tmpDir.mkdirs();
		}

		try {
			File targetDir;
			if(target == null) {
				targetDir = exportDir;
			} else {
				targetDir = new File(target.file, "oobium");
			}

			if(target instanceof AndroidApp) {
				return exportAndroid(targetDir);
			} else {
				return exportJava(targetDir);
			}
		} finally {
			FileUtils.delete(tmpDir);
		}
	}

	private File[] exportAndroid(File targetDir) throws IOException {
		// export netty jar (should just have a jar built already...)
		Map<String, File> nettyFiles = new HashMap<String, File>();
		addFiles("org.jboss.netty", nettyFiles);
		
		File nettyJar = createJar(targetDir, "org.jboss.netty.jar", nettyFiles);

		
		// export core oobium jar
		Map<String, File> oobiumFiles = new HashMap<String, File>();
		addFiles("org.oobium.client", oobiumFiles);
		addFiles("org.oobium.app.common", "org.oobium.app.http", oobiumFiles);
		addFile("org.oobium.logging", "org.oobium.logging.Logger", oobiumFiles);
		addFile("org.oobium.logging", "org.oobium.logging.LogProvider", oobiumFiles);
		addAndroidLogger(oobiumFiles);
		addFiles("org.oobium.persist", oobiumFiles);
		addFiles("org.oobium.persist.http", oobiumFiles);
		addFiles("org.oobium.utils", oobiumFiles);

		File oobiumJar = createJar(targetDir, "org.oobium.android.jar", oobiumFiles);
		

		// export application jar
		File applicationJar = createJar(targetDir, module.name + (full ? ".android.jar" : ".models.android.jar"), getApplicationFiles());

		
		if(target != null) {
			target.addBuildPath("oobium/" + nettyJar.getName(), "lib");
			target.addBuildPath("oobium/" + applicationJar.getName(), "lib");
			target.addBuildPath("oobium/" + oobiumJar.getName(), "lib");
		}
		
		return new File[] { nettyJar, oobiumJar, applicationJar };
	}
	
	private File[] exportJava(File targetDir) throws IOException {
		// export netty jar (should just have a jar built already...)
		Map<String, File> nettyFiles = new HashMap<String, File>();
		addFiles("org.jboss.netty", nettyFiles);
		
		File nettyJar = createJar(targetDir, "org.jboss.netty.jar", nettyFiles);

		
		// export core oobium jar
		Map<String, File> oobiumFiles = new HashMap<String, File>();
		addFiles("org.oobium.client", oobiumFiles);
		addFiles("org.oobium.app.common", "org.oobium.app.http", oobiumFiles);
		addFiles("org.oobium.logging", oobiumFiles);
		addFiles("org.oobium.persist", oobiumFiles);
		addFiles("org.oobium.persist.http", oobiumFiles);
		addFiles("org.oobium.utils", oobiumFiles);

		File oobiumJar = createJar(targetDir, "org.oobium.jar", oobiumFiles);
		

		// export application jar
		File applicationJar = createJar(targetDir, module.name + (full ? ".jar" : ".models.jar"), getApplicationFiles());

		
		if(target != null) {
			target.addBuildPath("oobium/" + nettyJar.getName(), "lib");
			target.addBuildPath("oobium/" + applicationJar.getName(), "lib");
			target.addBuildPath("oobium/" + oobiumJar.getName(), "lib");
		}
		
		return new File[] { nettyJar, oobiumJar, applicationJar };
	}
	
	private Map<String, File> getApplicationFiles() throws IOException {
		Set<Bundle> bundles = new TreeSet<Bundle>();
		Map<Bundle, List<Bundle>> deps = module.getDependencies(workspace, mode);
		bundles.addAll(deps.keySet());
		bundles.add(module);
		
		Map<String, File> applicationFiles = new HashMap<String, File>();
		int len = module.bin.getAbsolutePath().length() + 1;
		if(full) {
			addFiles(module.name, applicationFiles);
		} else {
			for(File model : module.findModels()) {
				File genModel = module.getGenModel(model);
				File[] genModelClasses = module.getBinFiles(genModel);
				for(File genModelClass : genModelClasses) {
					applicationFiles.put(relativePath(genModelClass, len), genModelClass);
				}
				if(includeSource) {
					applicationFiles.put(relativeSrcPath(genModel, genModelClasses[0], len), genModel);
				}
				if(target != null) {
					createClientModel(model);
				}
			}
		}

		return applicationFiles;
	}
	
	private void createClientModel(File model) {
		if(target != null) {
			String path = model.getAbsolutePath().substring(module.src.getAbsolutePath().length());
			File clientModel = new File(target.src, path);
			
			if(!clientModel.exists()) {
				String pkg = module.packageName(model);
				String name = module.getModelName(model);
				String sname = name + "Model";
	
				String src = 
					"package " + pkg + ";\n" +
					"\n" +
					"public class " + name + " extends " + sname + " {\n" +
					"\n" +
					"}";
	
				writeFile(clientModel, src);
			}
		}
	}
	
	private void addAndroidLogger(Map<String, File> files) {
		File file = writeFile(tmpDir, "LoggerImpl.class", getClass().getResourceAsStream("android/logger/LoggerImpl.class.android"));
		files.put("org/oobium/logging/LoggerImpl.class", file);
		if(includeSource) {
			file = writeFile(tmpDir, "LoggerImpl.java", getClass().getResourceAsStream("android/logger/LoggerImpl.java.android"));
			files.put("org/oobium/logging/LoggerImpl.java", file);
		}
	}
	
	private void addFiles(String bundleName, Map<String, File> files) throws IOException {
		Bundle bundle = workspace.getBundle(bundleName);
		if(bundle == null) {
			throw new NullPointerException(bundleName + " not in workspace");
		}
		if(bundle.isJar) {
			files.putAll(extract(bundle.file, new File(tmpDir, bundle.name)));
		} else {
			files.putAll(bundle.getBuildFiles(includeSource));
		}
	}

	private void addFile(String bundleName, String className, Map<String, File> files) throws IOException {
		Bundle bundle = workspace.getBundle(bundleName);
		if(bundle == null) {
			throw new NullPointerException(bundleName + " not in workspace");
		}
		if(bundle.isJar) {
			String entryName = className.replace('.', '/') + ".class";
			String dstName = bundle.file.getName();
			dstName = dstName.substring(0, dstName.lastIndexOf('.'));
			File dir = new File(tmpDir, dstName);
			if(!dir.isDirectory()) {
				dir.mkdirs();
			}
			File dst = copyJarEntry(bundle.file, entryName, dir);
			files.put(entryName, dst);
		} else {
			int len = bundle.bin.getAbsolutePath().length() + 1;
			File srcFile = new File(bundle.src, className.replace('.', File.separatorChar) + ".java");
			File[] binFiles = bundle.getBinFiles(srcFile);
			for(File binFile : binFiles) {
				files.put(relativePath(binFile, len), binFile);
			}
			if(includeSource) {
				files.put(relativeSrcPath(srcFile, binFiles[0], len), srcFile);
			}
		}
	}
	
	private void addFiles(String bundleName, String packageName, Map<String, File> files) throws IOException {
		Bundle bundle = workspace.getBundle(bundleName);
		if(bundle == null) {
			throw new NullPointerException(bundleName + " not in workspace");
		}
		if(bundle.isJar) {
			String regex = packageName.replace('.', '/') + "/.+";
			files.putAll(extract(bundle.file, new File(tmpDir, bundle.name), regex));
		} else {
			File pkg = new File(bundle.src, packageName.replace('.', File.separatorChar));
			if(pkg.isDirectory()) {
				int len = bundle.bin.getAbsolutePath().length() + 1;
				for(File srcFile : pkg.listFiles()) {
					File[] binFiles = bundle.getBinFiles(srcFile);
					for(File binFile : binFiles) {
						files.put(relativePath(binFile, len), binFile);
					}
					if(includeSource) {
						files.put(relativeSrcPath(srcFile, binFiles[0], len), srcFile);
					}
				}
			} else {
				pkg = new File(bundle.file, packageName.replace('.', File.separatorChar));
				if(pkg.isDirectory()) {
					int len = bundle.file.getAbsolutePath().length() + 1;
					for(File binFile : pkg.listFiles()) {
						if(binFile.isFile()) {
							files.put(relativePath(binFile, len), binFile);
						}
					}
				}
			}
		}
	}
	
}
