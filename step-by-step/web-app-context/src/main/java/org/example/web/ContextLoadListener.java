package org.example.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.example.context.AnnotationConfigApplicationContext;
import org.example.context.ApplicationContext;
import org.example.io.PropertyResolver;
import org.example.web.utils.WebUtils;

public class ContextLoadListener implements ServletContextListener {

    /**
     * 在Web应用程序初始化时执行，
     * 实现功能：
     *  1、初始化了字符编码
     *  2、创建应用程序上下文
     *  3、注册DispatcherServlet
     *  4、将应用程序上下文存储在ServletContext中，以供应用程序其他部分使用。
     * 这是典型的Java Web应用程序初始化代码，用于配置和启动应用程序的一些关键组件。
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 从sce中获取ServletContext，它代表web应用程序的上下文
        ServletContext sc = sce.getServletContext();
        // 创建一个属性解析器
        PropertyResolver pr = WebUtils.createPropertyResolver();
        // 设置请求和响应的字符编码，如果属性解析器中有，则从属性解析器中获取，如果没有，则默认设置UTF-8
        String encoding = pr.getProperty("${web.character.encoding:UTF-8}");
        sc.setRequestCharacterEncoding(encoding);
        sc.setResponseCharacterEncoding(encoding);
        // 创建应用程序上下文，配置路径从web.xml中的configuration配置项中获取
        var applicationContext = createApplicationContext(sc.getInitParameter("configuration"), pr);
        // 注册DispatchServlet
        WebUtils.registerDispatchServlet(sc, pr);
        // 将刚刚创建的应用程序上下文存储为applicationContext的属性，以便在整个应用程序中共享。
        sc.setAttribute("applicationContext", applicationContext);

    }

    public void contextDestroyed(ServletContextEvent sce) {
        if (sce.getServletContext().getAttribute("applicationContext") instanceof ApplicationContext applicationContext) {
            applicationContext.close();
        }
    }

    private ApplicationContext createApplicationContext(String configClassName, PropertyResolver propertyResolver) {
        try {
            return new AnnotationConfigApplicationContext(Class.forName(configClassName), propertyResolver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("ApplicationContext配置类不存在");
        }
    }
}
