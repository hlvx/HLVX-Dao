package com.github.hlvx.dao.database.sql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SQLSession implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(SQLSession.class);
    private final SQLConnection connection;
    private boolean closed = false;

    private SQLSession(SQLConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new SQLSession, useful when using multiple DAOs with the same session
     * @param client The SQL client to use
     * @param handler The handler to use
     */
    public static void createSession(SQLClient client, Handler<AsyncResult<SQLSession>> handler) {
        client.getConnection(connectionResult -> {
            if (connectionResult.failed()) {
                handler.handle(Future.failedFuture(connectionResult.cause()));
                return;
            }

            Vertx.currentContext().exceptionHandler(ex -> {
                List<Closeable> toClose = Vertx.currentContext().get("hlvx.dao.toClose");
                if (toClose != null) toClose.forEach(e -> {
                    try {
                        e.close();
                    } catch (IOException exc) {
                        logger.error("Close error", exc);
                    }
                });
                Vertx.currentContext().remove("hlvx.dao.toClose");
                throw new RuntimeException(ex);
            });

            SQLSession session = new SQLSession(connectionResult.result());
            List<Closeable> toClose = Vertx.currentContext().get("hlvx.dao.toClose");
            if (toClose == null) {
                toClose = new CopyOnWriteArrayList<>();
                Vertx.currentContext().put("hlvx.dao.toClose", toClose);
            }
            toClose.add(session);
            handler.handle(Future.succeededFuture(session));
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
    public void close() {
        if (closed) return;
        closed = true;
        connection.close(result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
        });
    }
}
