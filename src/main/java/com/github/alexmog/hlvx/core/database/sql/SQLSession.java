package com.github.alexmog.hlvx.core.database.sql;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SQLSession implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SQLSession.class);
    private final SQLConnection connection;
    private boolean closed = false;

    private SQLSession(SQLConnection connection) {
        this.connection = connection;
    }

    protected static void createSession(SQLClient client, Handler<SQLSession> handler) {
        client.getConnection(connectionResult -> {
            if (connectionResult.failed()) throw new RuntimeException(connectionResult.cause());
            SQLSession session = new SQLSession(connectionResult.result());
            handler.handle(session);
            try {
                session.close();
            } catch (Exception e) {
                logger.error("Cannot close Session ", e);
            }
        });
    }

    protected void executeUpdate(Handler<UpdateResult> consumer, String query, Object... params) {
        connection.updateWithParams(query, new JsonArray(List.of(params)), result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(result.result());
        });
    }

    protected void executeBatch(Handler<List<Integer>> consumer, String query, JsonArray... args) {
        connection.batchWithParams(query, List.of(args), result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(result.result());
        });
    }

    protected void executeBatchCallable(Handler<List<Integer>> consumer, String query, List<JsonArray> outputArgs,
                                     JsonArray... args) {
        connection.batchCallableWithParams(query, List.of(args), outputArgs, result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(result.result());
        });
    }

    protected void executeInsert(Handler<UpdateResult> consumer, String query, Object... params) {
        executeUpdate(consumer, query, params);
    }

    protected void executeQuery(Handler<ResultSet> consumer, String query, Object... params) {
        connection.queryWithParams(query, new JsonArray(List.of(params)), result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(result.result());
        });
    }

    protected void startTransaction(Handler<Void> consumer) {
        connection.setAutoCommit(false, result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(null);
        });
    }

    protected void commit(Handler<Void> consumer) {
        connection.commit(result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(null);
        });
    }

    protected void rollback(Handler<Void> consumer) {
        connection.rollback(result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            consumer.handle(null);
        });
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        closed = true;
        connection.setAutoCommit(true, r -> {
            connection.close(result -> {
                if (result.failed()) throw new RuntimeException(result.cause());
            });
        });
    }
}
