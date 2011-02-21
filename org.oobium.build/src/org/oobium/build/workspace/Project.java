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
package org.oobium.build.workspace;

import static org.oobium.utils.FileUtils.findFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.oobium.build.BuildBundle;
import org.oobium.logging.LogProvider;
import org.oobium.logging.Logger;
import org.oobium.utils.Config.Mode;
import org.oobium.utils.FileUtils;
import org.oobium.utils.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Project implements Comparable<Project> {

	public enum Type {
		Application, Module, Migrator, TestSuite, Bundle, Fragment, Project, Android
	}

	protected static Logger slogger = LogProvider.getLogger(BuildBundle.class);

	public static Project load(File file) {
		Manifest manifest = manifest(file);
		if(manifest == null) {
			File androidManifest = new File(file, "AndroidManifest.xml");
			if(androidManifest.exists()) {
				return new AndroidApp(file);
			} else {
				return new Project(Type.Project, file, null);
			}
		} else {
			Type type = parseType(manifest);
			switch(type) {
			case Application:
				return new Application(type, file, manifest);
			case Module:
				return new Module(type, file, manifest);
			case Migrator:
				return new Migrator(type, file, manifest);
			case TestSuite:
				return new TestSuite(type, file, manifest);
			case Bundle:
				return new Bundle(type, file, manifest);
			case Fragment:
				return new Fragment(type, file, manifest);
			default:
				return new Project(Type.Project, file, manifest);
			}
		}
	}

	public static Manifest manifest(File bundle) {
		if(bundle.isDirectory()) {
			File file = new File(bundle, "META-INF" + File.separator + "MANIFEST.MF");
			if(file.isFile()) {
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(file);
					return new Manifest(fis);
				} catch(Exception e) {
					// throw away
				} finally {
					if(fis != null) {
						try {
							fis.close();
						} catch(IOException e) {
							// throw away
						}
					}
				}
			}
		}
		if(bundle.isFile()) {
			JarFile jar = null;
			try {
				jar = new JarFile(bundle);
				return jar.getManifest();
			} catch(Exception e) {
				// throw away
			} finally {
				if(jar != null) {
					try {
						jar.close();
					} catch(IOException e) {
						// throw away
					}
				}
			}
		}
		return null;
	}

	private static Type parseType(Manifest manifest) {
		Attributes attrs = manifest.getMainAttributes();
		
		String type = (String) attrs.getValue("Oobium-Type");
		if(type != null) {
			type = StringUtils.camelCase(type);
			try {
				return Type.valueOf(type);
			} catch(Exception e) {
				// discard and fall through
			}
		}
		
		if(attrs.getValue("Bundle-Version") != null) {
			if(attrs.getValue("Fragment-Host") != null) {
				return Type.Fragment;
			}
			return Type.Bundle;
		}
		return Type.Project;
	}

	protected final Logger logger;

	/**
	 * This bundle's type, as specified in the custom manifest header, <code>Oobium-Type</code>.
	 * @see Type
	 */
	public final Type type;

	/**
	 * This bundle's root directory, or jar file, on the file system.
	 */
	public final File file;

	/**
	 * This bundle's symbolic name, as specified by the manifest header
	 * <code>Bundle-SymbolicName</code>
	 */
	public final String name;
	
	/**
	 * This bundle's manifest file, or null if this is a jarred bundle.
	 */
	public final File manifest;

	/**
	 * this project's "bin" directory<br>
	 * The value is obtained from the project's "build.properties" file, if it
	 * exists; otherwise it defaults to simply "bin";
	 */
	public final File bin;

	/**
	 * this project's "src" directory
	 */
	public final File src;

	/**
	 * this project's main source directory
	 */
	public final File main;

	/**
	 * this project's .classpath file (created by Eclipse if this project was
	 * created by Eclipse)
	 */
	public final File classpath;

	/**
	 * this project's .project file (created by Eclipse if this project was
	 * created by Eclipse)
	 */
	public final File project;

	/**
	 * true if this bundle is a jar file, false otherwise.
	 */
	public final boolean isJar;

	Project(Type type, File file, Manifest manifest) {
		this.logger = LogProvider.getLogger(BuildBundle.class);

		this.file = file;
		this.isJar = file.isFile() && file.getName().endsWith(".jar");

		this.type = type;
		this.manifest = setManifest();

		if(isJar) {
			this.name = parseName(manifest);
			this.bin = null;
			this.src = null;
			this.main = null;
			this.classpath = null;
			this.project = null;
		} else {
			File buildFile = new File(file, "build.properties");
			String binPath = null;
			if(buildFile.isFile()) {
				Properties props = new Properties();
				try {
					props.load(new FileReader(buildFile));
					binPath = props.getProperty("output..", "bin");
				} catch(Exception e) {
					binPath = "bin";
				}
			}
			this.project = new File(file, ".project");
			this.classpath = new File(file, ".classpath");
			this.name = parseName(manifest);
			this.bin = (binPath != null && !".".equals(binPath)) ? new File(file, binPath) : new File(file, "bin");
			this.src = new File(file, "src");
			this.main = setMain();
		}
	}

	protected File setManifest() {
		return new File(file, "META-INF" + File.separator + "MANIFEST.MF");
	}
	
	protected File setMain() {
		return new File(src, name.replace('.', File.separatorChar));
	}

	public boolean addBuildPath(String path, String kind) {
		if(classpath.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(classpath);
				NodeList lists = doc.getElementsByTagName("classpath");
				if(lists.getLength() > 0) {
					for(int i = 0; i < lists.getLength(); i++) {
						Node node = lists.item(i);
						if(node.getNodeType() == Node.ELEMENT_NODE) {
							NodeList list = node.getChildNodes();
							for(int j = 0; j < list.getLength(); j++) {
								Node child = list.item(j);
								if(child.getNodeType() == Node.ELEMENT_NODE && "classpathentry".equals(child.getNodeName())) {
									if(path.equals(((Element) child).getAttribute("path"))) {
										return true; // nature already present
									}
								}
							}
						}
					}
					lists.item(0).appendChild(doc.createTextNode("\t"));
					Element element = doc.createElement("classpathentry");
					element.setAttribute("path", path);
					element.setAttribute("kind", kind);
					lists.item(0).appendChild(element);
					write(classpath, doc);
					return true;
				}
			} catch(Exception e) {
				logger.warn(e);
			}
		}
		return false;
	}
	
	public boolean addNature(String nature) {
		if(project.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(project);
				NodeList lists = doc.getElementsByTagName("natures");
				if(lists.getLength() > 0) {
					for(int i = 0; i < lists.getLength(); i++) {
						Node node = lists.item(i);
						if(node.getNodeType() == Node.ELEMENT_NODE) {
							NodeList list = node.getChildNodes();
							for(int j = 0; j < list.getLength(); j++) {
								Node child = list.item(j);
								if(child.getNodeType() == Node.ELEMENT_NODE && "nature".equals(child.getNodeName())) {
									if(nature.equals(child.getFirstChild().getNodeValue())) {
										return true; // nature already present
									}
								}
							}
						}
					}
					lists.item(0).appendChild(doc.createTextNode("\t"));
					Element element = doc.createElement("nature");
					element.setTextContent(nature);
					lists.item(0).appendChild(element);
					write(project, doc);
					return true;
				}
			} catch(Exception e) {
				logger.warn(e);
			}
		}
		return false;
	}

	public void clean() {
		FileUtils.deleteContents(bin);
	}

	@Override
	public int compareTo(Project o) {
		return name.compareTo(o.name);
	}

	public void delete() {
		FileUtils.delete(file);
	}

	public File getBinFile(File srcFile) {
		int len = src.getAbsolutePath().length();
		String path = srcFile.getAbsolutePath();
		if(path.endsWith(".java")) {
			path = path.substring(len, path.length() - 4) + "class";
		} else {
			path = path.substring(len);
		}
		return new File(bin, path);
	}

	public Set<File> getBinFiles(File... srcFiles) {
		return getBinFiles(Arrays.asList(srcFiles));
	}

	public Set<File> getBinFiles(List<File> srcFiles) {
		Set<File> binFiles = new HashSet<File>();

		int len = src.getAbsolutePath().length();
		for(File file : srcFiles) {
			binFiles.add(new File(bin, file.getAbsolutePath().substring(len)));
		}

		return binFiles;
	}

	/**
	 * Returns a map of all files necessary to build the jar for the given
	 * bundle. The map key is the relative path to be used in the JarEntry
	 * object (Windows style path separators are corrected). The map value is
	 * the absolute File object pointing to the class or resource file to be
	 * added to the jar.
	 * 
	 * @param bundle the bundle for which to build the jar
	 * @return Map<String, File> all files necessary to build the jar for the given bundle
	 */
	public Map<String, File> getBuildFiles() throws IOException {
		return getBuildFiles(false);
	}
	
	public Map<String, File> getBuildFiles(boolean includeSource) throws IOException {
		Map<String, File> buildFiles = new HashMap<String, File>();
		File[] files = findFiles(bin);
		int len = bin.getAbsolutePath().length() + 1;
		for(File file : files) {
			String relativePath = file.getAbsolutePath().substring(len);
			if('\\' == File.separatorChar) {
				relativePath = relativePath.replace('\\', '/');
			}
			buildFiles.put(relativePath, file);
		}

		File buildFile = new File(file, "build.properties");
		if(buildFile.isFile()) {
			Properties props = new Properties();
			props.load(new FileReader(buildFile));
			String prop = props.getProperty("bin.includes");
			if(prop != null && prop.length() > 0) {
				String[] includes = prop.split("\\s*,\\s*");
				len = file.getAbsolutePath().length() + 1;
				for(String include : includes) {
					if(!".".equals(include)) {
						File folder = new File(file, include);
						files = findFiles(folder);
						for(File file : files) {
							String relativePath = file.getAbsolutePath().substring(len);
							if('\\' == File.separatorChar) {
								relativePath = relativePath.replace('\\', '/');
							}
							buildFiles.put(relativePath, file);
						}
					}
				}
			}
		}
		if(includeSource) {
			buildFiles.putAll(getSourceBuildFiles());
		}
		return buildFiles;
	}

	public Map<String, File> getSourceBuildFiles() {
		Map<String, File> buildFiles = new HashMap<String, File>();
		if(classpath != null && classpath.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(classpath);
				NodeList list = doc.getElementsByTagName("classpathentry");
				for(int i = 0; i < list.getLength(); i++) {
					Node node = list.item(i);
					if(node.getNodeType() == Node.ELEMENT_NODE) {
						Element cpe = (Element) node;
						String kind = cpe.getAttribute("kind");
						if("src".equals(kind)) {
							File folder = new File(file, cpe.getAttribute("path"));
							File[] files = findFiles(folder);
							int len = folder.getAbsolutePath().length() + 1;
							for(File file : files) {
								String relativePath = file.getAbsolutePath().substring(len);
								if('\\' == File.separatorChar) {
									relativePath = relativePath.replace('\\', '/');
								}
								buildFiles.put(relativePath, file);
							}
						}
					}
				}

				File buildFile = new File(file, "build.properties");
				if(buildFile.isFile()) {
					Properties props = new Properties();
					props.load(new FileReader(buildFile));
					String prop = props.getProperty("src.includes");
					if(prop != null && prop.length() > 0) {
						String[] includes = prop.split("\\s*,\\s*");
						int len = file.getAbsolutePath().length() + 1;
						for(String include : includes) {
							if(!".".equals(include)) {
								File folder = new File(file, include);
								File[] files = findFiles(folder);
								for(File file : files) {
									String relativePath = file.getAbsolutePath().substring(len);
									if('\\' == File.separatorChar) {
										relativePath = relativePath.replace('\\', '/');
									}
									buildFiles.put(relativePath, file);
								}
							}
						}
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return buildFiles;
	}

	public String getClasspath() {
		return StringUtils.join(getClasspathEntries(), File.pathSeparatorChar);
	}

	public String getClasspath(Workspace workspace) {
		return StringUtils.join(getClasspathEntries(workspace), File.pathSeparatorChar);
	}

	public String getClasspath(Workspace workspace, Mode mode) {
		return StringUtils.join(getClasspathEntries(workspace, mode), File.pathSeparatorChar);
	}

	private void addClasspathEntries(Set<String> cpes) {
		if(classpath != null && classpath.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(classpath);
				NodeList list = doc.getElementsByTagName("classpathentry");
				for(int i = 0; i < list.getLength(); i++) {
					Node node = list.item(i);
					if(node.getNodeType() == Node.ELEMENT_NODE) {
						Element cpe = (Element) node;
						String kind = cpe.getAttribute("kind");
						if("src".equals(kind)) {
							String path = file.getAbsolutePath() + File.separator + cpe.getAttribute("path");
							cpes.add(path);
						}
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		if(file.isDirectory()) {
			cpes.add(file.getAbsolutePath() + File.separator + "bin");
		} else {
			cpes.add(file.getAbsolutePath());
		}
	}

	public Set<String> getClasspathEntries() {
		Set<String> cpes = new LinkedHashSet<String>();
		addClasspathEntries(cpes);
		return cpes;
	}

	public Set<String> getClasspathEntries(Workspace workspace) {
		Set<String> cpes = new LinkedHashSet<String>();
		addClasspathEntries(cpes);
		return cpes;
	}

	public Set<String> getClasspathEntries(Workspace workspace, Mode mode) {
		Set<String> cpes = new LinkedHashSet<String>();
		addClasspathEntries(cpes);
		return cpes;
	}

	/**
	 * Get the full name of this bundle without the qualifier: "com.test.blog_1.0.0"
	 * @return the full name of this bundle
	 */
	public String getName() {
		return name;
	}

	public Set<String> getNatures() {
		Set<String> natures = new LinkedHashSet<String>();
		if(project.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(project);
				NodeList list = doc.getElementsByTagName("nature");
				for(int i = 0; i < list.getLength(); i++) {
					Node node = list.item(i);
					if(node.getNodeType() == Node.ELEMENT_NODE) {
						String nature = node.getFirstChild().getNodeValue();
						natures.add(nature);
					}
				}
			} catch(Exception e) {
				logger.warn(e);
			}
		}
		return natures;
	}

	public boolean hasNature(String nature) {
		return getNatures().contains(nature);
	}

	/**
	 * @return true if this is an Android project (contains an AndroidManifest.xml)
	 */
	public boolean isAndroid() {
		return type == Type.Android;
	}
	
	/**
	 * @return true if this is a Oobium Application.
	 */
	public boolean isApplication() {
		return type == Type.Application;
	}

	/**
	 * Checks whether or not this bundle is an OSGi framework bundle, meaning
	 * that it exports the org.osgi.framework package.
	 * 
	 * @return true if this bundle is an OSGi framework bundle; false otherwise.
	 */
	public boolean isFramework() {
		return false;
	}

	/**
	 * @return true if this is a Oobium Migration.
	 */
	public boolean isMigration() {
		return type == Type.Migrator;
	}

	/**
	 * @return true if this is a Oobium Module or Application (Application
	 *         extends Module).
	 */
	public boolean isModule() {
		return type == Type.Module || type == Type.Application;
	}

	public boolean isService() {
		return false;
	}

	/**
	 * @return true if this is a Oobium TestSuite.
	 */
	public boolean isTestSuite() {
		return type == Type.TestSuite;
	}

	/**
	 * Get the java package name for the given file.<br>
	 * The given file may be an actual file, or a directory.
	 */
	public String packageName(File file) {
		return packageName(src, file);
	}

	/**
	 * Get the java package name for the given file.<br>
	 * The given file may be an actual file, or a directory.
	 */
	public String packageName(File srcFolder, File file) {
		if(file.isFile()) {
			file = file.getParentFile();
		}
		int ix = srcFolder.getAbsolutePath().length();
		String name = file.getAbsolutePath().substring(ix + 1).replace(File.separatorChar, '.');
		return name;
	}

	private String parseName(Manifest manifest) {
		if(manifest == null) {
			if(isJar) {
				// TODO
			} else {
				// yup, parsing xml with a regex. read it and weep!
				if(project.exists()) {
					String src = FileUtils.readFile(project).toString();
					Pattern p = Pattern.compile("<name>([^<]*)</name>");
					Matcher m = p.matcher(src);
					if(m.find()) {
						return m.group(1);
					}
				} else {
					return file.getName();
				}
			}
		} else {
			String name = (String) manifest.getMainAttributes().getValue("Bundle-SymbolicName");
			if(name != null) {
				int ix = name.indexOf(';');
				if(ix == -1) {
					return name.trim();
				} else {
					return name.substring(0, ix).trim();
				}
			}
		}
		return "!UNKNOWN!";
	}

	public boolean removeNature(String nature) {
		if(project.isFile()) {
			try {
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
				Document doc = docBuilder.parse(project);
				NodeList lists = doc.getElementsByTagName("natures");
				if(lists.getLength() > 0) {
					for(int i = 0; i < lists.getLength(); i++) {
						Node node = lists.item(i);
						if(node.getNodeType() == Node.ELEMENT_NODE) {
							NodeList list = node.getChildNodes();
							for(int j = 0; j < list.getLength(); j++) {
								Node child = list.item(j);
								if(child.getNodeType() == Node.ELEMENT_NODE && "nature".equals(child.getNodeName())) {
									if(nature.equals(child.getFirstChild().getNodeValue())) {
										node.removeChild(child);
										if(j > 0 && list.item(j - 1).getNodeType() == Node.TEXT_NODE) {
											node.removeChild(list.item(j - 1));
										}
										write(project, doc);
										return true;
									}
								}
							}
						}
					}
				}
				return true; // nature not present
			} catch(Exception e) {
				logger.warn(e);
			}
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(type).append(':').append(' ').append(name);
		if(isJar) {
			sb.append(" (jarred)");
		}
		return sb.toString();
	}

	protected void write(File file, Document doc) throws Exception {
		TransformerFactory transfac = TransformerFactory.newInstance();
		Transformer trans = transfac.newTransformer();
		trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		trans.setOutputProperty(OutputKeys.INDENT, "yes");

		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		DOMSource source = new DOMSource(doc);
		trans.transform(source, result);
		String xmlString = sw.toString();

		FileUtils.writeFile(file, xmlString);
	}

}