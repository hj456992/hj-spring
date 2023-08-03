package org.example.aop;

import org.example.annotation.Around;
import org.example.context.ApplicationContextUtils;
import org.example.context.BeanDefinition;
import org.example.context.BeanPostProcessor;
import org.example.context.ConfigurableApplicationContext;
import org.example.io.ProxyResolver;

import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * 这个AroundBeanPostProcessor类的作用是在Bean初始化前检查是否标记了Around注解，
 * 如果标记了，就根据Around注解中指定的拦截器对象，创建一个代理对象，并在代理对象中应用AOP拦截逻辑。
 * 这样，我们就可以在特定的Bean上使用Around注解来实现AOP拦截。
 */
public class AroundBeanPostProcessor implements BeanPostProcessor {

    Map<String, Object> originBean = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // 从@Aound注解的value中获取拦截器对象
        Around around = bean.getClass().getAnnotation(Around.class);
        if (around == null) {
            return bean;
        }
        String handlerName = around.value();
        if (handlerName == null) {
            return bean;
        }
        ConfigurableApplicationContext ctx
                = (ConfigurableApplicationContext) ApplicationContextUtils.getApplicationContext();
        BeanDefinition handlerDef = ctx.findBeanDefinition(handlerName);
        Object handler = handlerDef.getInstance();
        if (handler == null) {
            handler = ctx.createBeanAsEarlySingleton(handlerDef);
        }
        if (handler == null) {
            // todo 记录错误
            return bean;
        }
        if (!(handler instanceof InvocationHandler)) {
            // todo 记录错误
            return bean;
        }

        // 将bean与拦截器对象传入ProxyResolver获取proxy
        Object proxy = ProxyResolver.getInstance().createProxy(bean, (InvocationHandler)handler);
        // 记录原实例
        originBean.put(beanName, proxy);
        // 替换原实例
        return proxy;
    }
}
