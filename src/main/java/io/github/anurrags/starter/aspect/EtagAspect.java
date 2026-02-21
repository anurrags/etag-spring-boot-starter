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
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        if (response == null) {
            return joinPoint.proceed();
        }

        try {
            Object evaluatedKey = evaluateSpelKey(joinPoint, deepEtag.key());

            if (evaluatedKey != null) {
                EtagProvider provider = applicationContext.getBean(deepEtag.provider());
                String version = provider.getVersion(evaluatedKey);
                
                if (version != null) {
                    String currentEtag = version.startsWith("\"") ? version : "\"" + version + "\"";
    
                    String ifNoneMatch = request.getHeader("If-None-Match");
    
                    if (currentEtag.equals(ifNoneMatch)) {
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return null;
                    }
                    
                    Object result = joinPoint.proceed();
                    response.setHeader("ETag", currentEtag);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate ETag for request {}. Proceeding without ETag.", request.getRequestURI(), e);
        }

        return joinPoint.proceed();
    }

    private Object evaluateSpelKey(ProceedingJoinPoint joinPoint, String spelExpression) {
        if (spelExpression == null || spelExpression.isEmpty()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();

        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                // SpEL variables are accessed with #, so we set them as variables
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(spelExpression);
        return expression.getValue(context);
    }
}
