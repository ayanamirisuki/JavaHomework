# jdbc-orm-demo —— 作业1：手写迷你 ORM（JDBCTool）

用 **Java 反射 + JDBC** 实现一个不依赖任何持久层框架的迷你 ORM 工具类 `JDBCTool`，
并用它完成 `DateTest` 库中 `student`、`college` 两张表的增删改查。

## 一、作业目标对照

| 作业要求 | 本工程对应实现 |
|---|---|
| 理解 ORM 基本原理 | `JDBCTool` 用「对象 ↔ 表」的元数据映射代替手写 SQL，见 `EntityMeta` |
| 反射在对象创建与属性赋值中的应用 | `newInstance()` 创建对象、`Field.set()` 赋值、`Field.get()` 取值 |
| JDBC 增删改查 | `PreparedStatement` + `ResultSet`，全程参数化查询 |
| 方法1 `resultSetToList(rs, clazz)` | `JDBCTool#resultSetToList`：ResultSet → `List<T>` |
| 方法2 `save(obj, connection)` | `JDBCTool#save`：反射拼 INSERT，自增主键回填 |
| 方法3 `update(obj, connection)` | `JDBCTool#update`：按主键拼 UPDATE |
| 方法4 `delete(obj, connection)` | `JDBCTool#delete`：按主键拼 DELETE |
| 方法5 `getOneById(id, clazz, connection)` | `JDBCTool#getOneById`：按主键查单条，入参 String 自动转主键类型 |
| 其他辅助方法 | `findAll` / `count` / `getTableName` / `camelToUnderline` / `underlineToCamel` / `convert` / `toSqlValue` |
| 数据库 `DateTest` 两张表 | `src/main/resources/sql/schema.sql` |
| Maven 构建 | `pom.xml` |
| Git 版本管理 | 仓库根目录即本工程目录 |

## 二、目录结构

```
jdbc-orm-demo
├── pom.xml                                   Maven 配置（mysql-connector-j 8.4.0）
├── README.md
├── .gitignore
├── doc/
│   └── screenshots/                          运行效果截图
└── src/main
    ├── java/com/linchuan/orm
    │   ├── Main.java                         演示程序：两张表的完整 CRUD
    │   ├── annotation
    │   │   ├── Table.java                    @Table  标注表名
    │   │   ├── Column.java                   @Column 标注列名
    │   │   └── Id.java                       @Id     标注主键（含是否自增）
    │   ├── entity
    │   │   ├── Student.java                  student 表实体
    │   │   └── College.java                  college 表实体
    │   └── util
    │       ├── JDBCTool.java                 ★ 核心：迷你 ORM 工具类
    │       └── DBUtil.java                   连接获取与资源释放
    └── resources
        ├── jdbc.properties                   数据库连接配置
        └── sql/schema.sql                    建库建表脚本
```

## 三、环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 及以上（本工程用 JDK 21 编译、`release=17`） |
| MySQL | 8.x（本机为 8.4.7） |
| Maven | 3.6+（本机用 3.9.16） |

## 四、运行步骤

```bash
# 1. 建库建表
mysql -uroot -p123456 < src/main/resources/sql/schema.sql

# 2. 按需修改 src/main/resources/jdbc.properties 中的用户名/密码

# 3. 编译打包（生成可执行 fat-jar：target/jdbc-orm-demo.jar）
mvn clean package

# 4. 运行演示
mvn exec:java
# 或者
java -jar target/jdbc-orm-demo.jar
```

## 五、JDBCTool 用法示例

```java
// 查询
Student s = JDBCTool.getOneById("1", Student.class, connection);
List<Student> all = JDBCTool.findAll(Student.class, connection);

// 新增（自增主键会自动回填到对象）
Student stu = new Student("张伟", "计算机科学与技术", 19,
        LocalDate.of(2024, 9, 1), false, new BigDecimal("5800.00"));
JDBCTool.save(stu, connection);
System.out.println(stu.getId());          // 数据库生成的学号

// 修改
stu.setTuition(new BigDecimal("6200.00"));
JDBCTool.update(stu, connection);

// 删除
JDBCTool.delete(stu, connection);

// 用原生 ResultSet 映射
try (PreparedStatement ps = connection.prepareStatement("select * from student where age > ?")) {
    ps.setInt(1, 18);
    try (ResultSet rs = ps.executeQuery()) {
        List<Student> list = JDBCTool.resultSetToList(rs, Student.class);
    }
}
```

## 六、映射规则

1. 表名优先取类上的 `@Table`，未标注时按类名驼峰转下划线（`StudentInfo → student_info`）。
2. 列名优先取字段上的 `@Column`，未标注时按字段名驼峰转下划线（`enrollDate → enroll_date`）。
3. 结果为 `ResultSet` 的列 → 实体字段匹配顺序：先列名精确匹配（忽略大小写），
   再按「列名下划线转驼峰 = 属性名」兜底；结果集里多出来的列自动忽略。
4. 类型转换由 `JDBCTool#convert` 统一处理，已覆盖：
   `int/long/short/byte/double/float` 及其包装类、`String`、`BigDecimal`、`BigInteger`、
   `boolean/Boolean`、`char`、`LocalDate`、`LocalDateTime`、`LocalTime`、
   `java.util.Date`、枚举。
5. 主键必须用 `@Id` 标注，`update` / `delete` / `getOneById` 均以它作为定位条件。

## 七、安全性说明

所有 SQL 的值部分一律使用 `PreparedStatement` 占位符传参，不存在字符串拼接注入风险；
表名、列名来自注解（编译期常量），并在拼接时统一加反引号，避免撞上 MySQL 关键字。
