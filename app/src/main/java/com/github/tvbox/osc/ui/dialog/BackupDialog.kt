package com.github.tvbox.osc.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.data.AppDataManager
import com.github.tvbox.osc.ui.adapter.BackupAdapter
import com.github.tvbox.osc.util.FileUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.owen.tvrecyclerview.widget.TvRecyclerView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Date
import java.util.Locale

class BackupDialog(context: Context) : BaseDialog(context) {

    init {
        setContentView(R.layout.dialog_backup)
        val tvRecyclerView = findViewById<TvRecyclerView>(R.id.list)
        val adapter = BackupAdapter()
        tvRecyclerView.adapter = adapter
        adapter.setNewData(allBackup())
        adapter.setOnItemChildClickListener { ad, view, position ->
            val item = ad.getItem(position) as? String ?: return@setOnItemChildClickListener
            if (view.id == R.id.tvName) {
                restore(item)
            } else if (view.id == R.id.tvDel) {
                delete(item)
                adapter.setNewData(allBackup())
            }
        }
        findViewById<View>(R.id.backupNow).setOnClickListener {
            backup()
            adapter.setNewData(allBackup())
        }
        findViewById<View>(R.id.storagePermission).setOnClickListener {
            if (XXPermissions.isGranted(context, Permission.Group.STORAGE)) {
                Toast.makeText(context, "已获得存储权限", Toast.LENGTH_SHORT).show()
            } else {
                XXPermissions.with(context)
                    .permission(Permission.Group.STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                adapter.setNewData(allBackup())
                                Toast.makeText(context, "已获得存储权限", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                Toast.makeText(context, "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show()
                                val act = context as? Activity
                                if (act != null) {
                                    XXPermissions.startPermissionActivity(act, permissions)
                                }
                            } else {
                                Toast.makeText(context, "获取存储权限失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
            }
        }
    }

    internal fun allBackup(): List<String> {
        val result = ArrayList<String>()
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val file = File("$root/tvbox_backup/")
            val list = file.listFiles()
            if (list != null) {
                Arrays.sort(list) { o1, o2 ->
                    if (o1.isDirectory && o2.isFile) -1
                    else if (o1.isFile && o2.isDirectory) 1
                    else o2.name.compareTo(o1.name)
                }
                if (file.exists()) {
                    for (f in list) {
                        if (result.size > 10) {
                            FileUtils.recursiveDelete(f)
                            continue
                        }
                        if (f.isDirectory) {
                            result.add(f.name)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return result
    }

    internal fun restore(dir: String) {
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val backup = File("$root/tvbox_backup/$dir")
            if (backup.exists()) {
                val db = File(backup, "sqlite")
                if (AppDataManager.restore(db)) {
                    val data = FileUtils.readSimple(File(backup, "hawk"))
                    if (data != null) {
                        val hawkJson = String(data, charset("UTF-8"))
                        val jsonObject = JSONObject(hawkJson)
                        val it = jsonObject.keys()
                        val sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE)
                        while (it.hasNext()) {
                            val key = it.next()
                            val value = jsonObject.getString(key)
                            if (key == "cipher_key") {
                                App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE)
                                    .edit().putString(key, value).commit()
                            } else {
                                sharedPreferences.edit().putString(key, value).commit()
                            }
                        }
                        Toast.makeText(context, "恢复成功,即将自动重启应用!", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            restartApp()
                        }, 3000)
                    } else {
                        Toast.makeText(context, "Hawk恢复失败!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "DB文件恢复失败!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun restartApp() {
        val ctx = context
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(i)
            System.exit(0)
        }
    }

    internal fun backup() {
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val file = File("$root/tvbox_backup/")
            if (!file.exists()) {
                file.mkdirs()
            }
            val now = Date()
            val f = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
            val backup = File(file, f.format(now))
            backup.mkdirs()
            val db = File(backup, "sqlite")
            if (AppDataManager.backup(db)) {
                var sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE)
                val jsonObject = JSONObject()
                for (key in sharedPreferences.all.keys) {
                    jsonObject.put(key, sharedPreferences.getString(key, ""))
                }
                sharedPreferences = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE)
                for (key in sharedPreferences.all.keys) {
                    jsonObject.put(key, sharedPreferences.getString(key, ""))
                }
                if (!FileUtils.writeSimple(jsonObject.toString().toByteArray(charset("UTF-8")), File(backup, "hawk"))) {
                    backup.delete()
                    Toast.makeText(context, "备份Hawk失败!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "备份成功!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "DB文件不存在!", Toast.LENGTH_SHORT).show()
                backup.delete()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "备份失败!", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun delete(dir: String) {
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val backup = File("$root/tvbox_backup/$dir")
            FileUtils.recursiveDelete(backup)
            Toast.makeText(context, "删除成功!", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
