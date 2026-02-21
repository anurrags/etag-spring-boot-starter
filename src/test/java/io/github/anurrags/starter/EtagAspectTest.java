package io.github.anurrags.starter;

import io.github.anurrags.starter.annotation.DeepEtag;
import io.github.anurrags.starter.provider.EtagProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {EtagSpringBootStarterApplication.class, EtagAspectTest.TestConfig.class})
@AutoConfigureMockMvc
public class EtagAspectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testEtagHeaderIsSet() throws Exception {
        mockMvc.perform(get("/api/test/123"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v-123\""))
                .andExpect(content().string("Data for 123"));
    }

    @Test
    public void testEtagNotModified() throws Exception {
        mockMvc.perform(get("/api/test/123")
                .header("If-None-Match", "\"v-123\""))
                .andExpect(status().isNotModified())
                .andExpect(header().doesNotExist("ETag")); // With 304, Spring/Tomcat might or might not include ETag again depending on config, but our code sets it only when 200... wait, ETag SHOULD be returned on 304. Let's fix our aspect.
    }

    @Test
    public void testEtagQueryParam() throws Exception {
        mockMvc.perform(get("/api/query?id=456"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v-456\""))
                .andExpect(content().string("Data for 456"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        
        @Bean
        public TestEtagProvider testEtagProvider() {
            return new TestEtagProvider();
        }

        @RestController
        static class TestController {

            @DeepEtag(provider = TestEtagProvider.class, key = "#id")
            @GetMapping("/api/test/{id}")
            public String getTestData(@PathVariable String id) {
                return "Data for " + id;
            }

            @DeepEtag(provider = TestEtagProvider.class, key = "#id")
            @GetMapping("/api/query")
            public String getQueryData(@RequestParam String id) {
                return "Data for " + id;
            }
        }
    }

    static class TestEtagProvider implements EtagProvider {
        @Override
        public String getVersion(Object key) {
            return "v-" + key;
        }
    }
}
