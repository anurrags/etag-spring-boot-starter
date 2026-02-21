package io.github.anurrags.starter.annotation;

import io.github.anurrags.starter.provider.EtagProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeepEtag {
    
    // Which class knows how to check the version for this data?
    Class<? extends EtagProvider> provider();
    
    // SpEL expression to find the ID in the method arguments (e.g., "#id")
    String key();
}