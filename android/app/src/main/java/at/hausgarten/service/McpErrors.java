package at.hausgarten.service;

import org.json.JSONObject;

/** One error boundary. Existing transport-specific codes/messages remain compatible. */
final class McpErrors {
    private McpErrors() {}

    private static String code(Exception error, String fallback) {
        return error instanceof PixelMcpWriteTools.Rejected
            ? ((PixelMcpWriteTools.Rejected) error).code : fallback;
    }

    static JSONObject rpc(Exception error) throws Exception {
        JSONObject problem = new JSONObject()
            .put("code", code(error, error instanceof DriveRestClient.Failure
                ? ((DriveRestClient.Failure) error).code
                : error instanceof IllegalArgumentException ? "INVALID_INPUT" : "READ_ERROR"))
            .put("message", error instanceof IllegalArgumentException || error instanceof DriveRestClient.Failure
                ? error.getMessage()
                : "Anfrage nicht möglich. Pflichtangaben, Datensatz-ID oder gespeicherten Drive-Stand prüfen.");
        if (error instanceof DriveRestClient.Failure) {
            DriveRestClient.Failure failure = (DriveRestClient.Failure) error;
            problem.put("retry_after_ms", Math.max(0, Math.min(3600000, failure.retryAfter)))
                .put("user_action_required", failure.userAction);
        }
        return envelope(problem);
    }

    static JSONObject bridge(Exception error) throws Exception {
        return envelope(new JSONObject().put("code", code(error, "VALIDATION_ERROR"))
            .put("message", error instanceof IllegalArgumentException ? error.getMessage()
                : "Vorgang nicht abgeschlossen. Sicherung, Versionen und Berechtigungen prüfen."));
    }

    private static JSONObject envelope(JSONObject problem) throws Exception {
        return new JSONObject().put("ok", false).put("error", problem);
    }
}
