# Classport

## Comparing JAR files

To compare two different JAR files, ignoring date and time, run the following
command (where `jar1` and `jar2` are the paths to the respective JARs):

```sh
diff \
    <(unzip -l $jar1 | awk '{print $1, $4}') \
    <(unzip -l $jar2 | awk '{print $1, $4}') \
    --report-identical-files
```

## Implementation

Using Java annotations. Why? Well...

> Annotations, a form of metadata, provide data about a program that is not part
> of the program itself. Annotations have no direct effect on the operation of
> the code they annotate.
>
> Annotations have a number of uses, among them:
>
> - Information for the compiler — Annotations can be used by the compiler to
  > detect errors or suppress warnings.
> - Compile-time and deployment-time processing — Software tools can process
  > annotation information to generate code, XML files, and so forth.
> - Runtime processing — Some annotations are available to be examined at
  > runtime.
>
> This lesson explains where annotations can be used, how to apply annotations,
> what predefined annotation types are available in the Java Platform, Standard
> Edition (Java SE API), how type annotations can be used in conjunction with
> pluggable type systems to write code with stronger type checking, and how to
> implement repeating annotations.
>
> -- <cite>
> [Oracle](https://docs.oracle.com/javase/tutorial/java/annotations/)</cite>

### TODOs

In priority order, from highest to lowest.

- [x] Replicate the maven directory structure so that we can point the build
      process to this directory instead of the default `~/.m2` one by using the
      `-Dmaven.repo.local` command-line argument.
- [ ] Figure out how to get the following metadata:
  - [ ] Checksums
  - [x] Parent info (Maven's
        [dependencyGraphBuilder](https://github.com/apache/maven-dependency-plugin/blob/c9e488ba11516aa5b4be22fedd5b109ab11fa32c/src/main/java/org/apache/maven/plugins/dependency/tree/TreeMojo.java#L239)
        maybe, see
        [this StackOverflow answer](https://stackoverflow.com/a/35380442))
  - [ ] URLs
- [ ] Create an actual interface for the annotation.
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
- [ ] Create a Java agent to run it, as it sees all classes being loaded and can
      thus build the SBOM?
- [ ] [Optional] Use a
      [fat jar](https://stackoverflow.com/questions/19150811/what-is-a-fat-jar)?
      This would allow us to bundle everything into one but might be a lot
      harder than just running the executable in a "modified way".
