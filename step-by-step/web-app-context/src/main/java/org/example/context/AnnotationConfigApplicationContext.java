package org.example.context;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.annotation.*;
import org.example.exception.BeanCreationException;
import org.example.exception.BeanDefinitionException;
import org.example.exception.BeansException;
import org.example.io.PropertyResolver;
import org.example.io.ResourceResolver;
import org.example.utils.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class AnnotationConfigApplicationContext implements ConfigurableApplicationContext {

    protected final PropertyResolver propertyResolver;

    protected final Map<String, BeanDefinition> beans;

    // 用来检测循环依赖
    protected Set<String> creatingBeanNames;

    protected List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        ApplicationContextUtils.setApplicationContext(this);

        this.propertyResolver = propertyResolver;

        // 扫描获取所有bean的class类型
        Set<String> beanClassNames = scanForClassNames(configClass);
        // 创建bean的定义
        beans = createBeanDefinitions(beanClassNames);

        // 创建BeanNames检测循环依赖
        this.creatingBeanNames = new HashSet<>();

        // 创建@Configuration类型的Bean
        this.beans.values().stream().filter(BeanDefinition::isConfiguration).sorted()
                .forEach(this::createBeanAsEarlySingleton);

        // 创建beanPostProcessors
        this.beanPostProcessors.addAll(
                this.beans.values().stream()
                        .filter(this::isBeanPostProcessor).sorted()
                        .map(def -> (BeanPostProcessor)createBeanAsEarlySingleton(def))
                        .toList()
        );

        // 创建普通的Bean
        createNormalBeans();

        // 通过字段和set方法注入依赖
        this.beans.values().forEach(def -> {
            injectBean(def);
        });
        // 调用init方法
        this.beans.values().forEach(def -> {
            initBean(def);
        });
    }

    public void createNormalBeans() {
        // 获取尚未实例化的BeanDefinition列表
        List<BeanDefinition> defs =
                this.beans.values().stream().filter(it -> it.getInstance() == null).sorted().toList();

        defs.forEach(
                def -> {
                    // 可能已经在其他Bean实例化的过程中已被实例化
                    if (def.getInstance() == null) {
                        createBeanAsEarlySingleton(def);
                    }
                }
        );
    }

    public Object createBeanAsEarlySingleton(BeanDefinition def) {
        // 检测循环依赖
        if (this.creatingBeanNames.add(def.getName())) {
            // 抛出循环依赖异常
            throw new BeanCreationException("创建【"+def.getName()+"】时检测到循环依赖");
        }

        // 创建方式：工厂方法或者构造函数
        Executable fn = null;
        if (def.getFactoryName() != null) {
            fn = def.getFactoryMethod();
        } else {
            fn = def.getConstructor();
        }

        // 创建参数
        Parameter[] params = fn.getParameters();
        Annotation[][] paramsAnnos = fn.getParameterAnnotations();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i ++) {
            Parameter param = params[i];
            Annotation[] paramAnnotations = paramsAnnos[i];
            Value value = ClassUtils.getAnnotation(paramAnnotations, Value.class);
            Autowired autowired = ClassUtils.getAnnotation(paramAnnotations, Autowired.class);

            // Configuration类型的Bean是工厂，不允许使用Autowired创建
            if (def.isConfiguration() && autowired != null) {
                throw new BeanCreationException(def.getName() + "是工厂，不允许使用Autowired创建");
            }
            // Value和Autowired不能同时为空
            if (value == null && autowired == null) {
                throw new BeanCreationException(def.getName() + "的参数，必须由Value或者Autowired注解");
            }
            // Value和Autowired不能同时存在
            if (value != null && autowired != null) {
                throw new BeanCreationException(def.getName() + "的参数，只能由Value或者Autowired注解");
            }

            // 参数类型
            Class<?> type = param.getType();
            if (value != null) {
                // 如果是Value，直接获取值即可
                args[i] = this.propertyResolver.getProperty(value.value(), type);
            } else {
                // 参数是Autowired
                String name = autowired.name();
                boolean isRequired = autowired.value();

                BeanDefinition dependDef = name.isEmpty() ? findBeanDefinition(type) : findBeanDefinition(name, type);
                if (isRequired && dependDef == null) {
                    throw new BeanCreationException(String.format("当创建Bean '%s': %s. 时，缺少 '%s' 的已注入的Bean", type.getName(),
                            def.getName(), def.getBeanClass().getName()));
                }

                if (dependDef != null) {
                    Object dependDefInstance = dependDef.getInstance();
                    if (dependDefInstance == null && !dependDef.isConfiguration()) {
                        // 递归生成注入的Bean
                        createBeanAsEarlySingleton(dependDef);
                    }
                    args[i] = dependDefInstance;
                } else {
                    args[i] = null;
                }
            }
        }

        // 创建Bean实例
        Object instance = null;
        if (def.getFactoryName() == null) {
            // 构造方法创建
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("创建Bean实例出错，Bean为 '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        } else {
            // @Bean创建
            Object factoryInstance = this.beans.get(def.getFactoryName());
            try {
                instance = def.getFactoryMethod().invoke(factoryInstance, args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("创建Bean实例出错，Bean为 '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        }
        def.setInstance(instance);

        // 调用BeanPostProcessor来处理bean
        for (BeanPostProcessor postProcessor : beanPostProcessors) {
            Object processed = postProcessor.postProcessBeforeInitialization(def.getInstance(), def.getName());
            if (processed == null) {
                throw new BeanCreationException("postProcessor在Bean实例化前处理【"+def.getName()+"】出现空异常");
            }
            // processed已被替换，则更新instance
            if (def.getInstance() != processed) {
                def.setInstance(processed);
            }
        }
        return def.getInstance();
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

    void injectBean(BeanDefinition def) {
        // 因为BeanPostProcessor可能修改掉bean实例，所以这里不能直接使用def.getInstance获取实例，要去拿原始的实例
        injectProperties(def, def.getBeanClass(), getProxiedInstance(def));
    }

    Object getProxiedInstance(BeanDefinition def) {
        Object instance = def.getInstance();
        List<BeanPostProcessor> reversed = new ArrayList<>(beanPostProcessors);
        Collections.reverse(reversed);
        for (BeanPostProcessor postProcessor : reversed) {
            Object restoredInstance = postProcessor.postProcessOnSetProperty(instance, def.getName());
            if (restoredInstance != instance) {
                instance = restoredInstance;
            }
        }
        return instance;
    }

    void initBean(BeanDefinition def) {
        callMethod(def.getInstance(), def.getInitMethod(), def.getInitMethodName());
    }

    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) {
        // 在当前类查找field和method并注入
        for (Field f : clazz.getDeclaredFields()) {
            try {
                tryInjectField(bean, f);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException("【"+def.getName()+"】注入字段【"+f.getName()+"】失败");
            }
        }
        for (Method m : clazz.getDeclaredMethods()) {
            try {
                tryInjectMethod(bean, m);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException("【"+def.getName()+"注入Method【"+m.getName()+"】失败");
            }
        }

        // 在父类查找field和method并注入
        Class<?> supperClass = clazz.getSuperclass();
        if (supperClass != null) {
            injectProperties(def, supperClass, bean);
        }
    }

    void tryInjectField(Object bean, Field field) throws ReflectiveOperationException {
        Value value = field.getAnnotation(Value.class);
        Autowired autowired = field.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }
        if (value != null && autowired != null) {
            throw new BeanCreationException("注入字段时，字段不能同时被Value和Autowired注解");
        }

        field.setAccessible(true);

        // value注入
        if (value != null) {
            Object propValue = this.propertyResolver.getProperty(value.value(), field.getType());
            field.set(bean, propValue);
        }

        // autowired注入
        if (autowired != null) {
            String name = autowired.name();
            boolean isRequired = autowired.value();
            Object depends = name.isEmpty() ? findBeanDefinition(field.getType()) : findBeanDefinition(name, field.getType());
            if (isRequired && depends == null) {
                throw new BeanCreationException("注入"+name+"时该Bean不存在");
            }
            if (depends != null) {
                field.set(bean, depends);
            }
        }
    }

    void tryInjectMethod(Object bean, Method method) throws ReflectiveOperationException {
        Value value = method.getAnnotation(Value.class);
        Autowired autowired = method.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }
        if (value != null && autowired != null) {
            throw new BeanCreationException("注入Method时，Method不能同时被Value和Autowired注解");
        }

        method.setAccessible(true);

        // value注入
        if (value != null) {
            Object propValue = this.propertyResolver.getProperty(value.value(), method.getReturnType());
            method.invoke(bean, propValue);
        }

        // autowired注入
        if (autowired != null) {
            String name = autowired.name();
            boolean isRequired = autowired.value();
            Object depends = name.isEmpty() ? findBeanDefinition(method.getReturnType()) : findBeanDefinition(name, method.getReturnType());
            if (isRequired && depends == null) {
                throw new BeanCreationException("注入"+name+"时该Bean不存在");
            }
            if (depends != null) {
                method.invoke(bean, depends);
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
    public BeanDefinition findBeanDefinition(String name, Class<?> type) {
        BeanDefinition def = beans.get(name);
        if (def == null) {
            return null;
        }

        if (type != def.getBeanClass()) {
            throw new BeansException(name + "的类型为"+def.getBeanClass().getName()+"，与要求返回的"+type.getName()+"类型不一致");
        }
        return def;
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


    @Override
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 根据type查找若干个BeanDefinition，返回0个或者多个
     *
     * type.isAssignableFrom(def.getClass())的意思是，def的类型是否是type或者其子类
     */
    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return beans.values().stream().filter(def -> type.isAssignableFrom(def.getClass())).sorted().toList();
    }

    private void callMethod(Object beanInstance, Method method, String namedMethod) {
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else {
            try {
                Method m = beanInstance.getClass().getMethod(namedMethod);
                m.setAccessible(true);
                m.invoke(beanInstance);
            } catch (NoSuchMethodException e) {
                throw new BeanDefinitionException("没有该方法【" + namedMethod + "】");
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    private boolean isBeanPostProcessor(BeanDefinition def) {
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
    }

    @Override
    public void close() {
        for (BeanDefinition def : this.beans.values()) {
            Object instance = getProxiedInstance(def);
            callMethod(instance, def.getDestroyMethod(), def.getDestroyMethodName());
        }

        this.beans.clear();
        ApplicationContextUtils.setApplicationContext(null);
    }
}
