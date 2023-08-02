import org.example.ProxyResolver;

public class ProxyTest {

    public static void main(String[] args) {
        TestBean bean = new TestBean();
        TestBean proxyBean = new ProxyResolver().createProxy(bean, new PrintTimeInvocationHandler());

        System.out.println(bean.equals(proxyBean));// false
        System.out.println(bean.hello());
        System.out.println(proxyBean.hello());

        System.out.println(bean.bye());
        System.out.println(proxyBean.bye());
    }
}
