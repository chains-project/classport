# Classport

## About

Classport is a way of embedding supply chain-related metadata in class files
using an annotation, contained in the `classport.commons` package. This
repository contains an example implementation in the form of a Maven plugin that
modifies the JAR files during the build process. These can then be packaged
together with the application, or just added to the class path in place of the
"regular" versions of the class files.

## Usage

### General

The `classport` goal will retrieve a list of project dependencies and their
corresponding JAR files, embed the annotation into all class files within each
JAR, and recreate what can be seen as a dependency-only Maven local repository
in the `classport-files` directory. The JAR files from within there can then be
included in the class path with the `-cp` flag as per usual.

### With an Uber-JAR

With plugins such as `maven-shade`, a project can be packaged into a jar
together with its dependencies. This facilitates distribution as the resulting
JAR is self-contained. To use `classport` in this case, we want to ensure that
the class files included in the Uber-JAR are the ones in `classport-files`. The
easiest way to do this (but perhaps not the only way?) is to just set the Maven
local repository to the `classport-files` directory manually using the
`-Dmaven.repo.local` flag and have Maven download the "regular" version of the
dependencies to there. Since Maven will not overwrite existing dependency JARs
and `classport` will, it doesn't matter if the `classport` goal is executed
before or after the rest of the dependencies are downloaded - the modified
classes will be the ones used regardless. However, executing the `classport`
goal first is usually easier as the process becomes `classport -> package`
instead of `<download-deps> -> classport -> package`.

## TODOs

In priority order, from highest to lowest.

- [x] Replicate the maven directory structure so that we can point the build
      process to this directory instead of the default `~/.m2` one by using the
      `-Dmaven.repo.local` command-line argument.
- [ ] Figure out how to get the following metadata:
  - [ ] Checksums
  - [x] Parent info
  - [ ] URLs
- [x] Create an actual interface for the annotation.
  - Currently, we are able to add annotations but since they don't actually
    exist and can't be loaded by the JVM, they won't be available using runtime
    reflection.
  - Since the program will always be accompanied by an agent, having the
    annotation interface in the agent's classpath should be enough, as long as
    we use its fully qualified name when embedding it.
  - If this turns out not to be enough, the agent can be annotated as well,
    forcing the class to be loaded into the JVM before the rest of the program
    executes.
  - As for naming, see the
    [JLS](https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.7):
    "The fully qualified name of a top level class or top level interface that
    is declared in an unnamed package is the simple name of the class or
    interface"). At this point, the metadata _should_ be available using
    reflection.
- [x] Create a Java agent to run it, as it sees all classes being loaded and can
      thus build the SBOM?
  - [ ] Create an object instance of the `ClassportInfo` class to have "safe"
        metadata access methods?
- [ ] [Optional] Use a
      [fat jar](https://stackoverflow.com/questions/19150811/what-is-a-fat-jar)?
      This would allow us to bundle everything into one but might be a lot
      harder than just running the executable in a "modified way".
