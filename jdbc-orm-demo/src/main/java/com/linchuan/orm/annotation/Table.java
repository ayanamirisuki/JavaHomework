package com.linchuan.orm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注实体类对应的数据库表名。
 * <p>
 * 不标注时，JDBCTool 会把类名按「驼峰 → 下划线小写」规则推导为表名，
 * 例如 {@code StudentInfo → student_info}。
 *
 * @author 林川
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /** 数据库表名。 */
    String value() default "";
}
