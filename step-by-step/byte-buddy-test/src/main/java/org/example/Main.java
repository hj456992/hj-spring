package org.example;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;

public class Main {
    public static void main(String[] args) {
        // 这个动态创建的类型与直接扩展 Object 并且没有实现任何方法、属性和构造函数的类型是等价的。
        DynamicType.Unloaded<?> dynamicType1 = new ByteBuddy()
                .subclass(Object.class)
                .name("example.type")
                .make();

        // 自定义命名
        DynamicType.Unloaded<?> dynamicType2 = new ByteBuddy()
                .with(new NamingStrategy.AbstractBase() {
                    @Override
                    protected String name(TypeDescription superClass) {
                        return "I love ByteBuddy " + superClass.getSimpleName();
                    }
                })
                .subclass(Object.class)
                .make();

        DynamicType.Unloaded<?> dynamicType3 = new ByteBuddy()
                .with(new NamingStrategy.SuffixingRandom("love"))
                .subclass(Object.class)
                .make();
    }
}