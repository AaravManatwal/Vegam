package com.vegam.core.download

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * User ka chuna hua save folder (SAF tree uri). Abhi SharedPreferences; Phase 3C mein Settings/DataStore se judega.
 * null = app ka private folder (koi permission nahi).
 */
class SaveLocationStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("vegam_prefs", Context.MODE_PRIVATE)

    var treeUri: String?
        get() = prefs.getString(KEY, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
        }

    fun label(): String {
        val uri = treeUri ?: return "App folder (private)"
        return DocumentFile.fromTreeUri(app, Uri.parse(uri))?.name ?: "Chosen folder"
    }

    private companion object { const val KEY = "save_tree_uri" }
}

/**
 * Engine RandomAccessFile se likhta hai, jo SAF uri par seedha nahi chalta. Isliye file pehle app folder mein
 * download hoti hai, aur complete hone par chune hue folder mein copy hoti hai (copy ke dauran
 * file ke size jitni extra jagah lagti hai).
 */
object SafExporter {
    suspend fun export(context: Context, source: File, treeUri: Uri, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            if (tree == null || !tree.canWrite()) return@withContext false
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension.lowercase())
                ?: "application/octet-stream"
            val doc = tree.createFile(mime, displayName) ?: return@withContext false
            val out = context.contentResolver.openOutputStream(doc.uri) ?: return@withContext false
            out.use { o -> source.inputStream().use { it.copyTo(o, 64 * 1024) } }
            true
        }
}
