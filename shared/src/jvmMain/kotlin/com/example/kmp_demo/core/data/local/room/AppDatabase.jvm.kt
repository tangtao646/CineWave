package com.example.kmp_demo.core.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM 平台上的 Room 数据库构建器
 *
 * Room KMP 在 JVM 上需要显式提供 SQLiteDriver 实现。
 * 这里使用 org.xerial:sqlite-jdbc 作为底层驱动。
 *
 * 注意：JVM 版 Room 使用 name 参数而非 Android Context 来指定数据库文件路径。
 * 数据库文件将创建在当前工作目录下。
 */

/**
 * 基于 JDBC 的 SQLiteDriver 实现
 */
private class JdbcSQLiteDriver : SQLiteDriver {
    override fun open(path: String): SQLiteConnection {
        val conn = DriverManager.getConnection("jdbc:sqlite:$path")
        return JdbcSQLiteConnection(conn)
    }
}

/**
 * 基于 JDBC 的 SQLiteConnection 实现
 *
 * SQLiteConnection 接口只有 prepare() 和 close() 两个方法。
 * Room 内部通过 PRAGMA 语句来管理事务和版本号。
 */
private class JdbcSQLiteConnection(
    private val connection: Connection
) : SQLiteConnection {
    override fun prepare(sql: String): SQLiteStatement {
        val statement = connection.prepareStatement(sql)
        return JdbcSQLiteStatement(statement)
    }

    override fun close() {
        connection.close()
    }
}

/**
 * 基于 JDBC 的 SQLiteStatement 实现
 */
private class JdbcSQLiteStatement(
    private val statement: java.sql.PreparedStatement
) : SQLiteStatement {
    override fun step(): Boolean {
        return statement.execute()
    }

    override fun close() {
        statement.close()
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        statement.setBytes(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        statement.setDouble(index, value)
    }

    override fun bindLong(index: Int, value: Long) {
        statement.setLong(index, value)
    }

    override fun bindNull(index: Int) {
        statement.setNull(index, java.sql.Types.NULL)
    }

    override fun bindText(index: Int, value: String) {
        statement.setString(index, value)
    }

    override fun getBlob(index: Int): ByteArray {
        val rs = statement.resultSet
        return rs?.getBytes(index) ?: ByteArray(0)
    }

    override fun getDouble(index: Int): Double {
        val rs = statement.resultSet
        return rs?.getDouble(index) ?: 0.0
    }

    override fun getLong(index: Int): Long {
        val rs = statement.resultSet
        return rs?.getLong(index) ?: 0L
    }

    override fun getText(index: Int): String {
        val rs = statement.resultSet
        return rs?.getString(index) ?: ""
    }

    override fun isNull(index: Int): Boolean {
        val rs = statement.resultSet
        return rs?.getObject(index) == null
    }

    override fun getColumnCount(): Int {
        return statement.metaData?.columnCount ?: 0
    }

    override fun getColumnName(index: Int): String {
        return statement.metaData?.getColumnName(index) ?: ""
    }

    override fun getColumnType(index: Int): Int {
        val type = statement.metaData?.getColumnTypeName(index) ?: ""
        return when (type.uppercase()) {
            "INTEGER" -> 1
            "FLOAT", "REAL", "DOUBLE" -> 2
            "TEXT", "VARCHAR" -> 3
            "BLOB" -> 4
            else -> 3
        }
    }

    override fun reset() {
        statement.clearParameters()
    }

    override fun clearBindings() {
        statement.clearParameters()
    }
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = "study_demo.db",
    ).setDriver(JdbcSQLiteDriver())
}
