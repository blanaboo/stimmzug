package io.github.blanaboo.stimmzug.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.blanaboo.stimmzug.R
import java.io.File

/**
 * Opens the system share sheet for the CSV export of the session log (#37). The file provider hands the file out
 * read-only to the app the player picks; the app itself sends nothing anywhere.
 */
fun shareLog(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.log_share_title)))
}
