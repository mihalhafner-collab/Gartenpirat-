package at.hausgarten.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;

/** Private credentials: Keystore key never leaves Android; no secret enters the WebView. */
public final class PixelTunnelConfig {
    private static final String ALIAS = "hausgarten_pixel_tunnel_v1";
    private static final String PREFS = "pixel_tunnel_private";
    private final SharedPreferences prefs;
    public PixelTunnelConfig(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static void validateId(String id) {
        if (!id.matches("tunnel_(?:[a-z0-9]{4}_)?[a-z0-9]{32}"))
            throw new IllegalArgumentException("Bitte die vollständige private Tunnel-ID eingeben.");
    }
    static void validateOrganization(String organization) {
        if (organization == null || (!organization.isEmpty() && !organization.matches("org-[a-zA-Z0-9]{4,64}")))
            throw new IllegalArgumentException("Bitte die OpenAI-Organisations-ID im Format org-… eingeben.");
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    public synchronized void save(String id, String secret) throws Exception {
        save(id, organization(), secret);
    }
    public synchronized void save(String id, String organization, String secret) throws Exception {
        validateId(id);
        validateOrganization(organization);
        if (secret == null || secret.length() < 20 || secret.length() > 4096 || secret.matches("(?s).*\\s.*"))
            throw new IllegalArgumentException("Bitte den Runtime-Schlüssel vollständig eingeben.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!prefs.edit().putString("id", id).putString("organization", organization).putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("secret", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit())
            throw new java.io.IOException("Die Verbindungseinstellungen konnten nicht gespeichert werden.");
    }
    public String id() { return prefs.getString("id", ""); }
    public String organization() { return prefs.getString("organization", ""); }
    public void organization(String value) {
        validateOrganization(value);
        if (!prefs.edit().putString("organization", value).commit())
            throw new IllegalStateException("Die Organisationsangabe konnte nicht gespeichert werden.");
    }
    public boolean configured() { return !id().isEmpty() && !prefs.getString("secret", "").isEmpty(); }
    String secret() throws Exception {
        validateId(id());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("secret", ""), Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
    }
    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public boolean writeEnabled() { return prefs.getBoolean("worklog_write_enabled", false); }
    public void writeEnabled(boolean value) {
        if(!prefs.edit().putBoolean("worklog_write_enabled",value).commit())throw new IllegalStateException("Die Schreibfreigabe konnte nicht gespeichert werden.");
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Persist opt-in/opt-out before a reboot or service restart.
    public void enabled(boolean value) {
        if(!prefs.edit().putBoolean("enabled", value).commit())throw new IllegalStateException("Die Verbindungsfreigabe konnte nicht gespeichert werden.");
    }
}
