package com.linchuan.orm.entity;

import com.linchuan.orm.annotation.Column;
import com.linchuan.orm.annotation.Id;
import com.linchuan.orm.annotation.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * 学院实体，对应 DateTest 库中的 college 表。
 *
 * <pre>
 * college(id, name, code)
 * </pre>
 *
 * @author 林川
 */
@Table("college")
public class College implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学院编号，主键、数据库自增。 */
    @Id
    @Column("id")
    private Integer id;

    /** 学院名称。 */
    @Column("name")
    private String name;

    /** 学院代码。 */
    @Column("code")
    private String code;

    public College() {
    }

    public College(String name, String code) {
        this.name = name;
        this.code = code;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof College)) {
            return false;
        }
        College other = (College) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("College{id=%s, name='%s', code='%s'}", id, name, code);
    }
}
