package com.marvinformatics.sonar.pullrequestintegration;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.CommitFile;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.sonar.wsclient.internal.HttpRequestFactory;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueClient;
import org.sonar.wsclient.issue.IssueQuery;
import org.sonar.wsclient.issue.Issues;
import org.sonar.wsclient.issue.internal.DefaultIssueClient;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Generate a PullRequest report. WARNING, Sonar server must be started.
 * 
 * @goal publish
 * @aggregator
 */
public class SonarPullRequestMojo extends AbstractMojo {

	/**
	 * Maven project info.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * The projects in the reactor.
	 * 
	 * @parameter expression="${reactorProjects}"
	 * @readonly
	 */
	private List<MavenProject> reactorProjects;

	/**
	 * Sonar Base URL.
	 * 
	 * @parameter expression="${sonar.host.url}"
	 *            default-value="http://localhost:9000/"
	 * @optional
	 */
	private String sonarHostUrl;

	/**
	 * Branch to be used.
	 * 
	 * @parameter expression="${sonar.branch}"
	 * @optional
	 */
	private String sonarBranch;

	/**
	 * Username to access WS API.
	 * 
	 * @parameter expression="${sonar.ws.username}"
	 * @optional
	 */
	private String username;

	/**
	 * Password to access WS API.
	 * 
	 * @parameter expression="${sonar.ws.password}"
	 * @optional
	 */
	private String password;

	/**
	 * Set OAuth2 token
	 * 
	 * @parameter expression="${github.oauth2}"
	 */
	private String oauth2;

	/**
	 * Github pull request ID
	 * 
	 * @parameter expression="${github.pullRequestId}"
	 */
	private int pullRequestId;

	/**
	 * Github repository owner
	 * 
	 * @parameter expression="${github.repositoryOwner}"
	 */
	private String repositoryOwner;

	/**
	 * Github repository name
	 * 
	 * @parameter expression="${github.repositoryName}"
	 */
	private String repositoryName;

	private Repository repository;

	private PullRequestService pullRequestService;

	public void execute() throws MojoExecutionException {
		try {
			connectGithub();
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to comment to github", e );
		}

		getLog().info( "Branch " + sonarBranch + " selected" );

		ComponentConverter componentConverter;
		try {
			componentConverter = getRelatedComponents();
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to get pull request file list", e );
		}
		getLog().info( "Files affected " + componentConverter.size() );

		List<Issue> issues;
		try {
			issues = getIssues( componentConverter );
		} catch (Exception e) {
			throw new MojoExecutionException( "Unable to get sonar project", e );
		}
		getLog().info( "Issues found " + issues.size() );
		if (issues.isEmpty())
			return;

		Multimap<String, Issue> fileViolations = LinkedHashMultimap.create();
		for (Issue issue : issues) {
			fileViolations.put( componentConverter.componentToPath( issue.componentKey() ), issue );
		}

		Map<String, LinePositioner> linePositioners;
		try {
			linePositioners = createLinePositioners();
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to get commits on github", e );
		}

		Map<String, String> filesSha = getFilesSha();

		removeIssuesOutsideBounds( fileViolations, linePositioners );
		getLog().info( "Files with issues " + fileViolations.keySet().size() + ":(" + fileViolations.size() + ")" );
		removeIssuesAlreadyReported( fileViolations, linePositioners );
		getLog().info( "Files with new issues " + fileViolations.keySet().size() + ":(" + fileViolations.size() + ")" );

		try {
			recordGit( fileViolations, linePositioners, filesSha );
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to comment on github", e );
		}
	}

	private Map<String, String> getFilesSha() throws MojoExecutionException {
		List<CommitFile> files;
		try {
			files = pullRequestService.getFiles( repository, pullRequestId );
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to retrieve commits from github", e );
		}

		Map<String, String> shas = Maps.newHashMap();
		for (CommitFile commitFile : files) {
			shas.put( commitFile.getFilename(),
					commitFile.getBlobUrl().replaceAll( ".*blob/", "" ).replaceAll( "/.*", "" ) );
		}
		return shas;
	}

	private void removeIssuesOutsideBounds(Multimap<String, Issue> fileViolations,
			Map<String, LinePositioner> linePositioners) {

		List<String> paths = Lists.newArrayList( fileViolations.keySet() );
		for (String path : paths) {
			Iterator<Issue> issues = fileViolations.get( path ).iterator();
			while (issues.hasNext()) {
				Issue issue = (Issue) issues.next();
				Integer line = issue.line();
				if (line == null)
					line = 1;
				LinePositioner positioner = linePositioners.get( path );
				if (positioner == null) {
					issues.remove();
					continue;
				}

				int position = positioner.toPostion( line );
				if (position == -1)
					issues.remove();
			}
		}
	}

