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
        JSONObject provider=McpErrors.rpc(new DriveRestClient.Failure("TEMPORARY_UNAVAILABLE","QA retry",7200000,false)).getJSONObject("error");
        yes(provider.getLong("retry_after_ms")==3600000 && !provider.getBoolean("user_action_required"));
        yes(provider.getString("code").equals("TEMPORARY_UNAVAILABLE"));
        System.out.println("PASS 14 compatible domain/provider/transport error and redaction assertions");
    }
}
