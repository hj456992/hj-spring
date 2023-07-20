package org.example.context;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.annotation.*;
import org.example.annotation.Component;
import org.example.exception.BeanDefinitionException;
import org.example.io.PropertyResolver;
import org.example.io.ResourceResolver;
import org.example.utils.ClassUtils;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

public class AnnotationConfigApplicationContext {

    protected final PropertyResolver propertyResolver;

    protected final Map<String, BeanDefinition> beans;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        // 扫描获取所有bean的class类型
        Set<String> beanClassNames = scanForClassNames(configClass);
        // 创建bean的定义
        beans = createBeanDefinitions(beanClassNames);
    }

    Set<String> scanForClassNames(Class<?> configClass) {
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        String[] packages = scan.value() == null || scan.value().length == 0
                ? new String[] {configClass.getPackageName()} : scan.value();

        Set<String> classNameSet = new HashSet<>();
        for (String pkg : packages) {
            ResourceResolver rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(res -> {
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            // 将扫描结果放入classSet
            classNameSet.addAll(classList);
        }

        // 继续查找@Import(Xyz.class)导入的Class配置:
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                classNameSet.add(importClassName);
            }
        }
        return classNameSet;
    }

    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        Map<String, BeanDefinition> defs = new HashMap<>();
        for (String className : classNameSet) {
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new BeanDefinitionException(e);
            }
            // 寻找Component注解
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if (component != null) {
                // 获取bean名称
                String beanName = ClassUtils.getBeanName(clazz);
                // 构造BeanDefinition
                BeanDefinition def = new BeanDefinition(
                        beanName, clazz, getSuitableConstructor(clazz), getOrder(clazz), clazz.isAnnotationPresent(Primary.class),
                        // 无factory
                        // initMethod、destroyMethod
                        null, null,
                        ClassUtils.findAnnotationMethod(clazz, PostConstruct.class),
                        ClassUtils.findAnnotationMethod(clazz, PreDestroy.class)
                );
                addBeanDefinitions(defs, def);
                // 如果有Configuration，则扫描其中的Bean方法
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanName, clazz, defs);
                }
            }
        }
        return defs;
    }

    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs) {
        for (Method method: clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                // Bean的声明类型是方法返回类型:
                Class<?> beanClass = method.getReturnType();
                var def = new BeanDefinition(
                        ClassUtils.getBeanName(method), beanClass,
                        factoryBeanName,
                        // 创建Bean的工厂方法:
                        method,
                        // @Order
                        getOrder(method),
                        // 是否存在@Primary标注?
                        method.isAnnotationPresent(Primary.class),
                        // init方法名称:
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        // destroy方法名称:
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        // @PostConstruct / @PreDestroy方法:
                        null, null);
                addBeanDefinitions(defs, def);
            }
        }
    }

    /**
     * 这段代码的主要目的是获取一个类的合适的构造器。它首先尝试获取类的所有公共构造器，如果没有公共构造器，那么它会尝试获取类的所有声明的构造器（包括私有的、保护的和默认的）。如果在任何一种情况下，类有多于一个的构造器，那么它会抛出一个BeanDefinitionException异常。
     *
     * 以下是这段代码的详细步骤：
     *
     * 1、使用getConstructors方法获取类的所有公共构造器。
     * 2、如果没有公共构造器，那么使用getDeclaredConstructors方法获取类的所有声明的构造器。
     * 3、如果在任何一种情况下，类有多于一个的构造器，那么抛出一个BeanDefinitionException异常。
     * 4、如果类只有一个构造器，那么返回这个构造器。
     *
     * 这段代码假设一个类只能有一个构造器，这在某些情况下可能是正确的，例如在某些设计模式（如单例模式）中。然而，在大多数情况下，一个类可能有多个构造器，这段代码在这种情况下可能会抛出异常。
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor[] cons = clazz.getConstructors();
        if (cons.length == 0) {
            cons = clazz.getDeclaredConstructors();
            if (cons.length != 1) {
                throw new BeanDefinitionException("构造器数量有且只能有一个");
            }
        }
        if (cons.length != 1) {
            throw new BeanDefinitionException("构造器数量有且只能有一个");
        }
        return cons[0];
    }

    public int getOrder(Class<?> clazz) {
        Order order = ClassUtils.findAnnotation(clazz, Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    public int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return beans.get(name);
    }

    /**
     * Check and add bean definitions.
     */
    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> beanDefinitions = findBeanDefinitions(type);
        if (beanDefinitions.isEmpty()) {
            return null;
        }
        if (beanDefinitions.size() == 1) {
            return beanDefinitions.get(0);
        }
        // 多余一个时，查找primary
        List<BeanDefinition> primaryDefs = beanDefinitions.stream().filter(BeanDefinition::isPrimary).toList();
        if (primaryDefs.size() == 1) {
            return primaryDefs.get(0);
        }
        if (primaryDefs.isEmpty()) { // 不存在@Primary
            throw new BeanDefinitionException(
                    String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else { // @Primary不唯一
            throw new BeanDefinitionException(
                    String.format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }

    /**
     * 根据type查找若干个BeanDefinition，返回0个或者多个
     *
     * type.isAssignableFrom(def.getClass())的意思是，def的类型是否是type或者其子类
     */
    List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return beans.values().stream().filter(def -> type.isAssignableFrom(def.getClass())).sorted().toList();
    }
}
