package com.example.external_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.anurrags.starter.annotation.DeepEtag;
import io.github.anurrags.starter.provider.EtagProvider;

@SpringBootApplication
public class ExternalTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExternalTestApplication.class, args);
	}

}

@Service
class DemoProvider implements EtagProvider {
    @Override
    public String getVersion(Object key) {
        System.out.println("======> getVersion() CALLED WITH KEY: " + key);
        return "version-of-" + key;
    }
}

@RestController
@RequestMapping("/api")
class DemoController {

    @DeepEtag(provider = DemoProvider.class, key = "#id")
    @GetMapping("/demo/{id}")
    public String getDemo(@PathVariable String id) {
        System.out.println("======> getDemo() CONTROLLER EXECUTED FOR ID: " + id);
        return "Hello " + id;
    }
}
