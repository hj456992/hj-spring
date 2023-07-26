package org.example.context;

import jakarta.annotation.Nullable;
import org.example.annotation.Configuration;
import org.example.utils.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BeanDefinition {

    // 全局唯一的bean名字
    private final String name;

    // bean的声明类型
    /**
     * 对于@Component定义的bean，其声明类型就是Class本身，即声明类型和实际类型相同。
     * 但是对于工厂方法创建的bean，其声明类型不一定是Class本身，实际类型可能是声明类型的子类。
     * 因此，获取实际类型，使用instance.getClass()进行获取。
     */
    private final Class<?> beanClass;

    // bean的实例
    private Object instance = null;

    /**
     * 构造方法
     */
    private Constructor<?> constructor;

    // 工厂方法名
    private final String factoryName;

    // 工厂方法
    private final Method factoryMethod;

    // bean的顺序
    private final int order;

    // 是否标志@Primary
    private final boolean isPrimary;

    /**
     * 我们同时存储了initMethodName和initMethod，以及destroyMethodName和destroyMethod，
     * 这是因为在@Component声明的Bean中，我们可以根据@PostConstruct和@PreDestroy直接拿到Method本身，
     * 而在@Bean声明的Bean中，我们拿不到Method，只能从@Bean注解提取出字符串格式的方法名称，
     * 因此，存储在BeanDefinition的方法名称与方法，其中总有一个为null。
     */
    // 初始化方法名
    private String initMethodName;

    // 销毁方法名
    private String destroyMethodName;

    // 初始化方法
    private Method initMethod;

    // 销毁方法
    private Method destroyMethod;


    /**
     * 对于使用@Componet注解的bean，需要获取class类型和构造方法来创建bean，同时需要收集@PostConstruct和@PreDestroy标注的
     * 初始化和销毁方法，以及其他信息，比如@Order定义的顺序，@Primary定义的存在多个相同类型时返回哪个bean
     */
    public BeanDefinition(String name, Class<?> beanClass, Constructor<?> constructor, int order, boolean isPrimary
            , String initMethodName, String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = constructor;
        this.order = order;
        this.isPrimary = isPrimary;
        this.factoryMethod = null;
        this.factoryName = null;
        constructor.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    /**
     * 对于@Configuration定义的@Bean方法，将其看做是工厂方法，需要将方法的返回值作为Class类型，方法本身作为创建bean的工厂方法，
     * 然后收集bean定义的initMethod和destroyMethod作为初始化和销毁方法，以及其他的信息，比如@Order定义的顺序，@Primary定义的存在多个相同类型时返回哪个bean
     *
     * 一个典型的示例如下：
     * ```
     * @Configuration
     * public class Appconfig{
     *     @Bean(initMethod="init", destroyMethod="destroy")
     *     Datasource getDatasource(){
     *         return new HikariDatasource();
     *     }
     * }
     * ```
     */
    public BeanDefinition(String name, Class<?> beanClass, String factoryName, Method factoryMethod, int order, boolean isPrimary
            , String initMethodName, String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.factoryName = factoryName;
        this.factoryMethod = factoryMethod;
        this.order = order;
        this.isPrimary = isPrimary;
        this.constructor = null;
        factoryMethod.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    private void setInitAndDestroyMethod(String initMethodName, String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.initMethodName = initMethodName;
        this.destroyMethodName = destroyMethodName;
        if (initMethod != null) {
            initMethod.setAccessible(true);
        }
        if (destroyMethod != null) {
            destroyMethod.setAccessible(true);
        }
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    public boolean isConfiguration() {
        return ClassUtils.findAnnotation(this.beanClass, Configuration.class) != null;
    }

    @Nullable
    public boolean isPrimary() {
        return isPrimary;
    }

    public String getName() {
        return name;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public Constructor<?> getConstructor() {
        return constructor;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public Method getFactoryMethod() {
        return factoryMethod;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }
}
