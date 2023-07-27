package org.example.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) // 该注解可保留到运行时，可以通过反射机制读取注解的信息
@Target(ElementType.TYPE) // 该注解可用于类、接口（包括注解类型）或者枚举声明
@Documented
public @interface Component {

    /**
     * Bean name. Default to simple class name with first-letter-lowercase.
     */
    String value() default "";
}
