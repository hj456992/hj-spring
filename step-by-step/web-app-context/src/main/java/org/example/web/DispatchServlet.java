package org.example.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.context.ApplicationContext;
import org.example.io.PropertyResolver;

import java.io.IOException;
import java.io.PrintWriter;

public class DispatchServlet extends HttpServlet {

    ApplicationContext applicationContext;

    public DispatchServlet(ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 从resp（响应）对象获取一个PrintWriter对象。PrintWriter类用于向目标发送字符文本，在这种情况下是客户端的Web浏览器。
        PrintWriter pw = resp.getWriter();
        // 使用PrintWriter将字符串"<h1>Hello World</h1>"写入响应。这是一个HTML代码片段，在Web浏览器中显示时将呈现为带有文本"Hello World"的标题（h1）。
        pw.write("<h1>Hello World</h1>");
        // 刷新PrintWriter的缓冲区，确保任何缓冲的字符立即发送到客户端。这通常是必要的，以确保内容能够及时发送。
        pw.flush();
    }

    @Override
    public void destroy() {
        applicationContext.close();
    }
}
