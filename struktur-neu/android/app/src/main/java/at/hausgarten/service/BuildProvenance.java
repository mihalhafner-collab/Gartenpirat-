package at.hausgarten.service;
import org.json.*;
final class BuildProvenance {
 static JSONObject describe()throws Exception{return new JSONObject().put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE).put("git_sha",BuildConfig.GIT_SHA).put("source_sha256",BuildConfig.SOURCE_SHA256).put("source_dirty",BuildConfig.SOURCE_DIRTY).put("build_type",BuildConfig.BUILD_TYPE).put("mcp_protocol",PixelMcpReadTools.PROTOCOL_VERSION);}
 private BuildProvenance(){}
}
