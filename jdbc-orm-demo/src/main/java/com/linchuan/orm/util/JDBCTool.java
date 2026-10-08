package com.linchuan.orm.util;

import com.linchuan.orm.annotation.Column;
import com.linchuan.orm.annotation.Id;
import com.linchuan.orm.annotation.Table;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手写的迷你 ORM 工具类。
 *
 * <p>核心思想：<b>对象 ↔ 表</b>的映射完全由「反射 + 注解」在运行时完成，
 * 调用方既不需要写 SQL，也不需要手工从 ResultSet 里逐列取值。
 *
 * <p>对外提供五个方法：
 * <ol>
 *     <li>{@link #resultSetToList(ResultSet, Class)}：ResultSet → List&lt;T&gt;（读）</li>
 *     <li>{@link #save(Object, Connection)}：INSERT（增）</li>
 *     <li>{@link #update(Object, Connection)}：UPDATE（改）</li>
 *     <li>{@link #delete(Object, Connection)}：DELETE（删）</li>
 *     <li>{@link #getOneById(String, Class, Connection)}：按主键查单条（查）</li>
 * </ol>
 *
 * <p>另外还提供 {@link #findAll(Class, Connection)}、{@link #resultSetToObject(ResultSet, Class)}
 * 以及一批命名转换 / 类型转换的辅助方法，供上面五个方法复用。
 *
 * <p><b>映射规则</b>（优先级由高到低）：
 * <ol>
 *     <li>类/字段上的 {@code @Table} / {@code @Column} 注解值</li>
 *     <li>无注解时，按「驼峰 ↔ 下划线」自动推导（{@code enrollDate ↔ enroll_date}）</li>
 *     <li>ResultSet 中多出来的列（例如 join 出来的字段）自动忽略，不报错</li>
 * </ol>
 *
 * @author 林川
 */
public final class JDBCTool {

    /** 实体类 → 元数据的缓存，避免每次操作都重新反射一遍。 */
    private static final Map<Class<?>, EntityMeta> META_CACHE = new ConcurrentHashMap<>();

    private JDBCTool() {
    }

    // ==================================================================
    //  一、五个核心方法
    // ==================================================================

    /**
     * 【方法1】把 ResultSet 映射成对象列表。
     *
     * <p>实现步骤：
     * <ol>
     *     <li>通过 {@code ResultSetMetaData} 取出所有列名；</li>
     *     <li>逐行遍历，每行用无参构造反射出一个 {@code T} 实例；</li>
     *     <li>按列名找到对应字段，把值转换类型后写入该字段。</li>
     * </ol>
     *
     * @param rs    已经执行完查询的 ResultSet（游标在第 0 行之前）
     * @param clazz 目标实体的 Class 对象
     * @param <T>   实体类型
     * @return 实体列表；结果集为空时返回空列表（而不是 null）
     * @throws SQLException 读取结果集失败
     */
    public static <T> List<T> resultSetToList(ResultSet rs, Class<T> clazz) throws SQLException {
        Objects.requireNonNull(rs, "ResultSet 不能为空");
        Objects.requireNonNull(clazz, "目标 Class 不能为空");

        EntityMeta meta = metaOf(clazz);
        List<T> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs, clazz, meta));
        }
        return list;
    }

    /**
     * 把 ResultSet 的<b>首行</b>映射成一个对象，没有数据时返回 {@code null}。
     * 属于 {@link #resultSetToList} 的辅助方法，被 {@link #getOneById} 复用。
     */
    public static <T> T resultSetToObject(ResultSet rs, Class<T> clazz) throws SQLException {
        Objects.requireNonNull(rs, "ResultSet 不能为空");
        Objects.requireNonNull(clazz, "目标 Class 不能为空");
        return rs.next() ? mapRow(rs, clazz, metaOf(clazz)) : null;
    }

    /**
     * 【方法2】保存（INSERT）一个对象。
     *
     * <p>会反射出所有字段拼成 {@code INSERT INTO 表 (列...) VALUES (?, ?...)}，
     * 用 PreparedStatement 预编译执行以防 SQL 注入；
     * 若主键是数据库自增且当前值为空，则该列不参与 INSERT，
     * 插入后用 JDBC 返回的生成键回填到对象上。
     *
     * @return 受影响的行数
     */
    public static <T> int save(T obj, Connection connection) throws SQLException {
        Objects.requireNonNull(obj, "待保存的对象不能为空");
        Objects.requireNonNull(connection, "Connection 不能为空");

        EntityMeta meta = metaOf(obj.getClass());

        List<FieldMeta> insertFields = new ArrayList<>();
        for (FieldMeta fm : meta.fields) {
            // 自增主键且还没有值 → 交给数据库生成
            if (fm.autoIncrement && isBlankValue(getValue(fm, obj))) {
                continue;
            }
            insertFields.add(fm);
        }
        if (insertFields.isEmpty()) {
            throw new IllegalArgumentException("实体 " + obj.getClass().getName() + " 没有可插入的字段");
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < insertFields.size(); i++) {
            if (i > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(quote(insertFields.get(i).columnName));
            placeholders.append('?');
        }

        String sql = "INSERT INTO " + quote(meta.tableName)
                + " (" + columns + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < insertFields.size(); i++) {
                ps.setObject(i + 1, toSqlValue(getValue(insertFields.get(i), obj)));
            }
            int rows = ps.executeUpdate();

            // 回填自增主键
            if (meta.pk != null && meta.pk.autoIncrement) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        setValue(meta.pk, obj, convert(keys.getObject(1), meta.pk.field.getType()));
                    }
                }
            }
            return rows;
        }
    }

    /**
     * 【方法3】更新（UPDATE）一个对象，以 {@code @Id} 字段为 WHERE 条件。
     *
     * @return 受影响的行数（0 表示没有匹配到记录）
     */
    public static <T> int update(T obj, Connection connection) throws SQLException {
        Objects.requireNonNull(obj, "待更新的对象不能为空");
        Objects.requireNonNull(connection, "Connection 不能为空");

        EntityMeta meta = metaOf(obj.getClass());
        FieldMeta pk = meta.requirePk();
        Object pkValue = getValue(pk, obj);
        if (isBlankValue(pkValue)) {
            throw new IllegalArgumentException("更新必须提供主键值：" + obj.getClass().getSimpleName()
                    + "." + pk.name() + " 当前为空");
        }

        List<FieldMeta> setFields = new ArrayList<>();
        for (FieldMeta fm : meta.fields) {
            if (!fm.isPk) {
                setFields.add(fm);
            }
        }
        if (setFields.isEmpty()) {
            throw new IllegalArgumentException("实体 " + obj.getClass().getName() + " 除主键外没有可更新的字段");
        }

        StringBuilder assignments = new StringBuilder();
        for (int i = 0; i < setFields.size(); i++) {
            if (i > 0) {
                assignments.append(", ");
            }
            assignments.append(quote(setFields.get(i).columnName)).append(" = ?");
        }

        String sql = "UPDATE " + quote(meta.tableName)
                + " SET " + assignments
                + " WHERE " + quote(pk.columnName) + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (FieldMeta fm : setFields) {
                ps.setObject(index++, toSqlValue(getValue(fm, obj)));
            }
            ps.setObject(index, toSqlValue(pkValue));
            return ps.executeUpdate();
        }
    }

    /**
     * 【方法4】删除（DELETE）一个对象，以 {@code @Id} 字段为 WHERE 条件。
     *
     * @return 受影响的行数（0 表示没有匹配到记录）
     */
    public static <T> int delete(T obj, Connection connection) throws SQLException {
        Objects.requireNonNull(obj, "待删除的对象不能为空");
        Objects.requireNonNull(connection, "Connection 不能为空");

        EntityMeta meta = metaOf(obj.getClass());
        FieldMeta pk = meta.requirePk();
        Object pkValue = getValue(pk, obj);
        if (isBlankValue(pkValue)) {
            throw new IllegalArgumentException("删除必须提供主键值：" + obj.getClass().getSimpleName()
                    + "." + pk.name() + " 当前为空");
        }

        String sql = "DELETE FROM " + quote(meta.tableName)
                + " WHERE " + quote(pk.columnName) + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, toSqlValue(pkValue));
            return ps.executeUpdate();
        }
    }

    /**
     * 【方法5】按主键查询单个对象。
     *
     * <p>入参 {@code id} 是字符串，内部会按主键字段的真实类型自动转换，
     * 因此 {@code "3"} 可以正确查到 {@code Integer id = 3} 的记录。
     *
     * @return 查到的对象；查不到返回 {@code null}
     */
    public static <T> T getOneById(String id, Class<T> clazz, Connection connection) throws SQLException {
        Objects.requireNonNull(clazz, "目标 Class 不能为空");
        Objects.requireNonNull(connection, "Connection 不能为空");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("主键 id 不能为空");
        }

        EntityMeta meta = metaOf(clazz);
        FieldMeta pk = meta.requirePk();

        String sql = "SELECT * FROM " + quote(meta.tableName)
                + " WHERE " + quote(pk.columnName) + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, toSqlValue(convert(id, pk.field.getType())));
            try (ResultSet rs = ps.executeQuery()) {
                return resultSetToObject(rs, clazz);
            }
        }
    }

    // ==================================================================
    //  二、其他辅助方法（供上面五个方法复用）
    // ==================================================================

    /**
     * 查询表里的全部记录，演示 {@link #resultSetToList} 的典型用法。
     *
     * @param orderByColumn 可选的排序列（null 表示不排序）
     */
    public static <T> List<T> findAll(Class<T> clazz, String orderByColumn, Connection connection) throws SQLException {
        Objects.requireNonNull(clazz, "目标 Class 不能为空");
        Objects.requireNonNull(connection, "Connection 不能为空");

        EntityMeta meta = metaOf(clazz);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(quote(meta.tableName));
        if (orderByColumn != null && !orderByColumn.isBlank()) {
            sql.append(" ORDER BY ").append(quote(orderByColumn));
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            return resultSetToList(rs, clazz);
        }
    }

    /** 查询表里的全部记录（默认按主键排序）。 */
    public static <T> List<T> findAll(Class<T> clazz, Connection connection) throws SQLException {
        EntityMeta meta = metaOf(clazz);
        return findAll(clazz, meta.pk == null ? null : meta.pk.columnName, connection);
    }

    /** 统计某张表的记录数。 */
    public static long count(Class<?> clazz, Connection connection) throws SQLException {
        EntityMeta meta = metaOf(clazz);
        String sql = "SELECT COUNT(*) FROM " + quote(meta.tableName);
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** 取某个实体的表名（辅助方法，便于调试）。 */
    public static String getTableName(Class<?> clazz) {
        return metaOf(clazz).tableName;
    }

    // ---------------- 行映射 ----------------

    /** 把结果集当前行映射成实体对象。 */
    private static <T> T mapRow(ResultSet rs, Class<T> clazz, EntityMeta meta) throws SQLException {
        T instance = newInstance(clazz);
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String label = rsmd.getColumnLabel(i);
            FieldMeta fm = meta.findByColumn(label);
            if (fm == null) {
                // 结果集里存在实体中没有的列（如连表查询的附加列），直接忽略
                continue;
            }
            Object raw = rs.getObject(i);
            setValue(fm, instance, convert(raw, fm.field.getType()));
        }
        return instance;
    }

    // ---------------- 命名转换 ----------------

    /** 驼峰 → 下划线小写，例：{@code enrollDate → enroll_date}。 */
    public static String camelToUnderline(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 下划线 → 驼峰，例：{@code enroll_date → enrollDate}。 */
    public static String underlineToCamel(String name) {
        if (name == null || name.indexOf('_') < 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upperNext = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
            upperNext = false;
        }
        return sb.toString();
    }

    /** 给标识符加反引号，避免表名/列名撞上 MySQL 关键字。 */
    private static String quote(String identifier) {
        return "`" + identifier + "`";
    }

    // ---------------- 反射工具 ----------------

    /** 用无参构造反射创建实例。 */
    private static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("无法创建实例 " + clazz.getName() + "，请确认它提供了无参构造方法", e);
        }
    }

    /** 反射读取字段值。 */
    private static Object getValue(FieldMeta fm, Object target) {
        try {
            return fm.field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取字段失败：" + fm.field, e);
        }
    }

    /** 反射写入字段值。 */
    private static void setValue(FieldMeta fm, Object target, Object value) {
        try {
            fm.field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("写入字段失败：" + fm.field + "，值=" + value, e);
        }
    }

    /**
     * 判断一个值是否算作「空主键」。数字 0 也视为空，
     * 以便 {@code new Student()} 这种 id 默认 0 的对象也能走自增逻辑。
     */
    private static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof Number && ((Number) value).longValue() == 0L;
    }

    // ---------------- 类型转换 ----------------

    /**
     * 把 JDBC 返回的对象转换成实体字段需要的类型。
     * 覆盖了作业里用到的全部类型：基本类型/包装类、String、BigDecimal、
     * Boolean、LocalDate、LocalDateTime、LocalTime、java.util.Date、枚举。
     */
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return defaultValue(targetType);
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        String text = value.toString().trim();

        if (targetType == String.class || targetType == CharSequence.class) {
            return text;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number n ? n.intValue() : Integer.valueOf(strip(text));
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number n ? n.longValue() : Long.valueOf(strip(text));
        }
        if (targetType == Short.class || targetType == short.class) {
            return value instanceof Number n ? n.shortValue() : Short.valueOf(strip(text));
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return value instanceof Number n ? n.byteValue() : Byte.valueOf(strip(text));
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.valueOf(strip(text));
        }
        if (targetType == Float.class || targetType == float.class) {
            return value instanceof Number n ? n.floatValue() : Float.valueOf(strip(text));
        }
        if (targetType == BigDecimal.class) {
            return value instanceof BigDecimal bd ? bd : new BigDecimal(text);
        }
        if (targetType == BigInteger.class) {
            return value instanceof BigInteger bi ? bi : new BigInteger(text);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof Number n) {
                return n.intValue() != 0;
            }
            return "1".equals(text) || Boolean.parseBoolean(text);
        }
        if (targetType == Character.class || targetType == char.class) {
            return text.isEmpty() ? '\0' : text.charAt(0);
        }
        if (targetType == LocalDate.class) {
            if (value instanceof java.sql.Date d) {
                return d.toLocalDate();
            }
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime().toLocalDate();
            }
            if (value instanceof LocalDateTime ldt) {
                return ldt.toLocalDate();
            }
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        }
        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime();
            }
            if (value instanceof java.sql.Date d) {
                return d.toLocalDate().atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(' ', 'T'));
        }
        if (targetType == LocalTime.class) {
            if (value instanceof Time t) {
                return t.toLocalTime();
            }
            return LocalTime.parse(text);
        }
        if (targetType == java.util.Date.class) {
            if (value instanceof Timestamp ts) {
                return new java.util.Date(ts.getTime());
            }
            if (value instanceof java.sql.Date d) {
                return new java.util.Date(d.getTime());
            }
            return new java.util.Date(Long.parseLong(text));
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object constant = Enum.valueOf((Class<? extends Enum>) targetType, text);
            return constant;
        }
        if (targetType == Object.class) {
            return value;
        }
        // 兜底：尝试用「String 构造方法」转换，失败则原样返回
        try {
            Constructor<?> c = targetType.getConstructor(String.class);
            return c.newInstance(text);
        } catch (Exception ignored) {
            return value;
        }
    }

    /** 基本类型的默认值（避免把 null 写进 int 字段导致反射报错）。 */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    /** 去掉字符串里可能出现的千分位等干扰字符。 */
    private static String strip(String text) {
        return text.replace(",", "");
    }

    /**
     * 把 Java 值转换成 JDBC 能直接 setObject 的值：
     * JDK8 时间类型 → java.sql 时间类型，枚举 → 名字字符串。
     */
    private static Object toSqlValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return java.sql.Date.valueOf(d);
        }
        if (value instanceof LocalDateTime dt) {
            return Timestamp.valueOf(dt);
        }
        if (value instanceof LocalTime t) {
            return Time.valueOf(t);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return value;
    }

    // ==================================================================
    //  三、实体元数据（表名 / 列名 / 主键）
    // ==================================================================

    private static EntityMeta metaOf(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, EntityMeta::new);
    }

    /** 一个实体类的映射元数据。 */
    private static final class EntityMeta {

        final Class<?> clazz;
        final String tableName;
        final List<FieldMeta> fields = new ArrayList<>();
        FieldMeta pk;

        EntityMeta(Class<?> clazz) {
            this.clazz = clazz;

            Table table = clazz.getAnnotation(Table.class);
            this.tableName = (table != null && !table.value().isBlank())
                    ? table.value()
                    : camelToUnderline(clazz.getSimpleName());

            // 连同父类字段一起扫描，子类字段优先
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                        continue;
                    }
                    Column column = field.getAnnotation(Column.class);
                    String columnName = (column != null && !column.value().isBlank())
                            ? column.value()
                            : camelToUnderline(field.getName());

                    Id id = field.getAnnotation(Id.class);
                    FieldMeta fm = new FieldMeta(field, columnName, id != null, id != null && id.autoIncrement());
                    fields.add(fm);

                    if (fm.isPk) {
                        if (pk != null) {
                            throw new IllegalStateException("实体 " + clazz.getName()
                                    + " 同时给多个字段标注了 @Id，JDBCTool 只支持单主键");
                        }
                        pk = fm;
                    }
                }
            }
        }

        /** 列名 → 字段，先按列名精确匹配，再按「下划线转驼峰 = 属性名」兜底。 */
        FieldMeta findByColumn(String label) {
            if (label == null) {
                return null;
            }
            for (FieldMeta fm : fields) {
                if (fm.columnName.equalsIgnoreCase(label)) {
                    return fm;
                }
            }
            String camel = underlineToCamel(label);
            for (FieldMeta fm : fields) {
                if (fm.field.getName().equalsIgnoreCase(camel)) {
                    return fm;
                }
            }
            return null;
        }

        FieldMeta requirePk() {
            if (pk == null) {
                throw new IllegalStateException("实体 " + clazz.getName()
                        + " 没有标注 @Id 字段，无法按主键定位记录");
            }
            return pk;
        }
    }

    /** 一个字段的映射元数据。 */
    private static final class FieldMeta {

        final Field field;
        final String columnName;
        final boolean isPk;
        final boolean autoIncrement;

        FieldMeta(Field field, String columnName, boolean isPk, boolean autoIncrement) {
            this.field = field;
            this.columnName = columnName;
            this.isPk = isPk;
            this.autoIncrement = autoIncrement;
            this.field.setAccessible(true);
        }

        String name() {
            return field.getName();
        }
    }
}
