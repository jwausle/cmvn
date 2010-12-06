package de.tototec.tools.emvn;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.Getter;
import lombok.ToString;

import org.apache.maven.pom.x400.Dependency.Exclusions;
import org.apache.maven.pom.x400.DependencyManagement;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.maven.pom.x400.Model.PluginRepositories;
import org.apache.maven.pom.x400.Model.Properties;
import org.apache.maven.pom.x400.Model.Repositories;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import de.tototec.tools.emvn.model.Dependency;
import de.tototec.tools.emvn.model.ProjectConfig;
import de.tototec.tools.emvn.model.Repository;

@ToString
public class Project {

	private final File projectFile;
	@Getter
	private ProjectConfig projectConfig;

	public Project(final File file) {
		projectFile = file.isDirectory() ? new File(file, "emvn.conf") : file;
		final ProjectReader reader = new ProjectReaderImpl();
		projectConfig = reader.readConfigFile(projectFile);
	}

	public void updateMavenProject() {
		if (needsGenerate()) {
			generateMavenProject();
		}
	}

	public boolean needsGenerate() {
		long lastModified = projectFile.lastModified();
		final File templateFile = new File(projectFile.getParent(),
				projectConfig.getPomTemplateFileName());
		if (templateFile.exists()) {
			lastModified = Math.max(lastModified, templateFile.lastModified());
		}

		final File pomFile = new File(projectFile.getParent(),
				projectConfig.getPomFileName());
		return !pomFile.exists() || lastModified > pomFile.lastModified();
	}

