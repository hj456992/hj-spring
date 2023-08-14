package org.example.web.utils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.example.context.ApplicationContext;
import org.example.context.ApplicationContextUtils;
import org.example.io.PropertyResolver;
import org.example.utils.YamlUtils;
import org.example.web.DispatchServlet;

import java.util.Map;
import java.util.Properties;

public class WebUtils {

    public static final String CONFIG_YAML_PATH = "/application.yml";

    public static void registerDispatchServlet(ServletContext servletContext, PropertyResolver propertyResolver) {
        DispatchServlet dispatchServlet = new DispatchServlet(ApplicationContextUtils.getApplicationContext(), propertyResolver);

        String servletName = "dispatchServlet";
        // 映射路径
        String servletMapping = "/";
        // 启动顺序
        int loadOnStartUp = 0;

        ServletRegistration.Dynamic seg = servletContext.addServlet(servletName, dispatchServlet);
        seg.setLoadOnStartup(loadOnStartUp);
        seg.addMapping(servletMapping);
    }

    public static PropertyResolver createPropertyResolver() {
        final Properties prop = new Properties();
        // 尝试从application.yml中读取文件
        Map<String, Object> ymlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_YAML_PATH);
        for (String key : ymlMap.keySet()) {
            Object val = ymlMap.get(key);
            if (val instanceof String) {
                prop.put(key, val);
            }
        }
        return new PropertyResolver(prop);
    }
}
