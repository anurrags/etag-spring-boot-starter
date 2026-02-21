package io.github.anurrags.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.anurrags.starter.aspect.EtagAspect;

@Configuration
@ConditionalOnProperty(name = "etag.deep.enabled", havingValue = "true", matchIfMissing = true)
public class EtagConfiguration {

    @Bean
    public EtagAspect etagAspect(ApplicationContext applicationContext) {
        return new EtagAspect(applicationContext);
    }
}
