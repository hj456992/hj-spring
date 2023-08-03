package org.example.aop;

import org.example.context.ApplicationContextUtils;
import org.example.context.BeanDefinition;
import org.example.context.BeanPostProcessor;
import org.example.context.ConfigurableApplicationContext;
import org.example.io.ProxyResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AnnoBeanPostProcessor <A extends Annotation> implements BeanPostProcessor {

    Map<String, Object> originBean = new HashMap<>();

    Class<A> annoClass;

    public AnnoBeanPostProcessor() {
        this.annoClass = getAnnoClass();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // 从annoClass注解的value中获取拦截器对象
        A a = bean.getClass().getAnnotation(annoClass);
        String handlerName;
        try {
            handlerName = (String) a.getClass().getMethod("value").invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ConfigurableApplicationContext ctx
                = (ConfigurableApplicationContext) ApplicationContextUtils.getApplicationContext();
        BeanDefinition handlerDef = ctx.findBeanDefinition(handlerName);
        if (handlerDef == null) {
            return bean;
        }
        Object handlerInstance = handlerDef.getInstance();
        if (handlerInstance == null) {
            handlerInstance = ctx.createBeanAsEarlySingleton(handlerDef);
        }

        // 调用Proxy-Resolver获取proxy
        Object proxy = ProxyResolver.getInstance().createProxy(bean, (InvocationHandler) handlerInstance);
        // 存储原Bean
        originBean.put(beanName, bean);
        // 替换原Bean
        return proxy;
    }

    private Class<A> getAnnoClass() {
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType)) {
            throw new RuntimeException();
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type[] types = pt.getActualTypeArguments();
        if (types.length != 1) {
            throw new RuntimeException();
        }

        Type r = types[0];
        if (!(r instanceof Class<?>)) {
            throw new RuntimeException();
        }
        return (Class<A>) r;
    }
}
