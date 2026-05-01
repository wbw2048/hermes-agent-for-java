package com.hermes.agent.controller;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工具调用追踪器。
 * <p>
 * 通过 Spring AOP 代理包装工具 Bean，拦截 {@code @Tool} 方法调用，
 * 记录调用名称、参数、结果、错误和耗时，存入 ThreadLocal。
 */
@Component
public class ToolCallTracker {

    private static final Logger log = LoggerFactory.getLogger(ToolCallTracker.class);

    private static final ThreadLocal<List<ToolCallInfo>> TRACKING = new ThreadLocal<>();

    /**
     * 开始追踪当前线程的工具调用。
     */
    public void startTracking() {
        TRACKING.set(new ArrayList<>());
    }

    /**
     * 停止追踪并返回本轮的工具调用列表。
     */
    public List<ToolCallInfo> stopTracking() {
        List<ToolCallInfo> calls = TRACKING.get();
        TRACKING.remove();
        return calls != null ? calls : List.of();
    }

    /**
     * 获取当前线程已记录的工具调用（不终止追踪）。
     */
    public List<ToolCallInfo> getCalls() {
        List<ToolCallInfo> calls = TRACKING.get();
        return calls != null ? List.copyOf(calls) : List.of();
    }

    /**
     * 清除当前线程的追踪数据。
     */
    public void reset() {
        TRACKING.remove();
    }

    /**
     * 用 Spring AOP 代理包装工具 Bean。
     * <p>
     * 使用 CGLIB 子类代理（targetClass=true），无需接口。
     * 拦截带有 {@code @Tool} 注解的方法：记录调用信息后委托给原方法。
     *
     * @param bean 原始工具 Bean
     * @return 代理对象，行为与原 Bean 一致但会记录 @Tool 调用
     */
    public Object wrap(Object bean) {
        Class<?> cls = bean.getClass();
        boolean hasTool = Arrays.stream(cls.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Tool.class));
        if (!hasTool) {
            return bean;
        }

        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            Method method = invocation.getMethod();
            // 检查原始类上的 @Tool 注解
            Method targetMethod = cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
            if (!targetMethod.isAnnotationPresent(Tool.class)) {
                return invocation.proceed();
            }

            Tool toolAnno = targetMethod.getAnnotation(Tool.class);
            String toolName = toolAnno.name().isEmpty() ? method.getName() : toolAnno.name();
            String argsStr = formatArgs(invocation.getArguments(), targetMethod);
            long start = System.currentTimeMillis();

            try {
                Object result = invocation.proceed();
                long elapsed = System.currentTimeMillis() - start;
                String resultStr = result != null ? result.toString() : "(null)";
                if (resultStr.length() > 5000) {
                    resultStr = resultStr.substring(0, 5000) + "... (truncated)";
                }
                recordCall(new ToolCallInfo(toolName, argsStr, resultStr, null, elapsed));
                log.info("[TRACKER] tool={} elapsed={}ms", toolName, elapsed);
                return result;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                recordCall(new ToolCallInfo(toolName, argsStr, null, cause.getMessage(), elapsed));
                log.warn("[TRACKER] tool={} error={} elapsed={}ms", toolName, cause.getMessage(), elapsed);
                throw e;
            }
        });

        return factory.getProxy(cls.getClassLoader());
    }

    private static String formatArgs(Object[] args, Method method) {
        if (args == null || args.length == 0) return "()";
        var params = method.getParameters();
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            String name = i < params.length ? params[i].getName() : "arg" + i;
            sb.append(name).append("=").append(args[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    private void recordCall(ToolCallInfo info) {
        List<ToolCallInfo> calls = TRACKING.get();
        if (calls != null) {
            calls.add(info);
        }
    }
}
