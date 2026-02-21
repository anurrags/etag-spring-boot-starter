package io.github.anurrags.starter.provider;

public interface EtagProvider {
    String getVersion(Object key);
}
