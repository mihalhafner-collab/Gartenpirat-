package at.hausgarten.service;

import android.accounts.*;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.*;
import java.security.KeyStore;
import java.util.*;

/** Device consent and private weather key. Neither is part of snapshot/MCP/backup data. */
final class PlanningAccount {
    static final String BASE_SCOPE=CalendarAccessRules.BASE_SCOPE;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("planning_private",0);}
    static boolean connected(Context c){return !prefs(c).getString("calendar_id","").isEmpty()&&!prefs(c).getString("account","").isEmpty();}
    static String token(Context c,String account,boolean busy,Activity consent)throws Exception {
        try{
            String scope=CalendarAccessRules.scope(busy);AccountManager manager=AccountManager.get(c);Account a=new Account(account,"com.google");
            Bundle b=consent==null?manager.getAuthToken(a,scope,null,false,null,null).getResult():manager.getAuthToken(a,scope,null,consent,null,null).getResult();String token=b.getString(AccountManager.KEY_AUTHTOKEN,"");
            if(token.isEmpty())throw new DriveRestClient.Failure("CALENDAR_AUTH_REQUIRED",CalendarAccessRules.message("CALENDAR_AUTH_REQUIRED"),0,true);return token;
        }catch(DriveRestClient.Failure known){throw known;}catch(Exception error){String code=CalendarAccessRules.authenticationCode(error);throw new DriveRestClient.Failure(code,CalendarAccessRules.message(code),code.equals("CALENDAR_TOKEN_NETWORK")?30000:0,!code.equals("CALENDAR_TOKEN_NETWORK"));}
    }
    static CalendarClient client(Context c){SharedPreferences p=prefs(c);return new CalendarClient(new CalendarClient.Token(){public String get()throws Exception{return token(c,p.getString("account",""),p.getBoolean("read_busy",false),null);}public void invalidate(String rejected){AccountManager.get(c).invalidateAuthToken("com.google",rejected);}},new PlanningWire());}
    static javax.crypto.SecretKey encryptionKey()throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="hausgarten.weather.private.v1";
        if(!ks.containsAlias(alias)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (javax.crypto.SecretKey)ks.getKey(alias,null);
    }
    static void weatherKey(Context c,String value)throws Exception {
        if(value.isEmpty()){prefs(c).edit().remove("weather_secret").remove("weather_iv").commit();return;}
        if(value.length()>512||!value.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Ungültiges API-Schlüsselformat.");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryptionKey());
        if(!prefs(c).edit().putString("weather_secret",Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).putString("weather_iv",Base64.getEncoder().encodeToString(cipher.getIV())).commit())throw new java.io.IOException("Wetterzugang nicht gespeichert.");
    }
    static String weatherKey(Context c)throws Exception {SharedPreferences p=prefs(c);String value=p.getString("weather_secret","");if(value.isEmpty())return "";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(),new GCMParameterSpec(128,Base64.getDecoder().decode(p.getString("weather_iv",""))));return new String(cipher.doFinal(Base64.getDecoder().decode(value)),java.nio.charset.StandardCharsets.UTF_8);}
    private PlanningAccount(){}
}
