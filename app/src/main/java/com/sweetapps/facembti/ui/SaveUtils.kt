package com.sweetapps.facembti.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SaveUtils {
    /** 갤러리에 PNG 저장. 성공 시 저장된 경로(또는 display name)를 리턴. */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String = "FaceMBTI_${System.currentTimeMillis()}.png"): String {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceMBTI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to insert MediaStore")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            displayName
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(dir, "FaceMBTI").apply { if (!exists()) mkdirs() }
            val file = File(targetDir, displayName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }
    }

    /** 캐시에 PNG 저장 후 파일 객체 반환(공유용). */
    fun saveBitmapToCachePng(context: Context, bitmap: Bitmap, fileName: String = "share_${System.currentTimeMillis()}.png"): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}

