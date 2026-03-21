package com.github.gtexpert.bquclaim.api.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TModule {

    String moduleID();

    String containerID();

    String name();

    String[] modDependencies() default {};

    boolean coreModule() default false;

    String description() default "";
}
