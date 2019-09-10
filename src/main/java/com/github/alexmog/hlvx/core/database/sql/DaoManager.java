package com.github.alexmog.hlvx.core.database.sql;

import io.vertx.core.Handler;
import io.vertx.ext.sql.SQLClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DaoManager {
    private static final Logger logger = LoggerFactory.getLogger(DaoManager.class);
    private final SQLClient client;
    private final Map<Class<? extends DAO> , ObjectPool<DAO>> daoPools = new ConcurrentHashMap<>();

    public DaoManager(SQLClient client) {
        this.client = client;
    }

    /**
     * Register DAOs, this method will avoid to have a bottleneck when calling {@link #create(Class, Handler)}
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
     * program using {@link #register(Class)}
     * @param dao The dao Class to instantiate
     * @param handler A handler to retrieve the created DAO
     * @param <T> The DAO generic type you want to instantiate
     * @throws Exception
     */
    public <T extends DAO> void createDao(Class<T> dao, Handler<T> handler) throws Exception {
        ObjectPool<DAO> pool = daoPools.get(dao);
        if (pool == null) {
            pool = new GenericObjectPool<>(new Factory(dao.getConstructor()));
            daoPools.put(dao, pool);
        }

        DAO instance = pool.borrowObject();
        SQLSession.createSession(client, session -> {
            instance.setSession(session);
            instance.setManager(this);
            handler.handle((T) instance);
            try {
                instance.close();
            } catch (Exception e) {
                logger.error("Cannot close DAO " + dao, e);
            }
        });
    }

    /**
     * Creates a new instance of a DAO assigned the associated SQLSession
     * The DAO is automatically closed after the handler finishes its actions
     * It is strongly recommended to register the Dao Class before calling this method at the beginning og the
     * program using {@link #register(Class)}
     * @param session A valid SQLSession generated using {@link #createSession(Handler)}
     * @param dao The dao Class to instantiate
     * @param handler A handler to retrieve the created DAO
     * @param <T> The DAO generic type you want to instantiate
     * @throws Exception
     */
    public <T extends DAO> void createDao(SQLSession session, Class<T> dao, Handler<T> handler) throws Exception {
        ObjectPool<DAO> pool = daoPools.get(dao);
        if (pool == null) {
            pool = new GenericObjectPool<>(new Factory(dao.getConstructor()));
            daoPools.put(dao, pool);
        }

        DAO instance = pool.borrowObject();
        instance.setSession(session);
        instance.setManager(this);
        handler.handle((T) instance);
        try {
            instance.close();
        } catch (Exception e) {
            logger.error("Cannot close DAO " + dao, e);
        }
    }

    /**
     * Creates a new SQLSession, useful when using multiple DAOs with the same session
     * @param handler
     */
    public void createSession(Handler<SQLSession> handler) {
        SQLSession.createSession(client, session -> {
            handler.handle(session);
        });
    }

    protected void returnDao(DAO dao) throws Exception {
        ObjectPool<DAO> pool = daoPools.get(dao);
        if (pool == null) throw new IllegalArgumentException("Dao not registered");
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
