package tld.domain.me.classport.commons;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface ClassportInfo {
    String id();

    String artefact();

    String group();

    String version();

    String parentId();
}
