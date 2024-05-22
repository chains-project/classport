package io.github.chains_project.classport.commons;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface ClassportInfo {
    boolean isDirectDependency();

    String id();

    String artefact();

    String group();

    String version();

    String[] childIds();
}