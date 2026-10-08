package com.linchuan.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体字段对应的数据库列名。
 * <p>
 * 不标注时，按「驼峰 → 下划线」规则推导，例如 {@code enrollDate → enroll_date}。
 *
 * @author 林川
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /** 数据库列名。 */
    String value() default "";
}
