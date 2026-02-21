# Deep Etag Spring Boot Starter

A simple, highly capable Spring Boot Starter to automate ETag-based HTTP caching (using `If-None-Match` checks) around your Spring MVC controller methods using SpEL evaluation.

## Getting Started

### 1. Add Repository and Dependency

First, add the JitPack repository to your `pom.xml` just above the dependencies block:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then, add the starter dependency:

```xml
<dependency>
    <groupId>com.github.anurrags</groupId>
    <artifactId>etag-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create an ETag Provider

You need to define a class that implements `EtagProvider` and register it as a Spring Bean (using `@Component` or `@Service`). This provider takes the extracted key and computes the current version (ETag).

```java
import io.github.anurrags.starter.provider.EtagProvider;
import org.springframework.stereotype.Service;

@Service
public class ProductEtagProvider implements EtagProvider {
    
    // Inject your repositories here
    // private final ProductRepository productRepository;
    
    @Override
    public String getVersion(Object key) {
        Long productId = (Long) key;
        
        // Compute the version (could be a timestamp, hash, or database version column)
        // String version = productRepository.findVersionById(productId);
        // return version;
        
        return "v-12345"; // Return null if resource doesn't exist
    }
}
```

### 3. Annotate your Controller

Use the `@DeepEtag` annotation on your controller methods.

*   `provider`: Pass the class type of the `EtagProvider` you created in Step 2.
*   `key`: Provide a SpEL expression to extract the identifier from the method's arguments.

```java
import io.github.anurrags.starter.annotation.DeepEtag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @DeepEtag(provider = ProductEtagProvider.class, key = "#id")
    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        // This method will only execute if the ETag does NOT match 
        // the "If-None-Match" HTTP header sent by the client!
        return productService.getById(id);
    }
}
```

## How It Works

1.  **Request arrives**: The client sends a `GET` request. If they've fetched it before, the request includes an `If-None-Match: "v-12345"` header.
2.  **SpEL Evaluation**: The Aspect intercepts the call, reads the `@DeepEtag` annotation, and evaluates the `key` expression (e.g. `#id`) against the method arguments (in this case, the `@PathVariable Long id`).
3.  **Check Version**: The aspect passes the evaluated key to your `ProductEtagProvider`.
4.  **Short-Circuit**:
    *   **Match**: If the computed version matches the `If-None-Match` header, the aspect automatically halts the request and returns a bare `304 Not Modified` HTTP response. Your controller method is *never* executed. (Saves DB calls and processing overhead).
    *   **Mismatch/No Header**: If they don't match, the controller method proceeds as normal, and the aspect appends an `ETag: "new-version"` header to the outgoing response.

## Disabling the Starter

If you ever need to turn off the Etag logic entirely without removing code, you can disable the autoconfiguration via your `application.properties`:

```properties
etag.deep.enabled=false
```
