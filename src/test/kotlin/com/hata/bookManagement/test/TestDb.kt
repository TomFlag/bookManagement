package com.hata.bookManagement.test

import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object TestDb {
    private lateinit var container: PostgreSQLContainer<*>
    private lateinit var dslContext: DSLContext
    private val lock = Any()

    /**
     * Start the shared Postgres container and return the DSLContext and container.
     * This is idempotent and will only start the container once per JVM.
     */
    fun start(): Pair<DSLContext, PostgreSQLContainer<*>> {
        if (!::container.isInitialized) {
            synchronized(lock) {
                if (!::container.isInitialized) {
                    val postgresImage = DockerImageName.parse("postgres:15-alpine")
                    container = PostgreSQLContainer(postgresImage)
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test")
                    container.start()

                    val url = container.jdbcUrl
                    val user = container.username
                    val pass = container.password

                    // Run Flyway migrations against the test container
                    Flyway.configure()
                        .dataSource(url, user, pass)
                        .locations("filesystem:src/main/resources/db/migration")
                        .load()
                        .migrate()

                    val ds = PGSimpleDataSource().apply {
                        setURL(url)
                        setUser(user)
                        password = pass
                    }

                    dslContext = DSL.using(ds, SQLDialect.POSTGRES)

                    // Ensure container stops on JVM exit
                    Runtime.getRuntime().addShutdownHook(Thread {
                        try {
                            if (::container.isInitialized) container.stop()
                        } catch (_: Throwable) {
                        }
                    })
                }
            }
        }

        return Pair(dslContext, container)
    }
}
