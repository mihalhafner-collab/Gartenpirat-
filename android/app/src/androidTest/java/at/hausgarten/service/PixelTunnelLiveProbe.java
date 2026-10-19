package at.hausgarten.service;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;

/** Explicit USB diagnostic, test APK only. The key stays inside the phone. */
final class PixelTunnelLiveProbe {
    static void run(Instrumentation runner, Context context) {
        Bundle result = new Bundle();
        JSONObject report = SyntheticBusinessData.object();
        MainActivity.LocalStore store = null;
        PixelTunnelWire.HttpTransport http = null;
        try {
            report.put("business_writes", false);
            if (PixelTunnelService.status(context).optBoolean("running"))
                throw new IllegalStateException("Vorhandenen Pixel-Dienst zuerst beenden; keine konkurrierenden Tunnel-Clients.");
            PixelTunnelConfig config = new PixelTunnelConfig(context);
            if (!config.configured()) throw new IllegalStateException("Runtime-Schlüssel ist nicht eingerichtet.");
            if (context.getSharedPreferences("haus_garten", 0).getString("drive_snapshot", "").isEmpty())
                throw new IllegalStateException("Kein tatsächlicher Drive-Datenstand vorhanden.");
            report.put("expected_pixel_id", config.configured() && !config.id().isEmpty());
            store = new MainActivity.LocalStore(context);
            final MainActivity.LocalStore source = store;
            http = new PixelTunnelWire.HttpTransport(config.secret(), config.organization());
            final PixelTunnelWire.HttpTransport transport = http;
            // Read-only authentication cross-check; model names are neither used nor reported.
            report.put("models_auth_http", inspect(config.secret(), "/v1/models", report, "models_auth", config.organization()).code);
            // The metadata endpoint and the runtime polling endpoint are checked separately.
            PixelTunnelWire.Reply metadata = inspect(config.secret(), "/v1/tunnels/" + config.id(), report, "metadata", config.organization());
            report.put("metadata_http", metadata.code);
            // This path is present in the official bundled client. Never print response values:
            // a successful bootstrap may contain short-lived runtime credentials.
            PixelTunnelWire.Reply bootstrap = inspect(config.secret(), "/v1/tunnels/" + config.id() + "/cloudflare/runtime", report, "bootstrap", config.organization());
            report.put("official_bootstrap_http", bootstrap.code);
            org.json.JSONArray fields = new org.json.JSONArray();
            java.util.Iterator<String> names = bootstrap.json.keys();
            while(names.hasNext() && fields.length() < 30) {
                String name = names.next();
                if(name.matches("[a-zA-Z_][a-zA-Z0-9_]{0,60}")) fields.put(name);
            }
            report.put("bootstrap_field_names", fields);
            // Read-only comparison against the already existing tunnel-owner organization.
            report.put("explicit_org_metadata_http", inspect(config.secret(), "/v1/tunnels/" + config.id(), report, "explicit_org_metadata", config.organization()).code);
            PixelTunnelWire wire = new PixelTunnelWire(config.id(), (method, path, body, shard, timeout) -> {
                PixelTunnelWire.Reply reply = transport.request(method, path, body, shard, timeout);
                report.put(path.contains("/poll") ? "poll_http" : "response_http", reply.code);
                return reply;
            }, new PixelMcpReadTools(source::getState));
            try {
                report.put("answered_commands", wire.cycle());
                report.put("runtime_connected", true);
            } catch (PixelTunnelWire.AccessError denied) {
                report.put("runtime_connected", false).put("auth_http", denied.httpCode).put("access_reason", denied.reason);
            } catch (Exception failure) {
                report.put("runtime_connected", false).put("failure_type", failure.getClass().getSimpleName());
            }
            result.putString("stream", "LIVE_TUNNEL_PROBE " + report + "\n");
            runner.finish(-1, result); // Diagnostic completed, not an assertion of connectivity.
        } catch (Exception failure) {
            result.putString("stream", "LIVE_TUNNEL_PROBE_BLOCKED " + failure.getClass().getSimpleName() + "\n");
            runner.finish(0, result);
        } finally {
            if (http != null) http.close();
            if (store != null) store.close();
        }
    }

