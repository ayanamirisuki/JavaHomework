# JavaHomework

Java 课程作业仓库。

| 作业 | 内容 | 目录 |
|---|---|---|
| 作业1 | 手写迷你 ORM 工具类 `JDBCTool`（Java 反射 + JDBC），完成 DateTest 库两张表的增删改查 | [`jdbc-orm-demo/`](./jdbc-orm-demo) |

## 作业1 快速开始

```bash
cd jdbc-orm-demo

# 1. 建库建表（本机 MySQL，账号密码见 src/main/resources/jdbc.properties）
mysql -uroot -p < src/main/resources/sql/schema.sql

# 2. 编译打包（需 JDK 17+ 与 Maven 3.6+）
mvn clean package

# 3. 运行
java -jar target/jdbc-orm-demo.jar
```

运行效果见 [`jdbc-orm-demo/doc/screenshots/`](./jdbc-orm-demo/doc/screenshots/)。

详细设计说明见 [`jdbc-orm-demo/README.md`](./jdbc-orm-demo/README.md)。
