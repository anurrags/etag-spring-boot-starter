package io.github.anurrags.starter.aspect;

import io.github.anurrags.starter.annotation.DeepEtag;
import io.github.anurrags.starter.provider.EtagProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@RequiredArgsConstructor
@Slf4j
public class EtagAspect {

    private final ApplicationContext applicationContext;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(deepEtag) && execution(* *(..))")
    public Object handleEtag(ProceedingJoinPoint joinPoint, DeepEtag deepEtag) throws Throwable {
        log.debug("Intercepted method: {} with @DeepEtag", joinPoint.getSignature().toShortString());

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.debug("No ServletRequestAttributes found, proceeding without ETag.");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        if (response == null) {
            log.debug("HttpServletResponse is null, proceeding without ETag.");
            return joinPoint.proceed();
        }

        try {
            log.debug("Evaluating SpEL expression: '{}'", deepEtag.key());
            Object evaluatedKey = evaluateSpelKey(joinPoint, deepEtag.key());

            if (evaluatedKey != null) {
                log.debug("SpEL expression evaluated to key: {}", evaluatedKey);
                
                log.debug("Fetching EtagProvider bean of type: {}", deepEtag.provider().getSimpleName());
                EtagProvider provider = applicationContext.getBean(deepEtag.provider());
                
                log.debug("Calling getVersion() on provider.");
                String version = provider.getVersion(evaluatedKey);
                
                if (version != null) {
                    String currentEtag = version.startsWith("\"") ? version : "\"" + version + "\"";
                    log.debug("Current computed ETag: {}", currentEtag);
    
                    String ifNoneMatch = request.getHeader("If-None-Match");
                    log.debug("Received If-None-Match header: {}", ifNoneMatch);
    
                    if (currentEtag.equals(ifNoneMatch)) {
                        log.debug("ETag matches If-None-Match. Returning 304 Not Modified. Controller method will NOT execute.");
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return null;
                    }
                    
                    log.debug("ETag does not match or If-None-Match is missing. Proceeding to controller method.");
                    Object result = joinPoint.proceed();
                    
                    log.debug("Controller method executed. Setting ETag header on response.");
                    response.setHeader("ETag", currentEtag);
                    return result;
                } else {
                    log.debug("Provider returned null version. No ETag logic applied. Proceeding to controller.");
                }
            } else {
                log.debug("Evaluated SpEL key was null. Cannot compute ETag. Proceeding to controller.");
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate ETag for request {}. Proceeding to controller without ETag.", request.getRequestURI(), e);
        }

        return joinPoint.proceed();
    }

    private Object evaluateSpelKey(ProceedingJoinPoint joinPoint, String spelExpression) {
        if (spelExpression == null || spelExpression.isEmpty()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        
        // 1. First try Spring's standard discovery (works if compiled with -parameters)
        org.springframework.core.ParameterNameDiscoverer discoverer = new org.springframework.core.DefaultParameterNameDiscoverer();
        String[] parameterNames = discoverer.getParameterNames(method);

        if (parameterNames != null && parameterNames.length == args.length) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        } 
        // 2. FALLBACK: If parameter names are stripped (e.g., standard Maven/Gradle build without -parameters)
        // We must manually find them by looking at @PathVariable or @RequestParam annotations!
        else {
            log.debug("Standard parameter discovery failed. Scanning for @PathVariable and @RequestParam annotations.");
            java.lang.annotation.Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            
            for (int i = 0; i < args.length; i++) {
                String paramName = null;
                for (java.lang.annotation.Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof org.springframework.web.bind.annotation.PathVariable) {
                        paramName = ((org.springframework.web.bind.annotation.PathVariable) annotation).value();
                        if (paramName.isEmpty()) paramName = ((org.springframework.web.bind.annotation.PathVariable) annotation).name();
                    } else if (annotation instanceof org.springframework.web.bind.annotation.RequestParam) {
                        paramName = ((org.springframework.web.bind.annotation.RequestParam) annotation).value();
                        if (paramName.isEmpty()) paramName = ((org.springframework.web.bind.annotation.RequestParam) annotation).name();
                    }
                }
                
                if (paramName != null && !paramName.isEmpty()) {
                    log.debug("Discovered parameter name '{}' from annotation at index {}", paramName, i);
                    context.setVariable(paramName, args[i]);
                } else {
                    // We can't find a name, provide a default indexed fallback like p0, p1, a0, a1
                    context.setVariable("p" + i, args[i]);
                    context.setVariable("a" + i, args[i]); 
                }
            }
        }

        Expression expression = parser.parseExpression(spelExpression);
        return expression.getValue(context);
    }
}
