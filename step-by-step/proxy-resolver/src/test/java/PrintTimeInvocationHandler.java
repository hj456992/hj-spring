import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class PrintTimeInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object bean, Method method, Object[] args) throws Throwable {
        if (method.getAnnotation(PrintTime.class) != null) {
            System.out.println(System.currentTimeMillis());
            Thread.sleep(1000L);
            Object obj = method.invoke(bean, args);
            System.out.println(System.currentTimeMillis());
            return obj + " AOP";
        }
        return method.invoke(bean, args);
    }
}
