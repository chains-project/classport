package io.github.project.classport.commons;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface ClassportInfo {
    String sourceProjectId();

    boolean isDirectDependency();

    String id();

    String artefact();

    String group();

    String version();

    String[] childIds();
}
