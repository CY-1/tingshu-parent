package com.atguigu.tingshu.common.login;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GuiGuLogin {

    /**
     * 是否必须要登录
     * @return
     */
    boolean required() default true;
}