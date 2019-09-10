package com.github.alexmog.hlvx.core.database.sql.orm;

import io.vertx.ext.sql.UpdateResult;

import java.beans.Transient;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SQLEntity<T> {
    private static Map<Class<?>, SQLEntity<?>> cache = new ConcurrentHashMap<>();
    private final Class<T> entityClass;
    private String database;
    private String table;
    private String primaryKeyColumn;
    private Field primaryKeyField;
    private String selectSQL;
    private final Map<String, FieldData> columns;

    private SQLEntity(Class<T> entityClass) {
        this.entityClass = entityClass;
        initDatabase();
        initPrimaryKey();
        columns = Stream.of(entityClass.getDeclaredFields())
                .filter(field -> field.getAnnotation(Transient.class) == null)
                .collect(Collectors.toMap(Field::getName, val -> new FieldData(resolveColumnFromField(val), val)));
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ")
            .append(
                    columns.entrySet().stream()
                        .map(i -> "`" + i.getValue().getColumnName() + "` AS `" + i.getKey() + "`")
                        .reduce((l, r) -> l + "," + r).orElse("")
            )
            .append(" FROM `")
            .append(table)
            .append("`");
        selectSQL = builder.toString();
    }

    private void initDatabase() {
        Database database = entityClass.getAnnotation(Database.class);
        if (database == null || database.database().isEmpty())
            throw new RuntimeException("Database field must be set to a valid database");

        // TODO Check database in databases manager later
        this.database = database.database();

        // Init table
        if (database.table().isEmpty())
            table = database.table().replaceAll("[A-Z]", "_$0").toLowerCase();
        else table = database.table();
    }

    private void initPrimaryKey() {
        Optional<Field> field = Stream.of(entityClass.getDeclaredFields())
                .filter(f -> f.getAnnotation(Id.class) != null)
                .findFirst();

        if (field.isPresent()) {
            primaryKeyField = field.get();
            primaryKeyColumn = resolveColumnFromField(primaryKeyField);
        }
    }

    public static <E> SQLEntity<E> of(Class<E> entityClass) {
        SQLEntity<E> entity = (SQLEntity<E>) cache.get(entityClass);
        if (entity == null) {
            entity = new SQLEntity<E>(entityClass);
            cache.put(entityClass, entity);
        }
        return entity;
    }

    private static String resolveColumnFromField(Field field) {
        field.setAccessible(true);

        Column column = field.getAnnotation(Column.class);
        return column != null && !column.name().isEmpty() ?
                column.name() : field.getName().replaceAll("[A-Z]", "_$0").toLowerCase();
    }

    public void setId(T item, UpdateResult result) throws IllegalAccessException {
        if (primaryKeyField != null) {
            if (primaryKeyField.getType().equals(Integer.class))
                primaryKeyField.set(item, result.getKeys().getInteger(0));
            else if (primaryKeyField.getType().equals(Long.class))
                primaryKeyField.set(item, result.getKeys().getLong(0));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Id {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Column {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Database {
        String database();
        String table() default "";
    }

    public static class FieldData {
        private final String columnName;
        private final Field field;

        public FieldData(String columnName, Field field) {
            this.columnName = columnName;
            this.field = field;
        }

        public String getColumnName() {
            return columnName;
        }

        public Field getField() {
            return field;
        }
    }
}
