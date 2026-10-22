package at.hausgarten.service;
import java.io.IOException;
/** Stable non-personal IO diagnosis. Provider messages/URIs never enter public status. */
final class SyncIoFailure extends IOException {
    final String code;
    SyncIoFailure(String code){super(SyncPolicy.message(code));this.code=code;}
    SyncIoFailure(String code,Exception cause){super(SyncPolicy.message(code),cause);this.code=code;}
    boolean needsAction(){return code.equals("REMOTE_MASTER_MISSING")||code.equals("REMOTE_DUPLICATES_DIFFER")||code.equals("REMOTE_FILE_TOO_LARGE");}
}
