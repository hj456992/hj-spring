package org.example.exception;

public class BeanDefinitionException extends NestedRuntimeException{

    public BeanDefinitionException(){}

    public BeanDefinitionException(String message) {
        super(message);
    }

    public BeanDefinitionException(Throwable cause) {
        super(cause);
    }

    public BeanDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
