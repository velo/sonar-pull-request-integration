sonar-pull-request-integration
==============================

[![Build Status](https://travis-ci.org/velo/sonar-pull-request-integration.svg?branch=master)](https://travis-ci.org/velo/sonar-pull-request-integration?branch=master) 
[![Coverage Status](https://coveralls.io/repos/github/velo/sonar-pull-request-integration/badge.svg?branch=master)](https://coveralls.io/github/velo/sonar-pull-request-integration?branch=master) 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.marvinformatics.sonar/sonar-github-pull-request-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.marvinformatics.sonar/sonar-github-pull-request-maven-plugin/) 
[![Issues](https://img.shields.io/github/issues/velo/sonar-pull-request-integration.svg)](https://github.com/velo/sonar-pull-request-integration/issues) 
[![Forks](https://img.shields.io/github/forks/velo/sonar-pull-request-integration.svg)](https://github.com/velo/sonar-pull-request-integration/network) 
[![Stars](https://img.shields.io/github/stars/velo/sonar-pull-request-integration.svg)](https://github.com/velo/sonar-pull-request-integration/stargazers)

This project seek to create a better integration between SonarQube Issues Drilldown and Github pull request system

To execute this plugin use the following command

	$ mvn com.marvinformatics.sonar:sonar-github-pull-request-maven-plugin:publish -Dsonar.branch=hazelcast -Dgithub.pullRequestId=1 -Dgithub.repositoryOwner=velo -Dgithub.repositoryName=querydsl -Dgithub.oauth2=**********

This will produce the following comments:
https://github.com/velo/querydsl/pull/1