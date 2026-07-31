package com.enenhhh.smartschedule.util

import android.content.Context
import android.net.Uri
import com.enenhhh.smartschedule.data.ScheduleManager
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {
    private fun ts(): String = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault()).format(Date())

    private fun appExternalDir(ctx: Context): File {
        return ctx.getExternalFilesDir(null) ?: ctx.filesDir
    }

    private fun copyFileIfExists(src: File, dst: File) {
        if (!src.exists()) return
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
    }

    private fun copyDir(src: File, dst: File) {
        if (!src.exists()) return
        src.walkTopDown().forEach { f ->
            val rel = f.relativeTo(src)
            val out = File(dst, rel.path)
            if (f.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                f.copyTo(out, overwrite = true)
            }
        }
    }

    private fun deleteRecursivelySafe(file: File) {
        if (file.exists()) file.deleteRecursively()
    }

    fun exportToFolder(ctx: Context, folderUri: Uri): String {
        val cr = ctx.contentResolver
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, folderUri)
            ?: throw IllegalStateException("无法访问目标文件夹")
        val backupName = "backup_${ts()}"
        val backupDir = tree.createDirectory(backupName)
            ?: throw IllegalStateException("无法创建备份目录")

        val appDir = appExternalDir(ctx)
        val tmpRoot = File(ctx.cacheDir, backupName).apply {
            deleteRecursively()
            mkdirs()
        }

        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).all
        File(tmpRoot, "prefs.json").writeText(JSONObject(prefs).toString(2))

        copyFileIfExists(File(appDir, "homework.json"), File(tmpRoot, "homework.json"))
        copyDir(File(appDir, "plans"), File(tmpRoot, "plans"))

        tmpRoot.walkTopDown().forEach { f ->
            if (f.isFile) {
                val rel = f.relativeTo(tmpRoot).path
                val parts = rel.split("/").filter { it.isNotBlank() }
                var current = backupDir
                for (i in 0 until parts.lastIndex) {
                    current = current.findFile(parts[i]) ?: current.createDirectory(parts[i])
                    ?: throw IllegalStateException("无法创建目录 ${parts[i]}")
                }
                val target = current.findFile(parts.last()) ?: current.createFile("application/octet-stream", parts.last())
                ?: throw IllegalStateException("无法创建文件 ${parts.last()}")
                cr.openOutputStream(target.uri, "w")!!.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
            }
        }

        tmpRoot.deleteRecursively()
        return backupName
    }

    fun importFromFolder(ctx: Context, folderUri: Uri) {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, folderUri)
            ?: throw IllegalStateException("无法访问备份目录")

        val appDir = appExternalDir(ctx)
        val tmpRoot = File(ctx.cacheDir, "restore_tmp").apply {
            deleteRecursively()
            mkdirs()
        }

        fun copyDocDir(src: androidx.documentfile.provider.DocumentFile, dst: File) {
            src.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val d = File(dst, child.name ?: "dir")
                    d.mkdirs()
                    copyDocDir(child, d)
                } else if (child.isFile) {
                    val f = File(dst, child.name ?: "file")
                    f.parentFile?.mkdirs()
                    ctx.contentResolver.openInputStream(child.uri)!!.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        copyDocDir(doc, tmpRoot)

        val prefsFile = File(tmpRoot, "prefs.json")
        if (prefsFile.exists()) {
            val obj = JSONObject(prefsFile.readText())
            val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear()
            obj.keys().forEach { k ->
                when (val v = obj.get(k)) {
                    is Boolean -> sp.putBoolean(k, v)
                    is Int -> sp.putInt(k, v)
                    is Long -> sp.putLong(k, v)
                    is Float -> sp.putFloat(k, v)
                    is String -> sp.putString(k, v)
                    else -> sp.putString(k, v.toString())
                }
            }
            sp.apply()
        }

        copyFileIfExists(File(tmpRoot, "homework.json"), File(appDir, "homework.json"))

        deleteRecursivelySafe(File(appDir, "plans"))
        copyDir(File(tmpRoot, "plans"), File(appDir, "plans"))

        tmpRoot.deleteRecursively()
        // 磁盘上的方案已被整体替换，丢掉内存缓存
        ScheduleManager.invalidateCache()
    }

    /**
     * 导出配置为 JSON 对象（用于分享码上传）
     */
    fun exportToJson(ctx: Context): JSONObject? {
        return try {
            val appDir = appExternalDir(ctx)
            val result = JSONObject()

            // prefs
            val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).all
            result.put("prefs", JSONObject(prefs))

            val homeworkFile = File(appDir, "homework.json")
            if (homeworkFile.exists()) {
                result.put("homework", homeworkFile.readText())
            }

            // plans
            val plansDir = File(appDir, "plans")
            if (plansDir.exists()) {
                val plansObj = JSONObject()
                plansDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(plansDir).path
                    plansObj.put(rel, f.readText())
                }
                result.put("plans", plansObj)
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 JSON 对象导入配置（用于分享码下载）
     */
    fun importFromJson(ctx: Context, data: JSONObject) {
        val appDir = appExternalDir(ctx)

        // prefs
        if (data.has("prefs")) {
            val obj = data.getJSONObject("prefs")
            val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear()
            obj.keys().forEach { k ->
                when (val v = obj.get(k)) {
                    is Boolean -> sp.putBoolean(k, v)
                    is Int -> sp.putInt(k, v)
                    is Long -> sp.putLong(k, v)
                    is Float -> sp.putFloat(k, v)
                    is String -> sp.putString(k, v)
                    else -> sp.putString(k, v.toString())
                }
            }
            sp.apply()
        }

        // homework
        if (data.has("homework")) {
            File(appDir, "homework.json").writeText(data.getString("homework"))
        }

        // plans
        if (data.has("plans")) {
            val plansDir = File(appDir, "plans")
            plansDir.deleteRecursively()
            plansDir.mkdirs()
            val plansObj = data.getJSONObject("plans")
            plansObj.keys().forEach { rel ->
                val f = File(plansDir, rel)
                f.parentFile?.mkdirs()
                f.writeText(plansObj.getString(rel))
            }
        }

        ScheduleManager.invalidateCache()
    }
}
