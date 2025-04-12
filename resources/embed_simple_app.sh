#!/bin/bash

cd simple-app-monitoring

mvn io.github.chains-project:classport-maven-plugin:0.1.0-SNAPSHOT:embed

mvn package -Dmaven.repo.local=classport-files