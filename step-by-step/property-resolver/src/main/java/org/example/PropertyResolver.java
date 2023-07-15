package org.example;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Spring的注入分为@Autowired和@Value两种。对于@Autowired，涉及到Bean的依赖，而对于@Value，则仅仅是将对应的配置注入，不涉及Bean的依赖，相对比较简单。
 * 为了注入配置，我们用PropertyResolver保存所有配置项，对外提供查询功能。
 *
 * 本节我们来实现PropertyResolver，它支持3种查询方式：
 * 1、按配置的key查询，例如：getProperty("app.title");
 * 2、以${abc.xyz}形式的查询，例如，getProperty("${app.title}")，常用于@Value("${app.title}")注入；
 * 3、带默认值的，以${abc.xyz:defaultValue}形式的查询，例如，getProperty("${app.title:Summer}")，常用于@Value("${app.title:Summer}")注入。
 */
public class PropertyResolver {

    Map<String, String> properties = new HashMap<>();
    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    public PropertyResolver(Properties props) {
        // 存入环境变量
        this.properties.putAll(System.getenv());
        // 存入properties
        Set<String> names = props.stringPropertyNames();
        for (String name : names) {
            properties.put(name, props.getProperty(name));
        }
        // 存入类型转换映射关系
        converters.put(String.class, s -> s);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Boolean.class, Boolean::valueOf);

        converters.put(byte.class, Byte::parseByte);
        converters.put(Byte.class, Byte::valueOf);

        converters.put(short.class, Short::parseShort);
        converters.put(Short.class, Short::valueOf);

        converters.put(int.class, Integer::parseInt);
        converters.put(Integer.class, Integer::valueOf);

        converters.put(long.class, Long::parseLong);
        converters.put(Long.class, Long::valueOf);

        converters.put(float.class, Float::parseFloat);
        converters.put(Float.class, Float::valueOf);

        converters.put(double.class, Double::parseDouble);
        converters.put(Double.class, Double::valueOf);

        converters.put(LocalDate.class, LocalDate::parse);
        converters.put(LocalTime.class, LocalTime::parse);
        converters.put(LocalDateTime.class, LocalDateTime::parse);
        converters.put(ZonedDateTime.class, ZonedDateTime::parse);
        converters.put(Duration.class, Duration::parse);
        converters.put(ZoneId.class, ZoneId::of);
    }

    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        return value == null ? null : convert(value, targetType);
    }

    public <T> T convert(String value, Class<?> clazz) {
        Function<String, Object> fn = this.converters.get(clazz);
        if (fn == null) {
            throw new UnsupportedOperationException("Unsupported value type" + clazz.getName());
        }
        return (T)this.converters.get(clazz).apply(value);
    }


    public String getProperty(String key) {
        PropertyExpr expr = parseProperty(key);
        if (expr != null) {
            if (expr.defaultValue() != null) {
                return getProperty(key, expr.defaultValue());
            } else {
                return getProperty(key);
            }
        }
        String value = this.properties.get(key);
        return value;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? null: defaultValue;
    }

    /**
     * 解析类似 ${abc.xyz:defaultValue} 格式的key
     */
    PropertyExpr parseProperty(String key) {
        if (key.startsWith("${") && key.endsWith("}")) {
            int n = key.indexOf(":");
            if (n == -1) {
                // 没有defaultValue
                return new PropertyExpr(key.substring(2, key.length() - 1), null);
            } else {
                // 有defaultValue
                return new PropertyExpr(key.substring(2, n), key.substring(n + 1, key.length() - 1));
            }
        }
        return null;
    }
}

record PropertyExpr(String key, String defaultValue) {

}
