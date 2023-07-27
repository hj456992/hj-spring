package org.example.utils;

import org.example.annotation.Bean;
import org.example.annotation.Component;
import org.example.exception.BeanDefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ClassUtils {

    /**
     * 递归查找Annotation
     *
     * 示例：Annotation A可直接在Class中标注
     *
     * <code>
     * @A
     * public Class Hello{
     * }
     * </code>
     *
     * 或者Annotation B标注了Annotation A，Class标注了Annotation B
     *
     * <code>
     * @A
     * public @interface B{}
     *
     * @B
     * public class Hello{
     * }
     * </code>
     *
     * 如果target类注入了重复的annotation，则报错
     */
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A a = target.getAnnotation(annoClass);
        for (Annotation anno : target.getAnnotations()) {
            Class<? extends Annotation> annoType = anno.annotationType();
            if (!annoType.getPackageName().equals("java.lang.annotation")) {// 仅去重自定义注解
                A found = findAnnotation(annoType, annoClass);
                if (found != null) {
                    if (a != null) {
                        throw new BeanDefinitionException("Duplicate @" + annoClass.getSimpleName() + " found on class " + target.getSimpleName());
                    }
                    a = found;
                }
            }
        }
        return a;
    }

    /**
     * 这段代码是一个名为getBeanName的方法，它接收一个Class<?>类型的参数clazz，并返回一个字符串。这个方法的主要作用是获取类上的@Component注解的值，如果没有找到@Component注解，那么它会在其他注解中查找@Component。如果仍然没有找到，那么它会返回类的简单名称，首字母小写。
     *
     * 具体步骤如下：
     *
     * 首先，它尝试获取clazz上的@Component注解。如果存在，那么它会获取注解的值并赋值给name。
     *
     * 如果clazz上没有@Component注解，那么它会遍历clazz上的所有注解，并在每个注解的类型上查找@Component注解。如果找到，那么它会尝试通过反射获取注解的value方法，并调用该方法获取值。
     *
     * 如果在上述步骤中都没有找到@Component注解，或者找到了但是值为空，那么它会获取clazz的简单名称，并将首字母转换为小写，作为默认的name。
     *
     * 最后，返回name。
     *
     * 这个方法可能是用在Spring框架的上下文中，用于获取Bean的名称。在Spring框架中，@Component注解通常用于标记一个类为Spring管理的Bean，注解的值通常用作Bean的名称。
     */
    public static String getBeanName(Class<?> clazz) {
        Component component = clazz.getAnnotation(Component.class);

        String name = null;
        if (component != null) {
            name = component.value();
        } else {
            for (Annotation anno : clazz.getAnnotations()) {
                if (findAnnotation(anno.annotationType(), Component.class) != null) {
                    try {
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }

        if (name == null) {
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }


    /**
     * 这段代码定义了一个名为findAnnotationMethod的方法，它的作用是在给定的类clazz中查找带有特定注解annoClass的方法。这个方法返回的是一个Method对象，如果在类中没有找到带有指定注解的方法，那么就返回null。
     *
     * 这个方法的实现使用了Java 8的流式API。首先，它通过clazz.getDeclaredMethods()获取类中声明的所有方法，然后使用filter方法过滤出带有指定注解的方法。接着，使用map方法对过滤出的方法进行检查，如果方法有参数，那么就抛出一个BeanDefinitionException异常。最后，将过滤和检查后的方法收集到一个列表中。
     *
     * 如果列表为空，即没有找到带有指定注解的方法，那么就返回null。如果列表中只有一个方法，那么就返回这个方法。如果列表中有多个方法，那么就抛出一个BeanDefinitionException异常，因为这表示在类中找到了多个带有指定注解的方法，这可能是一个错误。
     */
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        List<Method> methods = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annoClass)).map(m -> {
            if (m.getParameterCount() != 0) {
                throw new BeanDefinitionException(
                        String.format("Method '%s' with @%s must not have argument: %s", m.getName(), annoClass.getSimpleName(), clazz.getName()));
            }
            return m;
        }).toList();
        if (methods.isEmpty()) {
            return null;
        }
        if (methods.size() == 1) {
            return methods.get(0);
        }
        throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s", annoClass.getSimpleName(), clazz.getName()));
    }

    public static <A extends Annotation> A getAnnotation(Annotation[] annos, Class<A> annoClass) {
        for (Annotation anno : annos) {
            if (annoClass.isInstance(anno)) {
                return (A)anno;
            }
        }
        return null;
    }
}
