package at.hausgarten.service

import java.io.IOException

/** Stable non-personal IO diagnosis. Provider messages/URIs never enter public status. */
class SyncIoFailure : IOException {
    @JvmField val code: String
    constructor(code: String) : super(SyncPolicy.message(code)) {
        this.code = code
    }
    constructor(code: String, cause: Exception) : super(SyncPolicy.message(code), cause) {
        this.code = code
    }
    fun needsAction(): Boolean {
        return ((((((code.equals("FOLDER_NOT_SELECTED") || code.equals("DEVICE_ID_MISSING")) || code.equals("PUBLICATION_INVALID")) || code.equals("REMOTE_STATE_REFERENCE_MISSING")) || code.equals("REMOTE_MASTER_MISSING")) || code.equals("REMOTE_DUPLICATES_DIFFER")) || code.equals("REMOTE_FILE_TOO_LARGE"))
    }
}
