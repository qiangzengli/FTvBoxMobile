package com.github.tvbox.osc.data

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.util.FileUtils
import java.io.File
import java.io.IOException

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
class AppDataManager private constructor() {

    companion object {
        private const val DB_FILE_VERSION = 3
        private const val DB_NAME = "tvbox"
        private var manager: AppDataManager? = null
        private var dbInstance: AppDataBase? = null

        @JvmStatic
        fun init() {
            if (manager == null) {
                synchronized(AppDataManager::class.java) {
                    if (manager == null) {
                        manager = AppDataManager()
                    }
                }
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE sourceState ADD COLUMN tidSort TEXT")
                } catch (e: SQLiteException) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            @SuppressLint("Range")
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `vodRecordTmp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vodId` TEXT, `updateTime` INTEGER NOT NULL, `sourceKey` TEXT, `data` BLOB, `dataJson` TEXT, `testMigration` INTEGER NOT NULL)")

                val cursor = database.query("SELECT * FROM vodRecord")

                var id: Int
                var vodId: Int
                var updateTime: Long
                var sourceKey: String
                var dataJson: String

                while (cursor.moveToNext()) {
                    id = cursor.getInt(cursor.getColumnIndex("id"))
                    vodId = cursor.getInt(cursor.getColumnIndex("vodId"))
                    updateTime = cursor.getLong(cursor.getColumnIndex("updateTime"))
                    sourceKey = cursor.getString(cursor.getColumnIndex("sourceKey"))
                    dataJson = cursor.getString(cursor.getColumnIndex("dataJson"))
                    database.execSQL(
                        "INSERT INTO vodRecordTmp (id, vodId, updateTime, sourceKey, dataJson, testMigration) VALUES" +
                                " ('$id', '$vodId', '$updateTime', '$sourceKey', '$dataJson',0  )"
                    )
                }

                database.execSQL("DROP TABLE vodRecord")
                database.execSQL("ALTER TABLE vodRecordTmp RENAME TO vodRecord")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE vodRecord ADD COLUMN dataJson TEXT")
                } catch (e: SQLiteException) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE localSource ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                } catch (e: SQLiteException) {
                    e.printStackTrace()
                }
            }
        }

        fun dbPath(): String {
            return "$DB_NAME.v$DB_FILE_VERSION.db"
        }

        @JvmStatic
        fun get(): AppDataBase {
            if (manager == null) {
                throw RuntimeException("AppDataManager is no init")
            }
            if (dbInstance == null) {
                dbInstance = Room.databaseBuilder(App.getInstance(), AppDataBase::class.java, dbPath())
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                        }
                    })
                    .allowMainThreadQueries()
                    .build()
            }
            return dbInstance!!
        }

        @Throws(IOException::class)
        @JvmStatic
        fun backup(path: File): Boolean {
            if (dbInstance != null && dbInstance!!.isOpen) {
                dbInstance!!.close()
            }
            val db = App.getInstance().getDatabasePath(dbPath())
            return if (db.exists()) {
                FileUtils.copyFile(db, path)
                true
            } else {
                false
            }
        }

        @Throws(IOException::class)
        @JvmStatic
        fun restore(path: File): Boolean {
            if (dbInstance != null && dbInstance!!.isOpen) {
                dbInstance!!.close()
            }
            val db = App.getInstance().getDatabasePath(dbPath())
            if (db.exists()) {
                db.delete()
            }
            if (!db.parentFile.exists()) {
                db.parentFile.mkdirs()
            }
            FileUtils.copyFile(path, db)
            return true
        }
    }
}
