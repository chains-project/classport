package tld.domain.me;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface ClassportInfo {
    String name();

    String group();

    String version();
}
