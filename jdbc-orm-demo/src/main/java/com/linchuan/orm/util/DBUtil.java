package com.linchuan.orm.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据库连接工具类：负责读取 {@code jdbc.properties} 并统一获取 / 释放连接。
 * <p>
 * 之所以把它和 {@link JDBCTool} 分开，是为了让 JDBCTool 只关心「对象 ←→ 表」的映射，
 * 连接从外部传入，便于以后换连接池或换数据库。
 *
 * @author 林川
 */
public final class DBUtil {

    private static final Properties PROPS = new Properties();

    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        try (InputStream in = DBUtil.class.getClassLoader().getResourceAsStream("jdbc.properties")) {
            if (in == null) {
                throw new IllegalStateException("classpath 根目录下未找到 jdbc.properties");
            }
            PROPS.load(new InputStreamReader(in, StandardCharsets.UTF_8));

            URL = PROPS.getProperty("jdbc.url");
            USERNAME = PROPS.getProperty("jdbc.username");
            PASSWORD = PROPS.getProperty("jdbc.password");

            // 显式加载驱动，兼容老版本写法（JDBC 4.0 之后其实已可自动装载）
            Class.forName(PROPS.getProperty("jdbc.driver"));
        } catch (IOException | ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DBUtil() {
    }

    /**
     * 获取一个数据库连接。
     *
     * @return 新的 {@link Connection}
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    /** 静默关闭一组 JDBC 资源，忽略关闭过程中的异常。 */
    public static void close(AutoCloseable... resources) {
        if (resources == null) {
            return;
        }
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                    // 关闭失败不影响主流程
                }
            }
        }
    }

    /** 当前使用的 JDBC URL，便于日志输出与排查。 */
    public static String getUrl() {
        return URL;
    }

    /** 返回配置项的只读快照。 */
    public static Properties getConfig() {
        Properties copy = new Properties();
        copy.putAll(PROPS);
        return copy;
    }
}
