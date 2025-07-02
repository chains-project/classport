#!/bin/bash

# clean classport-files
./clean.sh --classport-files

# clean multi mvn project
cd multi
mvn clean

# run embedding plugin to embed annotations into multi project
mvn io.github.project:classport-maven-plugin:0.1.0-SNAPSHOT:embed

# merge the classport-files
cd ..
./post_process_local_repo.sh multi

# move to submodule and first package them with the new annotated maven repository
cd multi/submulti
mvn package -Dmaven.repo.local=../all-classport-files

# move to the root and package everything
cd ..
mvn package -Dmaven.repo.local=all-classport-files

# run the retriever
cd ..
./run_retriever_multi.sh
