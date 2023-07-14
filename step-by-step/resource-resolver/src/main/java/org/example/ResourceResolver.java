package org.example;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ResourceResolver {
    String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * Java的ClassLoader机制可以在指定的Classpath中根据类名加载指定的Class，
     * 但遗憾的是，给出一个包名，例如，org.example，它并不能获取到该包下的所有Class，也不能获取子包。
     * 要在Classpath中扫描指定包名下的所有Class，包括子包，实际上是在Classpath中搜索所有文件，找出文件名匹配的.class文件。
     * 例如，Classpath中搜索的文件org/example/Hello.class就符合包名org.example，
     * 我们需要根据文件路径把它变为org.example.Hello，就相当于获得了类名。因此，搜索Class变成了搜索文件。
     *
     * @param mapper
     * @return
     * @param <R>
     */
    public <R> List<R> scan(Function<Resource, R> mapper) {
        String basePackagePath = this.basePackage.replace('.', '/');// 将包名更改为文件路径
        String path = basePackagePath;
        try {
            List<R> collector = new ArrayList<>();
            scan0(basePackagePath, path, collector, mapper);
            return collector;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 在ClassPath中扫描文件的代码是固定模式，可以在网上搜索获得
     * 参考 https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection#58773038
     *
     * @param basePackagePath
     * @param path
     * @throws IOException
     * @throws URISyntaxException
     */
    <R> void scan0(
            String basePackagePath,
            String path,
            List<R> collector,
            Function<Resource, R> mapper)
            throws IOException, URISyntaxException {
        // 通过classloader获得url列表
        Enumeration<URL> en = getContextClassLoader().getResources(path);
        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            URI uri = url.toURI();
            String uriStr = removeTrailingSlash(uriToString(uri)); // 此时uriStr为
            String uriBaseStr = uriStr.substring(0, uriStr.length() - basePackagePath.length());
            if (uriBaseStr.startsWith("file:")) {
                // 在目录中搜索
                uriBaseStr = uriBaseStr.substring(5);
            }
            if (uriStr.startsWith("jar:")) {
                // 在jar包中搜索
                scanFile(true, uriBaseStr, jarUriToPath(basePackagePath, uri), collector, mapper);
            } else {
                scanFile(false, uriBaseStr, Paths.get(uri), collector, mapper);
            }
        }
    }

    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }

    /**
     * 扫描文件并将结果收集到一个列表中
     * 例：
     * 1、当扫描的是jar包时，以sub路径下的AnnoScan为例，
     *    isJar = true,
     *    base = /Users/houjue/myspring/step-by-step/target/test-classes/,
     *    root = /Users/houjue/myspring/step-by-step/target/test-classes/jakarta/annotation,
     * 2、当扫描的是文件时，包名为org.example.scan，则
     *    isJar = false,
     *    base = /Users/houjue/myspring/step-by-step/target/test-classes/,
     *    root = /Users/houjue/myspring/step-by-step/target/test-classes/org/example/scan,
     *
     * @param isJar 一个布尔值，用于指示扫描的是否是一个JAR文件。
     * @param base 一个字符串，表示基础路径。
     * @param root 一个Path对象，表示要扫描的根路径。
     * @param collector 一个列表，用于收集扫描结果。这个列表的元素类型是R，这是一个泛型类型，表示可以是任何类型。
     * @param mapper 一个函数，用于将Resource对象映射为R类型的对象。这个函数将被应用到每一个找到的资源上。
     * @param <R> <R>是一个泛型标识符，表示这个方法可以处理任何类型的对象。在这个方法中，R被用作collector列表的元素类型，以及mapper函数的返回类型。
     * @throws IOException
     */
    <R> void scanFile(
            boolean isJar,
            String base,
            Path root,
            List<R> collector,
            Function<Resource, R> mapper)
            throws IOException {
        String baseDir = removeTrailingSlash(base);
        Files.walk(root).filter(Files::isRegularFile).forEach(file -> {
            Resource res = null;
            if (isJar) {
                 res = new Resource(baseDir, removeTrailingSlash(file.toString()));
            } else {
                String path = file.toString();
                String name = removeLeadingSlash(path.substring(baseDir.length()));
                res = new Resource("file" + path, name);
            }

            R r = mapper.apply(res);
            if (r != null) {
                collector.add(r);
            }
        });
    }

    /**
     * 去除字符串结尾处的/或者\
     */
    String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 去除字符串开头处的/或者\
     */
    String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }

    /**
     * 使用utf-8编码格式化uri
     */
    String uriToString(URI uri) {
        // 使用utf-8编码格式化uri
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    /**
     * ClassLoader首先从Thread.getContextClassLoader()获取，
     * 如果获取不到，再从当前Class获取，
     * 因为Web应用的ClassLoader不是JVM提供的基于Classpath的ClassLoader，而是Servlet容器提供的ClassLoader，
     * 它不在默认的Classpath搜索，而是在/WEB-INF/classes目录和/WEB-INF/lib的所有jar包搜索，
     * 从Thread.getContextClassLoader()可以获取到Servlet容器专属的ClassLoader；
     */
    ClassLoader getContextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        return cl;
    }
}
