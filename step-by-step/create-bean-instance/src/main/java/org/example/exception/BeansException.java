package org.example.exception;

import org.example.context.BeanDefinition;

public class BeansException extends NestedRuntimeException{

    public BeansException(){}

    public BeansException(String message) {
        super(message);
    }

    public BeansException(Throwable cause) {
        super(cause);
    }

    public BeansException(String message, Throwable cause) {
        super(message, cause);
    }
}
