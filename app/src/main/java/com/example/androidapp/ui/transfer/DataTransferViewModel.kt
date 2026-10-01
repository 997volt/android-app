package com.example.androidapp.ui.transfer

import androidx.lifecycle.ViewModel
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.repository.BackupRepository
import com.example.androidapp.domain.repository.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What an import did, or why it could not be done. */
sealed interface ImportOutcome {
    data class Imported(val summary: ImportSummary) : ImportOutcome
    data class Failed(val error: DataError) : ImportOutcome
}

/**
 * Backup and restore for the library screen's data menu (ROADMAP P1.12).
 *
 * Deliberately holds no state. The file *is* the state, and the screen already
 * owns somewhere to say what happened (its snackbar), so a `StateFlow` here would
 * only add a second place for a message to live.
 *
 * No file IO either: reading and writing go through the Storage Access Framework,
 * which needs a `Context`. Keeping that in the composable means this stays
 * testable on the JVM and the ViewModel has no idea a filesystem exists.
 */
@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    /** The JSON to write, or null if it could not be produced. */
    suspend fun export(): ExportOutcome = when (val result = backupRepository.export()) {
        is DataResult.Success -> ExportOutcome.Ready(result.data)
        is DataResult.Failure -> ExportOutcome.Failed(result.error)
    }

    /**
     * Deletes everything the user made (ROADMAP N18).
     *
     * Irreversible, which is why the screen asks for a typed confirmation before calling
     * it: "Delete everything" is a button someone presses by accident exactly once.
     */
    suspend fun clearEverything(): ClearOutcome =
        when (val result = backupRepository.clearAllUserData()) {
            is DataResult.Success -> ClearOutcome.Cleared
            is DataResult.Failure -> ClearOutcome.Failed(result.error)
        }

    /** Merges an imported file. Additive, so it can never cost the user data. */
    suspend fun import(text: String): ImportOutcome = when (val result = backupRepository.import(text)) {
        is DataResult.Success -> ImportOutcome.Imported(result.data)
        is DataResult.Failure -> ImportOutcome.Failed(result.error)
    }
}

/** The backup document, or the reason there isn't one. */
sealed interface ExportOutcome {
    data class Ready(val json: String) : ExportOutcome
    data class Failed(val error: DataError) : ExportOutcome
}

/** What came of the one irreversible action. */
sealed interface ClearOutcome {
    data object Cleared : ClearOutcome

    data class Failed(val error: DataError) : ClearOutcome
}
