package at.hausgarten.service;
import java.util.*;
/** Safe stage codes for local backup capture. No scheduling or remote targets. */
final class BackupScheduleRules {
    /** Only bounded machine codes, never exception prose, paths or business values. */
    static String errorCode(Throwable failure){
        String code=failure==null?null:failure.getMessage();
        return code!=null&&code.matches("(?:BACKUP|RESTORE|RECOVERY|DOCUMENT|BROWSER_MEDIA|FINANCIAL)_[A-Z0-9_]{1,100}")?code:"BACKUP_OPERATION_FAILED";
    }
    static Exception captureFailure(String stage,Exception failure){
        if(failure instanceof java.io.InterruptedIOException||Thread.currentThread().isInterrupted())return failure;
        if(!errorCode(failure).equals("BACKUP_OPERATION_FAILED"))return failure;
        if(!Arrays.asList("READINESS","MATERIALIZE","PIN_FILES","PREFERENCES","DATABASE","BROWSER","FILES","INVENTORY","INVENTORY_STATE","INVENTORY_BUSINESS","INVENTORY_RELATIONS","INVENTORY_FINANCIAL","INVENTORY_ORIGINALS","INVENTORY_BROWSER_MEDIA","INVENTORY_NUMBERS","INVENTORY_METADATA").contains(stage))stage="UNKNOWN";
        String category=failure.getClass().getName().startsWith("android.database.")?"DATABASE":failure instanceof java.io.IOException?"IO":failure instanceof org.json.JSONException?"DATA":failure instanceof SecurityException?"PERMISSION":"STATE";
        return new java.io.IOException("BACKUP_CAPTURE_"+stage+"_"+category+"_FAILED",failure);
    }
}
