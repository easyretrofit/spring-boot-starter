package io.github.liuziyuan.retrofit.core;

public interface AppContext {

    <T> T getBean(Class<T> clazz);

    Object getBean(String var1);
}
