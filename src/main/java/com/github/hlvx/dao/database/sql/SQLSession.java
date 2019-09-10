package com.github.hlvx.dao.database.sql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class SQLSession implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SQLSession.class);
    private final SQLConnection connection;
    private boolean closed = false;

    private SQLSession(SQLConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new SQLSession, useful when using multiple DAOs with the same session
     * @param client
     * @param handler
     */
    public static void createSession(SQLClient client, Handler<AsyncResult<SQLSession>> handler) {
        client.getConnection(connectionResult -> {
            if (connectionResult.failed()) {
                handler.handle(Future.failedFuture(connectionResult.cause()));
                return;
            }
            SQLSession session = new SQLSession(connectionResult.result());
            handler.handle(Future.succeededFuture(session));
            try {
                session.close();
            } catch (Exception e) {
                logger.error("Cannot close Session ", e);
            }
        });
    }

    protected void executeUpdate(Handler<AsyncResult<UpdateResult>> consumer, String query, Object... params) {
        connection.updateWithParams(query, new JsonArray(Arrays.asList(params)), result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    protected void executeBatch(Handler<AsyncResult<List<Integer>>> consumer, String query, JsonArray... args) {
        connection.batchWithParams(query, Arrays.asList(args), result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    protected void executeBatchCallable(Handler<AsyncResult<List<Integer>>> consumer, String query, List<JsonArray> outputArgs,
                                     JsonArray... args) {
        connection.batchCallableWithParams(query, Arrays.asList(args), outputArgs, result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    protected void executeInsert(Handler<AsyncResult<UpdateResult>> consumer, String query, Object... params) {
        executeUpdate(consumer, query, params);
    }

    protected void executeQuery(Handler<AsyncResult<ResultSet>> consumer, String query, Object... params) {
        connection.queryWithParams(query, new JsonArray(Arrays.asList(params)), result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    public void startTransaction(Handler<AsyncResult<Void>> consumer) {
        connection.setAutoCommit(false, result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    public void commit(Handler<AsyncResult<Void>> consumer) {
        connection.commit(result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
        });
    }

    public void rollback(Handler<AsyncResult<Void>> consumer) {
        connection.rollback(result -> {
            if (result.failed()) {
                consumer.handle(Future.failedFuture(result.cause()));
                return;
            }
            consumer.handle(Future.succeededFuture(result.result()));
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
