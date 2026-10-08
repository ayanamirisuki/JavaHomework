package com.linchuan.orm;

import com.linchuan.orm.entity.College;
import com.linchuan.orm.entity.Student;
import com.linchuan.orm.util.DBUtil;
import com.linchuan.orm.util.JDBCTool;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * 作业1 演示程序：用 JDBCTool 完成 DateTest 库中 student、college 两张表的增删改查。
 * <p>
 * 全程不写一句 SQL，只调用 JDBCTool 的五个方法，验证「反射 + JDBC」实现的 ORM 效果。
 *
 * @author 林川
 */
public class Main {

    public static void main(String[] args) {

        banner("作业1  JDBCTool 演示（Java 反射 + JDBC 实现迷你 ORM）");

        try (Connection connection = DBUtil.getConnection()) {

            section("0. 获取数据库连接");
            System.out.println("     数据库产品：" + connection.getMetaData().getDatabaseProductName()
                    + " " + connection.getMetaData().getDatabaseProductVersion());

            resetTables(connection);

            // ---------- 一、save 新增 ----------
            section("1. save(T obj, Connection) —— 新增");

            College cs = new College("计算机学院", "CS");
            College se = new College("软件工程学院", "SE");
            College ee = new College("电子信息学院", "EE");
            int rows = 0;
            rows += JDBCTool.save(cs, connection);
            rows += JDBCTool.save(se, connection);
            rows += JDBCTool.save(ee, connection);
            System.out.println("     college 表插入 3 条，受影响行数合计 = " + rows);
            System.out.println("     ↑ 主键由数据库自增生成后已回填到对象上：");
            System.out.println("       " + cs);
            System.out.println("       " + se);
            System.out.println("       " + ee);

            Student s1 = new Student("张伟", "计算机科学与技术", 19,
                    LocalDate.of(2024, 9, 1), false, new BigDecimal("5800.00"));
            Student s2 = new Student("李娜", "软件工程", 20,
                    LocalDate.of(2023, 9, 1), false, new BigDecimal("6200.00"));
            Student s3 = new Student("王强", "电子信息工程", 22,
                    LocalDate.of(2022, 9, 1), true, new BigDecimal("5500.00"));
            Student s4 = new Student("赵敏", "网络工程", 21,
                    LocalDate.of(2022, 9, 1), true, new BigDecimal("6000.00"));

            System.out.println();
            System.out.println("     student 表插入 4 条：");
            for (Student s : new Student[]{s1, s2, s3, s4}) {
                System.out.println("       受影响行数 = " + JDBCTool.save(s, connection) + " → " + s);
            }

            // ---------- 二、getOneById 按主键查询 ----------
            section("2. getOneById(String id, Class<T> clazz, Connection) —— 按主键查询");

            Student found = JDBCTool.getOneById(String.valueOf(s1.getId()), Student.class, connection);
            System.out.println("     getOneById(\"" + s1.getId() + "\", Student.class) → " + found);

            College foundCollege = JDBCTool.getOneById(String.valueOf(se.getId()), College.class, connection);
            System.out.println("     getOneById(\"" + se.getId() + "\", College.class) → " + foundCollege);

            Object missing = JDBCTool.getOneById("999", Student.class, connection);
            System.out.println("     getOneById(\"999\", Student.class) → " + missing + "  （不存在时返回 null）");

            // ---------- 三、update 修改 ----------
            section("3. update(T obj, Connection) —— 修改");

            System.out.println("     修改前：" + JDBCTool.getOneById(String.valueOf(s1.getId()), Student.class, connection));
            s1.setTuition(new BigDecimal("6200.00"));
            s1.setGraduated(true);
            System.out.println("     受影响行数 = " + JDBCTool.update(s1, connection));
            System.out.println("     修改后：" + JDBCTool.getOneById(String.valueOf(s1.getId()), Student.class, connection));

            se.setCode("SOFT");
            System.out.println("     受影响行数 = " + JDBCTool.update(se, connection) + " → " + se);

            // ---------- 四、delete 删除 ----------
            section("4. delete(T obj, Connection) —— 删除");

            System.out.println("     受影响行数 = " + JDBCTool.delete(s4, connection) + " → 已删除 " + s4);
            System.out.println("     再查一次 → " + JDBCTool.getOneById(String.valueOf(s4.getId()), Student.class, connection));

            // ---------- 五、resultSetToList 查询列表 ----------
            section("5. resultSetToList(ResultSet rs, Class<T> clazz) —— 查询列表");

            List<Student> students = JDBCTool.findAll(Student.class, connection);
            System.out.println("     student 表共 " + students.size() + " 条记录（直接由 SQL 取回的对象列表）：");
            students.forEach(s -> System.out.println("       " + s));

            System.out.println();
            List<College> colleges = JDBCTool.findAll(College.class, connection);
            System.out.println("     college 表共 " + colleges.size() + " 条记录：");
            colleges.forEach(c -> System.out.println("       " + c));

            System.out.println("     汇总：student 表 COUNT(*) = " + JDBCTool.count(Student.class, connection)
                    + " ，college 表 COUNT(*) = " + JDBCTool.count(College.class, connection));

            banner("演示结束，连接已关闭");

        } catch (SQLException e) {
            System.err.println("数据库操作异常：" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** 演示前清空两张表，保证每次运行的输出与主键编号一致。 */
    private static void resetTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE student");
            statement.executeUpdate("TRUNCATE TABLE college");
        }
    }

    private static void banner(String title) {
        String line = "=".repeat(64);
        System.out.println(line);
        System.out.println("  " + title);
        System.out.println(line);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("【" + title + "】");
    }
}