	private Map<String, LinePositioner> createLinePositioners() throws IOException {
		List<CommitFile> files = pullRequestService.getFiles( repository, pullRequestId );

		Map<String, LinePositioner> linePositioners = Maps.newLinkedHashMap();
		for (CommitFile commitFile : files) {
			if (commitFile.getPatch() == null)
				continue;

			LinePositioner positioner;
			if ("added".equals( commitFile.getStatus() ))
				positioner = new OneToOneLinePositioner();
			else
				positioner = new PatchLinePositioner( commitFile.getPatch() );
			linePositioners.put( commitFile.getFilename(), positioner );
		}

		return linePositioners;
	}

	private void connectGithub() throws IOException {
		GitHubClient client = new GitHubClient().setOAuth2Token( oauth2 );

		RepositoryService repositoryService = new RepositoryService( client );
		repository = repositoryService.getRepository( repositoryOwner, repositoryName );

		pullRequestService = new PullRequestService( client );
	}

	private ComponentConverter getRelatedComponents() throws IOException {
		List<CommitFile> files = pullRequestService.getFiles( repository, pullRequestId );

		return new ComponentConverter( sonarBranch, reactorProjects, files );
	}

	private void recordGit(Multimap<String, Issue> fileViolations, Map<String, LinePositioner> linePositioners,
			Map<String, String> filesSha) throws IOException {
		Iterator<RepositoryCommit> commits = pullRequestService.getCommits( repository, pullRequestId ).iterator();
		if (!commits.hasNext())
			return;

		Collection<Entry<String, Issue>> entries = fileViolations.entries();
		for (Entry<String, Issue> entry : entries) {
			String path = entry.getKey();

			CommitComment comment = new CommitComment();
			comment.setBody( entry.getValue().message() );
			comment.setCommitId( filesSha.get( path ) );
			comment.setPath( path );

			Integer line = entry.getValue().line();
			if (line == null)
				continue;

			comment.setLine( line );
			comment.setPosition( linePositioners.get( path ).toPostion( line ) );

			getLog().debug( "Path: " + path );
			getLog().debug( "Line: " + line );
			getLog().debug( "Position: " + comment.getPosition() );

			try {
				pullRequestService
						.createComment( repository, pullRequestId, comment );
			} catch (Exception e) {
				getLog().error( "Unable to comment on: " + path );
				getLog().debug( e );
			}
		}
	}

	private void removeIssuesAlreadyReported(Multimap<String, Issue> fileViolations,
			Map<String, LinePositioner> linePositioners) throws MojoExecutionException {
		List<CommitComment> currentComments;
		try {
			currentComments = pullRequestService.getComments( repository, pullRequestId );
		} catch (IOException e) {
			throw new MojoExecutionException( "Unable to retrieve comments", e );
		}
		for (CommitComment comment : currentComments) {
			Iterator<Issue> issues = fileViolations.get(
					comment.getPath() ).iterator();
			while (issues.hasNext()) {
				Issue issue = (Issue) issues.next();
				Integer line = issue.line();
				if (line == null)
					line = 1;

				int position = linePositioners.get( comment.getPath() )
						.toPostion( line );
				if (position == comment.getPosition()
						&& issue.message().equals( comment.getBody() ))
					issues.remove();
			}
		}
	}

	private List<Issue> getIssues(ComponentConverter resources) {
		if (sonarHostUrl != null) {
			if (sonarHostUrl.endsWith( "/" )) {
				sonarHostUrl = sonarHostUrl.substring( 0,
						sonarHostUrl.length() - 1 );
			}
		}

		HttpRequestFactory requestFactory = new HttpRequestFactory( sonarHostUrl )
				.setLogin( username ).setPassword( password );
		IssueClient client = new DefaultIssueClient( requestFactory );

		List<Issue> issues = Lists.newArrayList();
		getLog().debug( "sonarProjectId: " + sonarProjectId() );
		for (String component : resources.getComponents()) {
			Issues result;
			try {
				getLog().debug( "component: " + component );
				result = client.find( IssueQuery.create()
						.componentRoots( sonarProjectId() )
						.components( component )
						);
			} catch (Exception e) {
				getLog().error( "Unable to get issues for: " + component );
				getLog().debug( e.getMessage(), e );
				continue;
			}
			issues.addAll( result.list() );
		}

		return issues;
	}

	private String sonarProjectId() {
		String sonarProjectId = project.getGroupId() + ":"
				+ project.getArtifactId();
		if (sonarBranch != null) {
			sonarProjectId += ":" + sonarBranch;
		}
		return sonarProjectId;
	}

}