	public void generateMavenProject() {
		System.out.println("Generating " + projectConfig.getPomFileName()
				+ "...");

		ProjectDocument pom;
		final XmlOptions xmlOptions = createXmlOptions();
		try {
			pom = ProjectDocument.Factory.parse(
					new File(projectFile.getParent(), projectConfig
							.getPomTemplateFileName()), xmlOptions);
		} catch (final Exception e) {
			// throw new RuntimeException(e);
			// create new pom.xml
			// pom = ProjectDocument.Factory.newInstance(xmlOptions);

			final String xmlAsString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" "
					+ "xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
					+ "\t<modelVersion>4.0.0</modelVersion>\n</project>\n";

			// System.out.println("Using empty pom.xml as template:\n"
			// + xmlAsString);

			try {
				pom = ProjectDocument.Factory.parse(xmlAsString, xmlOptions);
			} catch (final XmlException e1) {
				throw new RuntimeException(e1);
			}

		}

		Model mvn = pom.getProject();
		if (mvn == null) {
			mvn = pom.addNewProject();
		}

		generateMarkerComment(mvn);
		generateProjectInfo(mvn);
		generateProperties(mvn);
		generateDependencies(mvn);
		generateRepositories(mvn);

		try {
			pom.save(
					new File(projectFile.getParent(), projectConfig
							.getPomFileName()), xmlOptions);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateMarkerComment(final Model mvn) {
		final XmlCursor cursor = mvn.newCursor();
		cursor.toFirstContentToken();
		cursor.insertChars("\n");
		cursor.insertComment(" This file was generated by emvn (sandbox version).  ");
		cursor.insertChars("\n");
		cursor.insertComment(" DO NOT EDIT THIS FILE!                              ");
		cursor.insertChars("\n");
		cursor.insertComment(" Your changes might be lost next time emvn is run.   ");
		cursor.insertChars("\n");
	}

	protected void generateRepositories(final Model mvn) {

		for (final Repository repo : projectConfig.getRepositories()) {

			final List<org.apache.maven.pom.x400.Repository> mvnRepos = new LinkedList<org.apache.maven.pom.x400.Repository>();

			if (repo.isForArtefacts()) {
				Repositories repos = mvn.getRepositories();
				if (repos == null) {
					repos = mvn.addNewRepositories();
				}
				mvnRepos.add(repos.addNewRepository());
			}
			if (repo.isForPlugins()) {
				PluginRepositories repos = mvn.getPluginRepositories();
				if (repos == null) {
					repos = mvn.addNewPluginRepositories();
				}
				mvnRepos.add(repos.addNewPluginRepository());
			}

			for (final org.apache.maven.pom.x400.Repository mvnRepo : mvnRepos) {
				mvnRepo.setId(Integer.toHexString(mvnRepo.hashCode()));
				mvnRepo.addNewReleases().setEnabled(repo.isForReleases());
				mvnRepo.addNewSnapshots().setEnabled(repo.isForSnapshots());
				mvnRepo.setUrl(repo.getUrl());
			}
		}
	}

	protected void generateProperties(final Model mvn) {
		Properties mvnProperties = mvn.getProperties();
		if (mvnProperties == null) {
			mvnProperties = mvn.addNewProperties();
		}

		final XmlCursor cursor = mvnProperties.newCursor();
		// cursor.toFirstContentToken();
		cursor.toEndToken();

		for (final Entry<String, String> entry : projectConfig.getProperties()
				.entrySet()) {
			cursor.insertChars("\n\t\t");
			cursor.beginElement(entry.getKey());
			cursor.insertChars(entry.getValue());
			cursor.toNextToken();
		}

		cursor.insertChars("\n\t");
	}

	protected void generateProjectInfo(final Model mvn) {
		final Dependency project = projectConfig.getProject();
		if (project != null) {
			mvn.setGroupId(project.getGroupId());
			mvn.setArtifactId(project.getArtifactId());
			mvn.setVersion(project.getVersion());
		}
		final String packaging = projectConfig.getPackaging();
		if (packaging != null) {
			mvn.setPackaging(packaging);
		}
	}

	protected void generateDependencies(final Model mvn) {
		for (final Dependency dep : projectConfig.getDependencies()) {
			Dependencies mvnDeps = mvn.getDependencies();
			if (mvnDeps == null) {
				mvnDeps = mvn.addNewDependencies();
			}

			org.apache.maven.pom.x400.Dependency mvnDep = null;

			for (final org.apache.maven.pom.x400.Dependency mvnDepExist : mvnDeps
					.getDependencyArray()) {
				final boolean exists = dep.getGroupId().equals(
						mvnDepExist.getGroupId())
						&& dep.getArtifactId().equals(
								mvnDepExist.getArtifactId());
				// && dep.getVersion().equals(mvnDepExist.getVersion());
				if (exists) {
					mvnDep = mvnDepExist;
					break;
				}
			}

			if (mvnDep == null) {
				mvnDep = mvnDeps.addNewDependency();
			}
			mvnDep.setGroupId(dep.getGroupId());
			mvnDep.setArtifactId(dep.getArtifactId());
			mvnDep.setVersion(dep.getVersion());
			mvnDep.setScope(dep.getScope());
			mvnDep.setOptional(dep.isOptionalAsTransitive());
			if (dep.getExcludes() != null) {
				Exclusions mvnExclusions = mvnDep.getExclusions();
				if (mvnExclusions == null) {
					mvnExclusions = mvnDep.addNewExclusions();
				}
				for (final Dependency exclude : dep.getExcludes()) {
					final Exclusion mvnExclusion = mvnExclusions
							.addNewExclusion();
					mvnExclusion.setGroupId(exclude.getGroupId());
					mvnExclusion.setArtifactId(exclude.getArtifactId());
				}
			}
			String jarPath = dep.getJarPath();
			if (jarPath != null) {
				if (!new File(jarPath).isAbsolute()) {
					jarPath = "${basedir}/" + jarPath;
				}
				mvnDep.setSystemPath(jarPath);
			}

			// dependency management
			if (dep.isForceVerison()) {

				DependencyManagement mvnMgmt = mvn.getDependencyManagement();
				if (mvnMgmt == null) {
					mvnMgmt = mvn.addNewDependencyManagement();
				}

				org.apache.maven.pom.x400.DependencyManagement.Dependencies mvnMgmtDeps = mvnMgmt
						.getDependencies();
				if (mvnMgmtDeps == null) {
					mvnMgmtDeps = mvnMgmt.addNewDependencies();
				}

				org.apache.maven.pom.x400.Dependency mvnMgmtDep = null;

				for (final org.apache.maven.pom.x400.Dependency mvnDepExist : mvnMgmtDeps
						.getDependencyArray()) {
					final boolean exists = dep.getGroupId().equals(
							mvnDepExist.getGroupId())
							&& dep.getArtifactId().equals(
									mvnDepExist.getArtifactId());
					if (exists) {
						mvnDep = mvnDepExist;
						break;
					}
				}

				if (mvnMgmtDep == null) {
					mvnMgmtDep = mvnMgmtDeps.addNewDependency();
				}

				mvnMgmtDep.setGroupId(dep.getGroupId());
				mvnMgmtDep.setArtifactId(dep.getArtifactId());
				mvnMgmtDep.setVersion(dep.getVersion());
			}
		}
	}

	public XmlOptions createXmlOptions() {
		final XmlOptions opts = new XmlOptions();
		final Map<String, String> ns = new HashMap<String, String>();
		ns.put("", "http://maven.apache.org/POM/4.0.0");
		opts.setLoadSubstituteNamespaces(ns);
		return opts;
	}

}
