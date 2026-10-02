package com.example.androidapp.ui.transfer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.example.androidapp.R
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.repository.ImportSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The two actions the data menu offers, ready to invoke from `onClick`. */
class DataTransferActions(
    val export: () -> Unit,
    val import: () -> Unit,
)

/**
 * Wires the data menu to the Storage Access Framework (ROADMAP P1.12).
 *
 * SAF rather than a fixed path or `WRITE_EXTERNAL_STORAGE`: the user picks where
 * the file goes, and the app declares **no permissions at all** (ROADMAP N26) — so there
 * is no storage permission to ask for and nothing else to ask for either. It also means the backup can live
 * somewhere that survives uninstalling the app, which is the entire point.
 *
 * The file IO lives here, in the composable, because it needs a `Context` and a
 * user-chosen `Uri`; the ViewModel only ever sees strings.
 */
@Composable
fun rememberDataTransferActions(
    viewModel: DataTransferViewModel,
    onMessage: (String) -> Unit,
): DataTransferActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Resolved during composition, so the callbacks below can use them without
    // needing a composable context of their own.
    val messages = rememberTransferMessages()

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // user backed out
        scope.launch { writeBackup(context, uri, viewModel, messages, onMessage) }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { readBackup(context, uri, viewModel, messages, onMessage) }
    }

    return remember(createDocument, openDocument) {
        DataTransferActions(
            export = { createDocument.launch(DEFAULT_FILE_NAME) },
            import = { openDocument.launch(arrayOf(JSON_MIME_TYPE, "text/plain")) },
        )
    }
}

private suspend fun writeBackup(
    context: Context,
    uri: Uri,
    viewModel: DataTransferViewModel,
    messages: TransferMessages,
    onMessage: (String) -> Unit,
) {
    when (val outcome = viewModel.export()) {
        is ExportOutcome.Ready -> {
            // Rethrowing CancellationException is the reason this is not `runCatching`,
            // which catches Throwable and so swallows it too. See the note on
            // `DataResult.dataResultOf`: this coroutine belongs to the composable's
            // scope, so a cancelled write means "this screen is gone", not "the file
            // could not be written" — reporting the latter would be a lie, and the
            // coroutine would refuse to finish cancelling.
            val written = try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(outcome.json.toByteArray())
                    true
                } ?: error("openOutputStream returned null for $uri")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                false
            }
            onMessage(if (written) messages.exported else messages.exportFailed)
        }

        is ExportOutcome.Failed -> onMessage(messages.forError(outcome.error))
    }
}

private suspend fun readBackup(
    context: Context,
    uri: Uri,
    viewModel: DataTransferViewModel,
    messages: TransferMessages,
    onMessage: (String) -> Unit,
) {
    val text = try {
        // `null` here is a readable file that is simply empty, which is a failed read
        // rather than a failure — the same message either way.
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    } catch (cancellation: CancellationException) {
        // Not a failure, for the reason spelled out in `writeBackup`. Without this the
        // cancellation would surface as "couldn't read that file".
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    if (text == null) {
        onMessage(messages.readFailed)
        return
    }

    when (val outcome = viewModel.import(text)) {
        is ImportOutcome.Imported -> onMessage(messages.imported(outcome.summary))
        is ImportOutcome.Failed -> onMessage(messages.forError(outcome.error))
    }
}

/** The strings the transfer callbacks need, resolved once during composition. */
class TransferMessages(
    val exported: String,
    val exportFailed: String,
    val readFailed: String,
    /** Resolves the plural for a count; a plural resource needs the quantity. */
    private val importedFormat: (Int) -> String,
    private val alreadyComplete: String,
    private val storageError: String,
) {
    fun imported(summary: ImportSummary): String = if (summary.wasAlreadyComplete) {
        alreadyComplete
    } else {
        importedFormat(summary.total)
    }

    fun forError(error: DataError): String = when (error) {
        // Already written for the user by the layer that rejected the file.
        is DataError.Invalid -> error.message
        DataError.NotFound -> storageError
        is DataError.Storage -> storageError
    }
}

@Composable
private fun rememberTransferMessages(): TransferMessages {
    // LocalResources rather than LocalContext.current.resources: the latter is not
    // invalidated by a Configuration change, so a locale switch would leave these
    // strings stale. The plural also needs the count, which is why this is a lambda
    // rather than a `stringResource` call.
    val resources = LocalResources.current

    val exported = stringResource(R.string.transfer_exported)
    val exportFailed = stringResource(R.string.transfer_export_failed)
    val readFailed = stringResource(R.string.transfer_read_failed)
    val alreadyComplete = stringResource(R.string.transfer_already_complete)
    val storageError = stringResource(R.string.transfer_storage_error)

    return remember(exported, exportFailed, readFailed, alreadyComplete, storageError) {
        TransferMessages(
            exported = exported,
            exportFailed = exportFailed,
            readFailed = readFailed,
            importedFormat = { count ->
                resources.getQuantityString(R.plurals.transfer_imported, count, count)
            },
            alreadyComplete = alreadyComplete,
            storageError = storageError,
        )
    }
}

private const val JSON_MIME_TYPE = "application/json"
private const val DEFAULT_FILE_NAME = "workout-backup.json"
