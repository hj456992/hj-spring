package org.example.annotation;

import java.lang.annotation.*;

/**
 * 该注解的作用为，在指定包下扫描所有class
 */
@Retention(RetentionPolicy.RUNTIME) // 该注解可保留到运行时，可以通过反射机制读取注解的信息
@Target(ElementType.TYPE) // 该注解可用于类、接口（包括注解类型）或者枚举声明
@Documented
public @interface ComponentScan {

    /**
     * 未指定包名的情况下，默认是当前注解所在类的包
     * Package names to scan. Default to current package.
     */
    String[] value() default {};
}
