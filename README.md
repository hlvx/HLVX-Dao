# HLVX-Dao
High Level DAO Library for VertX

The goal of this library is to offer a simple way to create and manage DAOs using VertX.

HLVX-Dao is still in alpha, so please report any [issues](https://github.com/hlvx/HLVX-Dao/issues) discovered.

# Setup
HLVX-Dao is available on maven central, simply add this dependency to your maven project:
```xml
<dependency>
  <groupId>com.github.hlvx</groupId>
  <artifactId>hlvx-dao</artifactId>
  <version>VERSION</version>
</dependency>
```

#### Use HLVX-Dao locally only
Cloning this repository
```bash
git clone https://github.com/hlvx/HLVX-Dao
```
Then install the library locally
```bash
cd HLVX-Dao
mvn install
```

# Examples
All examples assumes that you have a minimal environment similar to the one
defined here:
```java
public class Example {
    public void test() throws NoSuchMethodException {
        Vertx vertx = Vertx.vertx();
        JsonObject config = new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30);
        JDBCClient client = JDBCClient.createShared(vertx, config);
        DaoManager manager = new DaoManager(client);
        manager.registerDao(MyDaoClass.class);
        manager.registerDao(MyDaoClass2.class);

        // Next examples executes here
    }

    private static class MyDaoClass extends DAO<MyDaoClass> {
        public void exampleQuery(Handler<AsyncResult<ResultSet>> handler) {
            executeQuery(handler, "SELECT * FROM users");
        }

        public void exampleInsert(Handler<AsyncResult<UpdateResult>> handler, String username, String email) {
            executeInsert(handler, "INSERT INTO users (username, email) VALUES (?, ?)", username, email);
        }
    }

    private static class MyDaoClass2 extends DAO<MyDaoClass2> {
        public void exampleUpdate(Handler<AsyncResult<UpdateResult>> handler, String username, String newUsername) {
            executeUpdate(handler, "UPDATE users SET username = ? WHERE username = ?", newUsername, username);
        }
    }
}
```
#### Simple query
```java
manager.createDao(MyDaoClass.class, daoResult -> {
    if (daoResult.succeeded()) {
        daoResult.result().exampleQuery(resultSet -> {
            if (resultSet.succeeded()) {
                ResultSet rs = resultSet.result();
                // Do what you want to do with your result set here
            } else {
                // Handle error
            }
        });
    } else {
        // Handle error
    }
});
```

#### Single DAO Transactions
```java
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
                                            }
                                        });
                                        // Handle error
                                    }
                                });
                            } else {
                                dao.rollback(result -> {
                                    if (result.failed()) {
                                        // Handle error
                                    }
                                });
                                // Handle error
                            }
                        }, "username");
                    } else {
                        dao.rollback(result -> {
                            if (result.failed()) {
                                // Handle error
                            }
                        });
                        // Handle error
                    }
                }, "username", "email");
            } else {
                // Handle error
            }
        });
    } else {
        // Handle error
    }
});
```

#### Multiple DAO Transaction
```java
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
                                                    }
                                                });
                                            } else {
                                                session.rollback(rollbackResult -> {
                                                    if (rollbackResult.failed()) {
                                                        // Handle error
                                                    }
                                                });
                                                // Handle error
                                            }
                                        }, "username", "newUsername");
                                    } else {
                                        session.rollback(rollbackResult -> {
                                            if (rollbackResult.failed()) {
                                                // Handle error
                                            }
                                        });
                                        // Handle error
                                    }
                                }, "username", "email");
                            } else {
                                // Handle error
                            }
                        });
                    } else {
                        // Handle error
                    }
                });
            } else {
                // Handle error
            }
        });
    } else {
        // Handle error
    }
});
```

# Logging
HLVX-Dao uses [Slf4j](https://www.slf4j.org/) logging API. In order to see all messages produces
by HLVX-Dao use a Slf4j compatible logging implementation.

## Logback logging settings example
```xml
<logger name="com.github.hlvx" level="DEBUG" />
```