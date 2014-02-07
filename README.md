sonar-pull-request-integration
==============================

This project seek to create a better integration between SonarQube Issues Drilldown and Github pull request system

To execute this plugin use the following command

	$ mvn com.marvinformatics.sonar:sonar-github-pull-request-maven-plugin:publish -Dsonar.branch=hazelcast -Dgithub.pullRequestId=1 -Dgithub.repositoryOwner=velo -Dgithub.repositoryName=querydsl -Dgithub.oauth2=**********

This will produce the following comments:
https://github.com/velo/querydsl/pull/1