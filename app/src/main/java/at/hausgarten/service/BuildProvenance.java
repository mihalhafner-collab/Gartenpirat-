package at.hausgarten.service;
import org.json.*;
final class BuildProvenance {
 static String identityLabel(){return BuildConfig.APP_CHANNEL+" · "+BuildConfig.DISPLAY_VERSION+" · Build "+BuildConfig.BUILD_NUMBER;}
 static JSONObject describe()throws Exception{return new JSONObject().put("identity_label",identityLabel()).put("channel",BuildConfig.APP_CHANNEL).put("application_id",BuildConfig.APPLICATION_ID).put("build_number",BuildConfig.BUILD_NUMBER).put("display_version",BuildConfig.DISPLAY_VERSION).put("android_version_code",BuildConfig.VERSION_CODE).put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE).put("git_sha",BuildConfig.GIT_SHA).put("source_sha256",BuildConfig.SOURCE_SHA256).put("source_dirty",BuildConfig.SOURCE_DIRTY).put("build_type",BuildConfig.BUILD_TYPE).put("mcp_protocol",PixelMcpReadTools.PROTOCOL_VERSION);}
 private BuildProvenance(){}
}
