package com.marvinformatics.sonar.pullrequestintegration;

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

		String sources = new File( project.getBuild().getSourceDirectory() ).getAbsolutePath();
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

		File root = new File( "" ).getAbsoluteFile();
		File baseDir = project.getBasedir().getAbsoluteFile();

		int cutPoint = baseDir.getAbsolutePath().length() - root.getAbsolutePath().length();

		file = new File( project.getBasedir(), path.substring( cutPoint ) );
		if (path.contains( basedir.getName() )
				&& file.exists())
			return file;

		return null;
	}

	private MavenProject find(String path) {
		for (MavenProject project : reactorProjects)
			if (new File( project.getBasedir(), path ).exists())
				return project;

		int longest = -1;
		MavenProject bestMatch = null;
		for (MavenProject project : reactorProjects) {
			File baseDir = project.getBasedir().getAbsoluteFile();
			int longestSubstr = longestSubstr( path, baseDir.getAbsolutePath().replace( '\\', '/' ) );

			if (longestSubstr > longest) {
				bestMatch = project;
				longest = longestSubstr;
			}
		}

		if (bestMatch != null &&
				new File( bestMatch.getBasedir(), path.substring( longest ) ).exists())
			return bestMatch;

		return null;
	}

	public static int longestSubstr(String first, String second) {
		if (first == null || second == null || first.length() == 0 || second.length() == 0) {
			return 0;
		}

		int maxLen = 0;
		int fl = first.length();
		int sl = second.length();
		int[][] table = new int[fl][sl];

		for (int i = 0; i < fl; i++) {
			for (int j = 0; j < sl; j++) {
				if (first.charAt( i ) == second.charAt( j )) {
					if (i == 0 || j == 0) {
						table[i][j] = 1;
					}
					else {
						table[i][j] = table[i - 1][j - 1] + 1;
					}
					if (table[i][j] > maxLen) {
						maxLen = table[i][j];
					}
				}
			}
		}
		return maxLen;
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
