package com.marvinformatics.sonar.pullrequestintegration;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import org.sonar.wsclient.services.Violation;

import com.google.common.collect.LinkedHashMultimap;
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

	public void execute() throws MojoExecutionException {
		List<Issue> issues;
		try {
			issues = getIssues();
		} catch (Exception e) {
			throw new MojoExecutionException("Unable to get sonar project", e);
		}

		Multimap<String, Issue> fileViolations = LinkedHashMultimap.create();
		for (Issue issue : issues) {
			String fileName = issue.componentKey();
			fileName = fileName.substring(fileName.lastIndexOf(':') + 1);
			fileName = fileName.replace('.', '/');
			fileName = fileName + ".java";
			fileViolations.put(fileName, issue);
		}

		try {
			recordGit(fileViolations);
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to comment on github", e);
		}

	}

	private void recordGit(Multimap<String, Issue> fileViolations)
			throws IOException {
		GitHubClient client = new GitHubClient().setOAuth2Token(oauth2);

		RepositoryService rs = new RepositoryService(client);
		Repository repository = rs.getRepository(repositoryOwner,
				repositoryName);

		PullRequestService pullRequestService = new PullRequestService(client);

		Iterator<RepositoryCommit> commits = pullRequestService.getCommits(
				repository, pullRequestId).iterator();
		if (!commits.hasNext())
			return;

		RepositoryCommit lastCommit = commits.next();

		List<CommitFile> files = pullRequestService.getFiles(repository,
				pullRequestId);

		Multimap<String, Issue> relatedFileViolations = LinkedHashMultimap
				.create();

		Map<String, LinePositioner> linePositioners = Maps.newLinkedHashMap();
		for (CommitFile commitFile : files) {
			Set<String> keys = fileViolations.keySet();
			for (String key : keys) {
				if (commitFile.getFilename().contains(key)) {
					relatedFileViolations.putAll(commitFile.getFilename(),
							fileViolations.get(key));
					linePositioners.put(commitFile.getFilename(),
							new LinePositioner(commitFile.getPatch()));
				}
			}
		}

		List<CommitComment> currentComments = pullRequestService.getComments(
				repository, pullRequestId);
		for (CommitComment comment : currentComments) {
			Iterator<Issue> issues = relatedFileViolations.get(
					comment.getPath()).iterator();
			while (issues.hasNext()) {
				Issue issue = (Issue) issues.next();
				int position = linePositioners.get(comment.getPath())
						.toPostion(issue.line());
				if (position == comment.getPosition()
						&& issue.message().equals(comment.getBody()))
					issues.remove();
			}
		}

		Collection<Entry<String, Issue>> entries = relatedFileViolations
				.entries();
		for (Entry<String, Issue> entry : entries) {
			CommitComment comment = new CommitComment();
			comment.setBody(entry.getValue().message());
			comment.setCommitId(lastCommit.getSha());
			comment.setPath(entry.getKey());

			int line = entry.getValue().line();
			comment.setLine(line);
			comment.setPosition(linePositioners.get(entry.getKey()).toPostion(
					line));

			pullRequestService
					.createComment(repository, pullRequestId, comment);
		}
	}

	private List<Issue> getIssues() throws IOException {
		if (sonarHostUrl != null) {
			if (sonarHostUrl.endsWith("/")) {
				sonarHostUrl = sonarHostUrl.substring(0,
						sonarHostUrl.length() - 1);
			}
		}

		String sonarProjectId = project.getGroupId() + ":"
				+ project.getArtifactId();
		if (sonarBranch != null) {
			sonarProjectId += ":" + sonarBranch;
			getLog().info("Branch " + sonarBranch + " selected");
		}

		HttpRequestFactory requestFactory = new HttpRequestFactory(sonarHostUrl)
				.setLogin(username).setPassword(password);
		IssueClient client = new DefaultIssueClient(requestFactory);
		Issues result = client.find(IssueQuery.create().componentRoots(
				sonarProjectId));
		List<Issue> issues = result.list();

		return issues;
	}

}
