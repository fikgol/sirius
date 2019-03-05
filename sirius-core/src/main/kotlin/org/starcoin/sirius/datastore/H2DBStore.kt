package org.starcoin.sirius.datastore

import com.google.common.base.Preconditions
import org.sql2o.Connection
import org.sql2o.Sql2o
import java.io.File

class H2DBStore(private val sql2o: Sql2o, private val tableName: String) : DataStore<ByteArray, ByteArray> {

    /**
     * default use memory db.
     */
    constructor(tableName: String, dbName: String = "default") : this(
        Sql2o(
            h2dbUrlMemoryFormat.format(tableName),
            "sa",
            ""
        ), dbName
    )

    constructor(
        tableName: String,
        dbDir: File
    ) : this(dbDir.let {
        Preconditions.checkState(!dbDir.exists() || dbDir.isDirectory)
        Sql2o(
            h2dbUrlDiskFormat.format(it.absolutePath, tableName)
        )
    }, tableName)

    override fun put(key: ByteArray, value: ByteArray) {
        sql2o.beginTransaction().use { conn ->
            return putByConn(key, value, conn)
        }
    }

    private fun putByConn(key: ByteArray, value: ByteArray, conn: Connection) {
        conn.createQuery("insert into $tableName values(:key,:value)")
            .addParameter("key", key)
            .addParameter("value", value)
            .executeUpdate()
        conn.commit()
    }

    override fun get(key: ByteArray): ByteArray? {
        sql2o.open().use { conn ->
            return getByConn(key, conn)
        }
    }

    private fun getByConn(key: ByteArray, conn: Connection): ByteArray? {
        val query = "SELECT value FROM $tableName WHERE key = :key"

        return conn.createQuery(query)
            .addParameter("key", key).executeAndFetchFirst(ByteArray::class.java)
    }

    override fun delete(key: ByteArray) {
        sql2o.beginTransaction().use { con ->
            con.createQuery("delete from $tableName where key=:key")
                .addParameter("key", key)
                .executeUpdate()
            con.commit()
        }
    }

    override fun flush(): Boolean {
        return true
    }

    override fun updateBatch(rows: Map<ByteArray, ByteArray>) {
        sql2o.beginTransaction().use { conn ->
            for ((k, v) in rows) {
                putByConn(k, v, conn)
            }
        }
    }

    override fun keys(): List<ByteArray> {
        sql2o.open().use { con ->
            val query = "SELECT key FROM $tableName"
            return con.createQuery(query).executeAndFetch(ByteArray::class.java)
        }
    }

    override fun iterator(): CloseableIterator<Pair<ByteArray, ByteArray>> {
        //TODO optimize
        val conn = sql2o.open()
        val query = "SELECT key FROM $tableName"
        val iterable = conn.createQuery(query).executeAndFetchLazy(ByteArray::class.java)
        return CloseableIterator(iterable.iterator().asSequence().map { key ->
            val value = getByConn(key, conn)!!
            Pair(key, value)
        }.iterator()) {
            iterable.close()
        }
    }

    override fun destroy() {
        sql2o.beginTransaction().use { conn ->
            conn.createQuery("drop table IF EXISTS $tableName")
                .executeUpdate()
            conn.commit()
        }
    }

    override fun init() {
        sql2o.beginTransaction().use { conn ->
            conn.createQuery("create table IF NOT EXISTS $tableName (key BINARY(100),value BINARY(1000))")
                .executeUpdate()
            conn.commit()
        }
    }

    fun existTable(tableName: String): Boolean {
        return queryTableName(tableName) != null
    }

    private fun queryTableName(tableName: String): String? {
        return sql2o.beginTransaction().use { conn ->
            conn.createQuery(
                """
                    select table_name
                    from information_schema.tables
                    where table_schema = 'PUBLIC'
                      and table_type = 'TABLE'
                      and table_name = '$tableName'
                    """
            ).executeAndFetch(String::class.java).firstOrNull()
        }
    }

    fun getTable(tableName: String): H2DBStore? {
        val table = queryTableName(tableName)
        return table?.let { H2DBStore(sql2o, table) }
    }

    fun getOrCreateTable(tableName: String): H2DBStore {
        return H2DBStore(sql2o, tableName).apply { init() }
    }

    companion object {
        const val h2dbUrlMemoryFormat =
            "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;MODE=Mysql"
        const val h2dbUrlDiskFormat = "jdbc:h2:%s/%s/data:starcoin;FILE_LOCK=FS;PAGE_SIZE=1024;CACHE_SIZE=819"
    }
}