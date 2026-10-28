package at.hausgarten.service;
import java.util.*;
/** Safe stage codes for local backup capture. No scheduling or remote targets. */
final class BackupScheduleRules {
    static final int MAX_ATTEMPTS=8; // Validate historical journal counters; no automatic retries.
    static Exception captureFailure(String stage,Exception failure){
        if(failure instanceof java.io.InterruptedIOException||Thread.currentThread().isInterrupted())return failure;
        String code=failure.getMessage();if(code!=null&&code.matches("(?:BACKUP|RECOVERY)_[A-Z0-9_]{1,90}"))return failure;
        if(!Arrays.asList("READINESS","MATERIALIZE","PIN_FILES","PREFERENCES","DATABASE","BROWSER","FILES","INVENTORY").contains(stage))stage="UNKNOWN";
        String category=failure.getClass().getName().startsWith("android.database.")?"DATABASE":failure instanceof java.io.IOException?"IO":failure instanceof org.json.JSONException?"DATA":failure instanceof SecurityException?"PERMISSION":"STATE";
        return new java.io.IOException("BACKUP_CAPTURE_"+stage+"_"+category+"_FAILED",failure);
    }
}
