-- ============================================================
--  作业1 数据库脚本：数据库 DateTest
--  表1 student：Id、name、major、age、入学时间、是否毕业、学费
--  表2 college：id、name、code
--  字符集统一 utf8mb4，引擎 InnoDB
-- ============================================================

CREATE DATABASE IF NOT EXISTS DateTest
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE DateTest;

-- ----------------------------
-- 学生表
-- 注意：列名故意使用下划线风格（enroll_date），
--      用来演示 ORM 中「列名 → 驼峰属性名」的映射。
-- ----------------------------
DROP TABLE IF EXISTS student;
CREATE TABLE student
(
    id          INT PRIMARY KEY AUTO_INCREMENT COMMENT '学号（主键，自增）',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    major       VARCHAR(50) COMMENT '专业',
    age         INT COMMENT '年龄',
    enroll_date DATE COMMENT '入学时间',
    graduated   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否毕业：0-未毕业 1-已毕业',
    tuition     DECIMAL(10, 2) COMMENT '学费'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '学生表';

-- ----------------------------
-- 学院表
-- ----------------------------
DROP TABLE IF EXISTS college;
CREATE TABLE college
(
    id   INT PRIMARY KEY AUTO_INCREMENT COMMENT '学院编号（主键，自增）',
    name VARCHAR(50) NOT NULL COMMENT '学院名称',
    code VARCHAR(20) COMMENT '学院代码'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '学院表';
