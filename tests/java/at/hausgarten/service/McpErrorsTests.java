package at.hausgarten.service;
import org.json.*;

public final class McpErrorsTests {
    static void yes(boolean value) { if (!value) throw new AssertionError("error boundary"); }
    public static void main(String[] args) throws Exception {
        SyntheticBusinessData.verify();
        for (String code : new String[]{"WRITE_CONFLICT", "REFERENCE_NOT_FOUND", "WRITE_DISABLED", "PLATFORM_INTERACTION_REQUIRED"}) {
            Exception rejected = new PixelMcpWriteTools.Rejected(code, "QA domain failure");
            yes(McpErrors.rpc(rejected).getJSONObject("error").getString("code").equals(code));
            yes(McpErrors.bridge(rejected).getJSONObject("error").getString("code").equals(code));
        }
        Exception hidden = new java.io.IOException("qa_private_failure_must_not_escape");
        yes(!McpErrors.rpc(hidden).toString().contains(hidden.getMessage()));
        yes(!McpErrors.bridge(hidden).toString().contains(hidden.getMessage()));
        yes(McpErrors.rpc(new IllegalArgumentException("QA invalid")).getJSONObject("error").getString("code").equals("INVALID_INPUT"));
        yes(McpErrors.bridge(new IllegalArgumentException("QA invalid")).getJSONObject("error").getString("code").equals("VALIDATION_ERROR"));
        JSONObject provider=McpErrors.rpc(new HttpTransport.Failure("TEMPORARY_UNAVAILABLE","QA retry",7200000,false)).getJSONObject("error");
        yes(provider.getLong("retry_after_ms")==3600000 && !provider.getBoolean("user_action_required"));
        yes(provider.getString("code").equals("TEMPORARY_UNAVAILABLE"));
        for(String code:new String[]{"BACKUP_BROWSER_EDITOR_STATE_MISMATCH","BACKUP_JOB_WRITE_CONFLICT","BACKUP_STORAGE_RESERVE_REQUIRED","RECOVERY_NOT_CONFIGURED","REVIER_SCHEMA_UPGRADE_REQUIRED","INVOICE_CURRENT_LEDGER_INCOMPLETE","INVOICE_MATERIAL_SOURCE_NOT_AVAILABLE","LOCAL_MIGRATION_REQUIRED_AFTER_RESTORE","FINANCIAL_ORIGINAL_HASH_MISMATCH","LOCAL_AUTHORITY_CURRENT_EVIDENCE_REQUIRED","LOCAL_AUTHORITY_RECEIPT_STALE_AFTER_RESTORE","INVOICE_OPENING_BALANCE_CORRECTION_REQUIRED","INVOICE_RECONCILIATION_REVIEW_REQUIRED","LEGACY_SYNC_WRITER_DISABLED","LEGACY_SYNC_RETIRED","LOCAL_BOOTSTRAP_INVALID","LOCAL_ORIGINAL_MISSING","LOCAL_STATE_BUILD_FAILED","INVOICE_REVIEW_CONTEXT_REQUIRED","INVOICE_ORIGINAL_RECONCILIATION_REQUIRED","INVOICE_PDF_NOT_CREATED_REVIEW_REQUIRED","FINANCIAL_JOB_ORIGIN_INVALID","FINANCIAL_JOB_AUTHORIZATION_INVALID","FINANCIAL_JOB_ADOPTION_INVALID","FINANCIAL_RECOVERY_AUDIT_INVALID"}){
            JSONObject rpc=McpErrors.rpc(new java.io.IOException(code)).getJSONObject("error"),bridge=McpErrors.bridge(new java.io.IOException(code)).getJSONObject("error");
            yes(rpc.getString("code").equals(code)&&bridge.getString("code").equals(code));yes(rpc.getBoolean("user_action_required"));
        }
        for(String code:new String[]{"LOCAL_AUTHORITY_QA_PRIVATE_CUSTOMER","FINANCIAL_ORIGINAL_QA_PRIVATE_ADDRESS"}){Exception privateText=new java.io.IOException(code);yes(McpErrors.rpc(privateText).getJSONObject("error").getString("code").equals("READ_ERROR"));yes(!McpErrors.rpc(privateText).toString().contains(code));}
        yes(McpErrors.rpc(new java.io.IOException("BACKUP_BROWSER_EDITOR_STATE_MISMATCH")).getJSONObject("error").getString("message").contains("Zur App zurückkehren"));
        Exception unknownLocal=new java.io.IOException("BACKUP_QA_PRIVATE_CLIENT");
        yes(McpErrors.rpc(unknownLocal).getJSONObject("error").getString("code").equals("READ_ERROR"));
        yes(!McpErrors.rpc(unknownLocal).toString().contains(unknownLocal.getMessage()));
        System.out.println("PASS compatible domain/provider/transport errors, reviewed local codes and unknown-message redaction assertions");
    }
}
