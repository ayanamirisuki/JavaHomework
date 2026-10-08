package com.linchuan.orm.entity;

import com.linchuan.orm.annotation.Column;
import com.linchuan.orm.annotation.Id;
import com.linchuan.orm.annotation.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 学生实体，对应 DateTest 库中的 student 表。
 *
 * <pre>
 * student(id, name, major, age, enroll_date, graduated, tuition)
 * </pre>
 *
 * <p>字段类型刻意选得比较「刁钻」，用来验证 JDBCTool 的类型转换能力：
 * <ul>
 *     <li>{@code enroll_date DATE} → {@link LocalDate}</li>
 *     <li>{@code tuition DECIMAL(10,2)} → {@link BigDecimal}</li>
 *     <li>{@code graduated TINYINT(1)} → {@link Boolean}</li>
 * </ul>
 *
 * @author 林川
 */
@Table("student")
public class Student implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学号，主键、数据库自增。 */
    @Id
    @Column("id")
    private Integer id;

    /** 姓名。 */
    @Column("name")
    private String name;

    /** 专业。 */
    @Column("major")
    private String major;

    /** 年龄。 */
    @Column("age")
    private Integer age;

    /**
     * 入学时间。
     * 数据库列名是下划线的 enroll_date，属性名是驼峰的 enrollDate，
     * 这正是 ORM 框架必须解决的「命名风格差异」问题。
     */
    @Column("enroll_date")
    private LocalDate enrollDate;

    /** 是否毕业：false-未毕业，true-已毕业。 */
    @Column("graduated")
    private Boolean graduated;

    /** 学费。 */
    @Column("tuition")
    private BigDecimal tuition;

    public Student() {
    }

    public Student(String name, String major, Integer age, LocalDate enrollDate,
                   Boolean graduated, BigDecimal tuition) {
        this.name = name;
        this.major = major;
        this.age = age;
        this.enrollDate = enrollDate;
        this.graduated = graduated;
        this.tuition = tuition;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDate getEnrollDate() {
        return enrollDate;
    }

    public void setEnrollDate(LocalDate enrollDate) {
        this.enrollDate = enrollDate;
    }

    public Boolean getGraduated() {
        return graduated;
    }

    public void setGraduated(Boolean graduated) {
        this.graduated = graduated;
    }

    public BigDecimal getTuition() {
        return tuition;
    }

    public void setTuition(BigDecimal tuition) {
        this.tuition = tuition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Student)) {
            return false;
        }
        Student other = (Student) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(
                "Student{id=%s, name='%s', major='%s', age=%s, enrollDate=%s, graduated=%s, tuition=%s}",
                id, name, major, age, enrollDate,
                graduated == null ? "null" : (graduated ? "已毕业" : "未毕业"),
                tuition == null ? "null" : tuition.toPlainString());
    }
}
