package com.contaazul.sonarpullrequestintegration.mojo;

import java.io.File;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.egit.github.core.CommitFile;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;

public class ComponentConverter { 

	private final BiMap<String, String> components = HashBiMap.create();
	private final List<MavenProject> reactorProjects;
	private final String sonarBranch;

	public ComponentConverter(String sonarBranch, List<MavenProject> reactorProjects, List<CommitFile> files) {
		this.sonarBranch = sonarBranch;
		this.reactorProjects = reactorProjects;
		for (CommitFile file : files) {
			String path = file.getFilename();
			if (!path.endsWith( ".java" ))
				continue;
			String componentKey = toComponentKey( path );
			if (componentKey != null)
				components.put( componentKey, path );
		}
	}

	private String toComponentKey(String path) {
		if (path == null)
			return null;

		MavenProject project = find( path );
		if (project == null)
			return null;
		File file = getFullPath( project, path );
		if (file == null)
			return null;
		String fullPath = file.getAbsolutePath();

		String sources = new File(project.getBuild().getSourceDirectory()).getAbsolutePath();
		String classeName = fullPath.substring(
				fullPath.indexOf( sources ) + sources.length() + 1,
				fullPath.lastIndexOf( '.' ) ).replace( File.separatorChar, '.' );
		return project.getGroupId() + ":" + project.getArtifactId() + ":" + sonarBranch + ":" + classeName;
	}

	private File getFullPath(MavenProject project, String path) {
		File basedir = project.getBasedir();
		File file = new File( basedir, path );
		if (file.exists())
			return file;

		file = new File( basedir.getParentFile(), path );
		if (path.contains( basedir.getName() )
				&& file.exists())
			return file;

		return null;
	}

	private MavenProject find(String path) {
		for (MavenProject project : reactorProjects) {
			if (new File( project.getBasedir(), path ).exists()) {
				return project;
			}
			if (path.contains( project.getBasedir().getName() )
					&& new File( project.getBasedir().getParentFile(), path )
							.exists()) {
				return project;
			}
		}
		return null;
	}

	public String[] getComponents() {
		return components.keySet().toArray(
				new String[size()]
				);
	}

	public List<String> getPaths() {
		return Lists.newArrayList( components.values() );
	}

	public String pathToComponent(String path) {
		return components.inverse().get( path );
	}

	public int size() {
		return components.size();
	}

	public String componentToPath(String componentKey) {
		return components.get( componentKey );
	}

}
