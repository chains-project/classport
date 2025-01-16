cd resources/simple-app-monitoring

# Embeds the dependency information into the classfiles
mvn io.github.chains-project:classport-maven-plugin:embed

# Forces the project to use the embedded dependencies
mvn package -Dmaven.repo.local=classport-files

# Runs the application
java -jar target/test-agent-app-1.0-SNAPSHOT.jar
