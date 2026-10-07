package com.itravel.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份 / 恢复 管理器
 * 将数据库 + 照片打包为 zip（itravel-backup-yyyyMMdd-HHmmss.zip）导出到 Downloads，
 * 导入时从 zip 还原数据（JSON + 照片文件）。
 */
object BackupManager {

    private const val BACKUP_JSON = "backup.json"
    private const val PHOTOS_DIR = "images"

    /**
     * 导出备份到 Downloads 目录，返回导出后的 zip File。
     */
    suspend fun export(context: Context, places: List<PlaceWithPhotos>): File? = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(context)
            val dao = db.placeDao()
            val tmpDir = File(context.cacheDir, "backup_tmp").apply {
                deleteRecursively()
                mkdirs()
            }

            // 1. 复制照片到临时目录
            val imgDir = File(tmpDir, PHOTOS_DIR)
            val photoMap = mutableMapOf<String, String>() // oldPath -> newName
            var idx = 0
            places.forEach { item ->
                item.photos.forEach { photo ->
                    val src = File(photo.path)
                    if (src.exists()) {
                        val ext = src.extension.ifBlank { "jpg" }
                        val newName = "${idx++}.$ext"
                        src.copyTo(File(imgDir, newName), overwrite = true)
                        photoMap[photo.path] = "$PHOTOS_DIR/$newName"
                    }
                }
            }

            // 2. 写入 JSON
            val root = JSONObject()
            val placesArr = JSONArray()
            places.forEach { item ->
                val p = item.place
                val pObj = JSONObject().apply {
                    put("title", p.title)
                    put("placeName", p.placeName ?: "")
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("notes", p.notes ?: "")
                    put("visitDate", p.visitDate)
                    put("coverPath", photoMap[p.coverPath] ?: "")
                    val photosArr = JSONArray()
                    item.photos.forEach { photo ->
                        val mapped = photoMap[photo.path]
                        if (mapped != null) {
                            photosArr.put(JSONObject().put("path", mapped).put("createdAt", photo.createdAt))
                        }
                    }
                    put("photos", photosArr)
                }
                placesArr.put(pObj)
            }
            root.put("places", placesArr)
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())

            File(tmpDir, BACKUP_JSON).bufferedWriter().use { it.write(root.toString(2)) }

            // 3. 打包为 zip
            val downloads = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(downloads, "itravel-backup-$stamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                tmpDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(tmpDir).path.replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            tmpDir.deleteRecursively()
            zipFile
        }.getOrNull()
    }

    /**
     * 从 uri（zip 文件）导入备份。
     * 导入前清空现有数据（places + photos）。
     */
    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmpDir = File(context.cacheDir, "restore_tmp").apply {
                deleteRecursively()
                mkdirs()
            }
            // 解压
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        entry ?: continue
                        val outFile = File(tmpDir, entry!!.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                }
            } ?: return@withContext false

            val jsonFile = File(tmpDir, BACKUP_JSON)
            if (!jsonFile.exists()) {
                tmpDir.deleteRecursively()
                return@withContext false
            }

            val jsonStr = jsonFile.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val placesArr = root.getJSONArray("places")

            val db = AppDatabase.get(context)
            val dao = db.placeDao()

            // 清空现有数据
            db.close()
            val dbFile = context.getDatabasePath("itravel.db")
            dbFile.delete()
            val imagesDir = PhotoStorage.imagesDir(context)
            imagesDir.listFiles()?.forEach { it.delete() }

            val newDb = AppDatabase.get(context)
            val newDao = newDb.placeDao()

            for (i in 0 until placesArr.length()) {
                val pObj = placesArr.getJSONObject(i)
                val placeId = newDao.insert(
                    Place(
                        title = pObj.getString("title"),
                        placeName = pObj.optString("placeName").takeIf { it.isNotBlank() },
                        latitude = pObj.getDouble("latitude"),
                        longitude = pObj.getDouble("longitude"),
                        notes = pObj.optString("notes").takeIf { it.isNotBlank() },
                        visitDate = pObj.getLong("visitDate"),
                        coverPath = null // 稍后更新
                    )
                )

                val photosArr = pObj.getJSONArray("photos")
                val photoPaths = mutableListOf<String>()
                for (j in 0 until photosArr.length()) {
                    val phObj = photosArr.getJSONObject(j)
                    val relPath = phObj.getString("path")
                    val src = File(tmpDir, relPath)
                    if (src.exists()) {
                        val dst = PhotoStorage.newImageFile(context)
                        src.copyTo(dst, overwrite = true)
                        photoPaths.add(dst.absolutePath)
                    }
                }

                if (photoPaths.isNotEmpty()) {
                    newDao.insertPhotos(photoPaths.map {
                        Photo(placeId = placeId, path = it, createdAt = System.currentTimeMillis())
                    })
                    // 更新 coverPath
                    newDao.update(
                        newDao.getById(placeId)!!.copy(coverPath = photoPaths.first())
                    )
                }
            }

            tmpDir.deleteRecursively()
            true
        }.getOrDefault(false)
    }

    /**
     * 分享导出的 zip 文件。
     */
    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享 iTravel 备份")
        )
    }
}
