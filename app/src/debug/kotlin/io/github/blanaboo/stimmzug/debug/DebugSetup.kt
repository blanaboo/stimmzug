package io.github.blanaboo.stimmzug.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import io.github.blanaboo.stimmzug.BuildConfig
import io.github.blanaboo.stimmzug.practice.PracticeInputs
import io.github.blanaboo.stimmzug.practice.PracticeRecorder
import java.io.File

/**
 * Installs the debug-only hooks when the app process starts (a provider runs before any activity): every practice
 * session is recorded (#75). Serves no data.
 */
class DebugSetup : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        val root = context.getExternalFilesDir(PracticeRecorder.FOLDER)
            ?: File(context.filesDir, PracticeRecorder.FOLDER)
        PracticeInputs.tap = PracticeRecorder(
            root,
            header = listOf(
                "Stimmzug Übungs-Mitschnitt (#75), App ${BuildConfig.VERSION_NAME}",
                "Gerät: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0
}
