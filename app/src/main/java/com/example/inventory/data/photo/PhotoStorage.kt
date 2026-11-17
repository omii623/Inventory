package com.example.inventory.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tételfotók fájljai az app belső tárhelyén (files/photos/). Az adatbázis csak a fájlnevet tárolja (TERV.md 3.5).
 * - A kiválasztott / lefényképezett képet átmásolja és lekicsinyíti (leghosszabb oldal [MAX_SIDE] px, JPEG),
 *   az EXIF-forgatást alkalmazva – így az eredeti törlése után is megmarad, és nem foglal feleslegesen helyet.
 * - A kamera ideiglenes fájlja a cache/camera/ mappába kerül (FileProvider-en át kapja meg a kamera app).
 */
class PhotoStorage(context: Context) {

    private val appContext = context.applicationContext

    val directory: File = File(appContext.filesDir, DIR).apply { mkdirs() }
    private val cameraDir: File = File(appContext.cacheDir, CAMERA_DIR).apply { mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    /** Fotó beolvasása egy content:// vagy file URI-ról a saját tárhelyre. @return a fájlnév, vagy null hibánál. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // inJustDecodeBounds mellett a decodeStream mindig null-t ad – csak a méretet tölti ki.
            val stream = resolver.openInputStream(uri) ?: return@runCatching null
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val bitmap = scaleAndRotate(decoded, MAX_SIDE, orientation)

            val name = "${UUID.randomUUID()}.jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (bitmap !== decoded) decoded.recycle()
            bitmap.recycle()
            name
        }.onFailure { Log.w(TAG, "Fotó beolvasása sikertelen: $uri", it) }.getOrNull()
            .also { if (it == null) Log.w(TAG, "Fotó beolvasása: nincs eredmény ($uri)") }
    }

    /** Új ideiglenes fájl a kamerának, és a hozzá tartozó (FileProvider) URI. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(cameraDir, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    /** Fájlok törlése név szerint. A hiányzó fájl nem hiba. */
    suspend fun delete(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { name -> file(name).takeIf { it.exists() }?.delete() }
    }

    /**
     * Elárvult fájlok takarítása (pl. ha az app egy félbehagyott szerkesztés közben leállt):
     * a nem hivatkozott, [minAgeMs]-nél régebbi fotók és a kamera minden ideiglenes fájlja törlődik.
     */
    suspend fun cleanup(referenced: Set<String>, minAgeMs: Long = 24L * 60 * 60 * 1000) = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - minAgeMs
        directory.listFiles()?.filter { it.name !in referenced && it.lastModified() < limit }?.forEach { it.delete() }
        cameraDir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    /** Kép betöltése megjelenítéshez, legfeljebb [maxSide] px-re kicsinyítve. */
    fun loadBitmap(fileName: String, maxSide: Int): Bitmap? = runCatching {
        val f = file(fileName)
        if (!f.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        BitmapFactory.decodeFile(f.path, options)
    }.getOrNull()

    companion object {
        const val DIR = "photos"
        private const val TAG = "PhotoStorage"
        private const val CAMERA_DIR = "camera"

        /** Tételenként legfeljebb ennyi fotó lehet (TERV.md 3.5). */
        const val MAX_PHOTOS = 10

        /** A tárolt kép leghosszabb oldala (px). */
        const val MAX_SIDE = 2048
        private const val JPEG_QUALITY = 85

        /** A legnagyobb 2-hatvány, amivel osztva a kép még legalább [target] px marad (a pontos méretre később skálázunk). */
        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= target) sample *= 2
            return sample
        }

        private fun scaleAndRotate(source: Bitmap, maxSide: Int, orientation: Int): Bitmap {
            val matrix = Matrix()
            val longest = max(source.width, source.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                matrix.postScale(scale, scale)
            }
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            if (matrix.isIdentity) return source
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }
}