    /** Only known error categories leave the phone; never provider messages or response values. */
    private static PixelTunnelWire.Reply inspect(String secret, String path, JSONObject report, String phase, String organization) throws Exception {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
            new java.net.URL("https://api.openai.com" + path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + secret);
            connection.setRequestProperty("Accept", "application/json");
            if (organization != null) connection.setRequestProperty("OpenAI-Organization", organization);
            connection.setRequestProperty("X-Tunnel-Client-Name", "hausgarten-android");
            connection.setRequestProperty("X-Tunnel-Client-Version", "0.1.0");
            connection.setRequestProperty("X-Tunnel-Client-Instance-Id", java.util.UUID.randomUUID().toString());
            connection.setRequestProperty("X-Tunnel-MCP-Server-Info", "{\"version\":1,\"channels\":[{\"name\":\"main\",\"proc_affinity\":true}]}");
            int code = connection.getResponseCode();
            java.io.InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            JSONObject json = SyntheticBusinessData.object();
            if (input != null) try (java.io.InputStream in = input; java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count; if (total > 65536) throw new java.io.IOException("Diagnostic response too large");
                    bytes.write(buffer, 0, count);
                }
                try { json = SyntheticBusinessData.object(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)); }
                catch (org.json.JSONException ignored) { report.put(phase + "_error_category", "non_json_response"); }
            }
            if (code < 200 || code >= 300) {
                JSONObject error = json.optJSONObject("error");
                String value = error == null ? "" : error.optString("code");
                String message = (error == null ? "" : error.optString("message")) + " " + json.optString("detail") + " " + json.optString("message");
                if (json.opt("error") instanceof String) message += " " + json.optString("error");
                message = message.toLowerCase(java.util.Locale.ROOT);
                String category = "unclassified";
                if ("invalid_api_key".equals(value) || message.contains("incorrect api key") || message.contains("invalid api key")) category = "invalid_api_key";
                else if ("insufficient_permissions".equals(value) || message.contains("missing scopes") || message.contains("insufficient permissions")) category = "insufficient_permissions";
                else if ("organization_restricted".equals(value)) category = "organization_restricted";
                else if ("invalid_organization".equals(value) || message.contains("organization mismatch")) category = "organization_mismatch";
                else if (message.contains("not authorized") || message.contains("unauthorized")) category = "unauthorized";
                else if (message.contains("permission") || message.contains("scope")) category = "permission_related";
                report.put(phase + "_error_category", category);
                report.put(phase + "_mentions_tunnel_read", message.contains("api.tunnel.read") || message.contains("api.tunnels.read"));
                report.put(phase + "_mentions_tunnel_use", message.contains("api.tunnel.use") || message.contains("api.tunnels.use"));
                report.put(phase + "_mentions_tunnel_manage", message.contains("api.tunnel.manage") || message.contains("api.tunnels.manage"));
                // Redact inside the phone before any text can leave it. Never return the body.
                String safeMessage = message.replace(secret, "[credential removed]")
                    .replaceAll("(?i)sk-[a-z0-9_-]+", "[credential removed]")
                    .replaceAll("(?i)bearer\\s+[a-z0-9._-]+", "[credential removed]")
                    .replaceAll("(?i)(?:org-|proj_|tunnel_|key_)[a-z0-9_-]+", "[identifier removed]")
                    .replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[email removed]")
                    .replaceAll("[\\r\\n\\t]+", " ").trim();
                report.put(phase + "_redacted_reason", safeMessage.length() > 500 ? safeMessage.substring(0, 500) : safeMessage);
                return new PixelTunnelWire.Reply(code, SyntheticBusinessData.object());
            }
            return new PixelTunnelWire.Reply(code, json);
        } finally { connection.disconnect(); }
    }
}
