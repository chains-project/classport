package io.github.chains_project.classport.commons;

import java.lang.annotation.*;

public class ClassportHelper {
    public ClassportInfo getInstance(String id, String artefact, String group, String version, String[] childIds) {
        return new ClassportInfo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ClassportInfo.class;
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public String artefact() {
                return artefact;
            }

            @Override
            public String group() {
                return group;
            }

            @Override
            public String version() {
                return version;
            }

            @Override
            public String[] childIds() {
                return childIds;
            }
        };
    }
}
