package com.github.hlvx.dao.database.sql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

import java.io.Closeable;
import java.util.List;

/**
 * Base class for DAOs
 * It is intended to be inherited from each DAO created
 */
public abstract class DAO implements Closeable {
    private SQLSession session;
    private DaoManager manager;
    private boolean closeSession;

    protected void setCloseSession(boolean close) {
        closeSession = close;
    }

    protected void setSession(SQLSession session) {
        this.session = session;
    }

    protected void setManager(DaoManager daoManager) {
        this.manager = daoManager;
    }

    protected void executeUpdate(Handler<AsyncResult<UpdateResult>> consumer, String query, Object... params) {
        session.executeUpdate(consumer, query, params);
    }

    protected void executeBatch(Handler<AsyncResult<List<Integer>>> consumer, String query, JsonArray... args) {
        session.executeBatch(consumer, query, args);
    }

    protected void executeBatchCallable(Handler<AsyncResult<List<Integer>>> consumer, String query, List<JsonArray> outputArgs,
                                     JsonArray... args) {
        session.executeBatchCallable(consumer, query, outputArgs, args);
    }

    protected void executeInsert(Handler<AsyncResult<UpdateResult>> consumer, String query, Object... params) {
        session.executeInsert(consumer, query, params);
    }

    protected void executeQuery(Handler<AsyncResult<ResultSet>> consumer, String query, Object... params) {
        session.executeQuery(consumer, query, params);
    }

    public void startTransaction(Handler<AsyncResult<Void>> handler) {
        session.startTransaction(result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
                return;
            }
            handler.handle(Future.succeededFuture(result.result()));
        });
    }

    public void commit(Handler<AsyncResult<Void>> handler) {
        session.commit(result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
                return;
            }
            handler.handle(Future.succeededFuture(result.result()));
        });
    }

    public void rollback(Handler<AsyncResult<Void>> handler) {
        session.rollback(result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
                return;
            }
            handler.handle(Future.succeededFuture(result.result()));
        });
    }

    @Override
    public void close() {
        if (session == null || manager == null) return;
        if (closeSession) session.close();
        session = null;
        try {
            manager.returnDao(this);
            manager = null;
        } catch (Exception e) {
            manager = null;
            throw new RuntimeException(e);
        }
    }
}
