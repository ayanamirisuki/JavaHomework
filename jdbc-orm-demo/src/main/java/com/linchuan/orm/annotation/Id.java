package com.linchuan.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注主键字段。
 * <p>
 * update / delete / getOneById 三个方法都以该字段作为定位条件，
 * 因此每个交给 JDBCTool 持久化的实体必须有且只有一个 {@code @Id} 字段。
 *
 * @author 林川
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {

    /**
     * 主键是否由数据库自增生成。
     * <p>
     * 为 {@code true} 时：save() 会把值为 null（或 0）的主键排除在 INSERT 之外，
     * 插入成功后再用 JDBC 返回的生成键回填到对象里。
     */
    boolean autoIncrement() default true;
}
