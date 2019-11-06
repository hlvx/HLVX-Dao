package com.github.hlvx.dao.database.sql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.SQLClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DaoManager {
    private static final Logger logger = LoggerFactory.getLogger(DaoManager.class);
    private final SQLClient client;
    private final Map<Class<? extends DAO> , ObjectPool<DAO>> daoPools = new ConcurrentHashMap<>();

    public DaoManager(SQLClient client) {
        this.client = client;
    }

    /**
     * Register DAOs, this method will avoid to have a bottleneck when calling {@link #createDao(Class, Handler)}
     * @param dao The dao Class to manage
     * @throws NoSuchMethodException Will be thrown if a default empty constructor didn't exist
     */
    public void registerDao(Class<? extends DAO> dao) throws NoSuchMethodException {
        ObjectPool<DAO> pool = daoPools.get(dao);
        if (pool == null) {
            pool = new GenericObjectPool<>(new Factory(dao.getConstructor()));
            daoPools.put(dao, pool);
        }
    }

    /**
     * Creates a new instance of a DAO assigned to a unique session for this DAO
     * The DAO is automatically closed after the handler finishes its actions
     * It is strongly recommended to register the Dao Class before calling this method at the beginning og the
     * program using {@link #registerDao(Class)}
     * @param dao The dao Class to instantiate
     * @param handler A handler to retrieve the created DAO
     * @param <T> The DAO generic type you want to instantiate
     */
    public <T extends DAO> void createDao(Class<T> dao, Handler<AsyncResult<T>> handler) {
        try {
            ObjectPool<DAO> pool = daoPools.get(dao);
            if (pool == null) {
                pool = new GenericObjectPool<>(new Factory(dao.getConstructor()));
                daoPools.put(dao, pool);
            }

            DAO instance = pool.borrowObject();
            SQLSession.createSession(client, sessionResult -> {
                if (sessionResult.failed()) {
                    handler.handle(Future.failedFuture(sessionResult.cause()));
                    return;
                }
                instance.setSession(sessionResult.result());
                instance.setManager(this);
                instance.setCloseSession(true);
                addToClose(instance);
                handler.handle(Future.succeededFuture((T) instance));
            });
        } catch (Exception e) {
            logger.error("Exception catched for DAO " + dao, e);
            handler.handle(Future.failedFuture(e));
        }
    }

    private void addToClose(DAO instance) {
        List<Closeable> toClose = Vertx.currentContext().get("hlvx.dao.toClose");
        if (toClose == null) {
            toClose = new CopyOnWriteArrayList<>();
            Vertx.currentContext().put("hlvx.dao.toClose", toClose);
        }
        toClose.add(instance);
    }

    /**
     * Creates a new instance of a DAO assigned the associated SQLSession
     * The DAO is automatically closed after the handler finishes its actions
     * It is strongly recommended to register the Dao Class before calling this method at the beginning og the
     * program using {@link #registerDao(Class)}
     * @param session A valid SQLSession generated using {@link SQLSession#createSession(SQLClient, Handler)}
     * @param dao The dao Class to instantiate
     * @param handler A handler to retrieve the created DAO
     * @param <T> The DAO generic type you want to instantiate
     */
    public <T extends DAO> void createDao(SQLSession session, Class<T> dao, Handler<AsyncResult<T>> handler) {
        try {
            ObjectPool<DAO> pool = daoPools.get(dao);
            if (pool == null) {
                pool = new GenericObjectPool<>(new Factory(dao.getConstructor()));
                daoPools.put(dao, pool);
            }

            DAO instance = pool.borrowObject();
            instance.setSession(session);
            instance.setManager(this);
            addToClose(instance);
            handler.handle(Future.succeededFuture((T) instance));
        } catch (Exception e) {
            logger.error("Exception catched for DAO " + dao, e);
            handler.handle(Future.failedFuture(e));
        }
    }

    protected void returnDao(DAO dao) throws Exception {
        ObjectPool<DAO> pool = daoPools.get(dao.getClass());
        if (pool == null) throw new RuntimeException("Dao not registered");
        pool.returnObject(dao);
    }

    private static class Factory extends BasePooledObjectFactory<DAO> {
        private final Constructor<?> constructor;

        public Factory(Constructor<?> constructor) {
            this.constructor = constructor;
        }

        @Override
        public DAO create() throws Exception {
            return (DAO) constructor.newInstance();
        }

        @Override
        public PooledObject<DAO> wrap(DAO dao) {
            return new DefaultPooledObject<>(dao);
        }
    }
}
