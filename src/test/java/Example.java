import com.github.hlvx.dao.database.sql.DAO;
import com.github.hlvx.dao.database.sql.DaoManager;
import com.github.hlvx.dao.database.sql.SQLSession;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

public class Example {
    public static void main(String[] args) throws NoSuchMethodException {
        test();
    }

    public static void test() throws NoSuchMethodException {
        Vertx vertx = Vertx.vertx();
        JsonObject config = new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30);
        JDBCClient client = JDBCClient.createShared(vertx, config);

        // Creates a new manager for this JDBC configuration
        DaoManager manager = new DaoManager(client);

        // Register our DAOs.
        // This step is optional but will increase the speed of the first instantiation of each DAO
        manager.registerDao(MyDaoClass.class);
        manager.registerDao(MyDaoClass2.class);

        // Simple query example
        manager.createDao(MyDaoClass.class, daoResult -> {
            if (daoResult.succeeded()) {
                daoResult.result().exampleQuery(resultSet -> {
                    if (resultSet.succeeded()) {
                        ResultSet rs = resultSet.result();
                        // Do what you want to do with your result set here
                        daoResult.result().close();
                    } else {
                        // Handle error
                        throw new RuntimeException(resultSet.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                    }
                });
            } else {
                // Handle error
                throw new RuntimeException(daoResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
            }
        });

        // Single DAO Transaction example
        manager.createDao(MyDaoClass.class, daoResult -> {
            if (daoResult.succeeded()) {
                MyDaoClass dao = daoResult.result();
                dao.startTransaction(sessionResult -> {
                    if (sessionResult.succeeded()) {
                        dao.exampleInsert(resultSetResult -> {
                            if (resultSetResult.succeeded()) {
                                dao.deleteUser(deletionResult -> {
                                    if (deletionResult.succeeded()) {
                                        dao.commit(result -> {
                                            if (result.failed()) {
                                                dao.rollback(result2 -> {
                                                    if (result2.failed()) {
                                                        // Handle error
                                                        throw new RuntimeException(result2.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                                    }
                                                });
                                                // Handle error
                                                throw new RuntimeException(result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                            } else dao.close();
                                        });
                                    } else {
                                        dao.rollback(result -> {
                                            if (result.failed()) {
                                                // Handle error
                                                throw new RuntimeException(result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                            }
                                        });
                                        // Handle error
                                        throw new RuntimeException(deletionResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                    }
                                }, "username");
                            } else {
                                dao.rollback(result -> {
                                    if (result.failed()) {
                                        // Handle error
                                        throw new RuntimeException(result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                    }
                                });
                                // Handle error
                                throw new RuntimeException(resultSetResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                            }
                        }, "username", "email");
                    } else {
                        // Handle error
                        throw new RuntimeException(sessionResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                    }
                });
            } else {
                // Handle error
                throw new RuntimeException(daoResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
            }
        });

        // Multiple DAO Transaction example
        SQLSession.createSession(client, sessionResult -> {
            if (sessionResult.succeeded()) {
                SQLSession session = sessionResult.result();
                manager.createDao(session, MyDaoClass.class, dao1Result -> {
                    if (dao1Result.succeeded()) {
                        MyDaoClass dao1 = dao1Result.result();
                        manager.createDao(session, MyDaoClass2.class, dao2Result -> {
                            MyDaoClass2 dao2 = dao2Result.result();
                            if (dao2Result.succeeded()) {
                                session.startTransaction(result -> {
                                    if (result.succeeded()) {
                                        dao1.exampleInsert(resultSet1Result -> {
                                            if (resultSet1Result.succeeded()) {
                                                dao2.exampleUpdate(resultSet2Result -> {
                                                    if (resultSet2Result.succeeded()) {
                                                        session.commit(commitResult -> {
                                                            if (commitResult.failed()) {
                                                                // Handle error
                                                                throw new RuntimeException(sessionResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                                            }
                                                        });
                                                        session.close();
                                                        dao1.close();
                                                        dao2.close();
                                                    } else {
                                                        session.rollback(rollbackResult -> {
                                                            if (rollbackResult.failed()) {
                                                                // Handle error
                                                                throw new RuntimeException(sessionResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                                            }
                                                        });
                                                        // Handle error
                                                        throw new RuntimeException(resultSet2Result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                                    }
                                                }, "username", "newUsername");
                                            } else {
                                                session.rollback(rollbackResult -> {
                                                    if (rollbackResult.failed()) {
                                                        // Handle error
                                                        throw new RuntimeException(rollbackResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                                    }
                                                });
                                                // Handle error
                                                throw new RuntimeException(resultSet1Result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                            }
                                        }, "username", "email");
                                    } else {
                                        // Handle error
                                        throw new RuntimeException(result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                                    }
                                });
                            } else {
                                // Handle error
                                throw new RuntimeException(dao2Result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                            }
                        });
                    } else {
                        // Handle error
                        throw new RuntimeException(dao1Result.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
                    }
                });
            } else {
                // Handle error
                throw new RuntimeException(sessionResult.cause()); // This will ensure that the DAO and the session are closed. You can also use dao.close(); manually
            }
        });
    }

    private static class MyDaoClass extends DAO {
        public void exampleQuery(Handler<AsyncResult<ResultSet>> handler) {
            executeQuery(handler, "SELECT * FROM users");
        }

        public void exampleInsert(Handler<AsyncResult<UpdateResult>> handler, String username, String email) {
            executeInsert(handler, "INSERT INTO users (username, email) VALUES (?, ?)", username, email);
        }

        public void deleteUser(Handler<AsyncResult<UpdateResult>> handler, String username) {
            executeUpdate(handler, "DELETE FROM users WHERE username = ?", username);
        }
    }

    private static class MyDaoClass2 extends DAO {
        public void exampleUpdate(Handler<AsyncResult<UpdateResult>> handler, String username, String newUsername) {
            executeUpdate(handler, "UPDATE users SET username = ? WHERE username = ?", newUsername, username);
        }
    }
}
