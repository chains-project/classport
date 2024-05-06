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

- [x] Compare the classes logged with "java -verbose:class" and check for
      discrepancies (just basic manual checks for now)
  - There are some, not yet sure if this is because of shading, embedding errors
    or some oversight.
- [x] Optimise the plugin a bit. It's not really a bottleneck, but there are
      some trivial things that can be done such as not re-embedding info if two
      packages depend on the same artefact.
- [x] Insert even more supply chain information, making sure that dependency
      relationships and all other required SBOM attributes are present
- [x] Investigate errors
- [ ] Have the agent generate a "proper" (but simple) SBOM
  - How to do this if we can't get a graceful shutdown? (Graphhopper)
- [ ] (Optionally) make two separate annotations: one that is runtime-visible
      and one that's not. The non-visible will likely be the
      "default"/recommended version.
- [ ] Thoroughly compare the generated SBOM to the "ground truth" emitted by
      "java -verbose:class". (Can this be considered ground truth or are there
      things that don't show up, e.g. classes depended upon by our transformer?)
