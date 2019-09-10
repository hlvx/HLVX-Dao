package com.github.alexmog.hlvx.core.database.sql;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

import java.util.List;

/**
 * Base class for DAOs
 * It is intended to be inherited from each DAO created
 */
public abstract class DAO implements AutoCloseable {
    private SQLSession session;
    private DaoManager manager;

    protected void setSession(SQLSession session) {
        this.session = session;
    }

    protected void setManager(DaoManager daoManager) {
        this.manager = manager;
    }

    protected void executeUpdate(Handler<UpdateResult> consumer, String query, Object... params) {
        session.executeUpdate(consumer, query, params);
    }

    protected void executeBatch(Handler<List<Integer>> consumer, String query, JsonArray... args) {
        session.executeBatch(consumer, query, args);
    }

    protected void executeBatchCallable(Handler<List<Integer>> consumer, String query, List<JsonArray> outputArgs,
                                     JsonArray... args) {
        session.executeBatchCallable(consumer, query, outputArgs, args);
    }

    protected void executeInsert(Handler<UpdateResult> consumer, String query, Object... params) {
        session.executeInsert(consumer, query, params);
    }

    protected void executeQuery(Handler<ResultSet> consumer, String query, Object... params) {
        session.executeQuery(consumer, query, params);
    }

    public void startTransaction(Handler<DAO> handler) {
        session.startTransaction(result -> handler.handle(this));
    }

    public void commit(Handler<DAO> handler) {
        session.commit(result -> handler.handle(this));
    }

    public void rollback(Handler<DAO> handler) {
        session.rollback(result -> handler.handle(this));
    }

    @Override
    public void close() throws Exception {
        if (session == null) return;
        session = null;
        manager = null;
        manager.returnDao(this);
    }
}
