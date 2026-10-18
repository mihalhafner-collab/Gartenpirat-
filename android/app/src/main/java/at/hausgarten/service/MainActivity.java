package at.hausgarten.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41;
    private static final int SPEECH_REQUEST = 42;
    private static final int DRIVE_FOLDER_REQUEST = 43;
    private static final long AUTO_SYNC_DELAY_MS = 1500;
    private static final long UNDO_DELETE_WINDOW_MS = 8000;
    private static final long PERIODIC_SYNC_INTERVAL_MS = 15 * 60 * 1000;

    private WebView webView;
    private android.window.OnBackInvokedCallback backCallback;
    private ValueCallback<Uri[]> fileCallback;
    private Uri pendingCaptureUri;
    private File pendingCaptureFile;
    private volatile long gardenPickerPreparedUntil;
    private volatile boolean driveFolderPicking;
    private SharedPreferences preferences;
    private LocalStore store;
    private PendingWorkPhotoStore pendingWorkPhotos;
    private WorkLocationController workLocation;
    private static java.lang.ref.WeakReference<MainActivity> foreground = new java.lang.ref.WeakReference<>(null);
    private static void requestMcpSync(Context context) {
        MainActivity activity=foreground.get();
        if(activity!=null)activity.runOnUiThread(()->activity.requestAutomaticSync(500));
        else DriveSyncCoordinator.request(context,500);
    }
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicBoolean syncAgain = new AtomicBoolean(false);
    private final Runnable delayedAutomaticSync = this::automaticSyncIfConfigured;
    private final Runnable periodicAutomaticSync = new Runnable() {
        @Override public void run() {
            automaticSyncIfConfigured();
            syncHandler.postDelayed(this, PERIODIC_SYNC_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("haus_garten", MODE_PRIVATE);
        store = new LocalStore(this);
        DriveSyncJob.install(this);
        pendingWorkPhotos = new PendingWorkPhotoStore(this);
        setContentView(buildLayout());
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getInsetsController().setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::navigateBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        configureWebView();
        workLocation=new WorkLocationController(this,(request,value)->{
            if(webView!=null)webView.evaluateJavascript("window.onWorkLocationResult&&window.onWorkLocationResult("+JSONObject.quote(request)+","+value.toString()+");",null);
        });
        openMobile();
    }

    @Override protected void onResume() {
        foreground = new java.lang.ref.WeakReference<>(this);
        super.onResume();
        // Resume only the previously opted-in runtime after a process restart or device test.
        // Never enable a stopped connection or change its credentials/permissions here.
        PixelTunnelConfig existingTunnel=new PixelTunnelConfig(this);
        if(existingTunnel.enabled()&&existingTunnel.configured()&&!PixelTunnelService.status(this).optBoolean("running")){
            try{startForegroundService(new Intent(this,PixelTunnelService.class));}
            catch(RuntimeException denied){getSharedPreferences("pixel_tunnel_status",MODE_PRIVATE).edit().putString("state","start_blocked").putString("detail","Android hat den Wiederstart eingeschränkt. Bitte Verbindung in der App starten.").apply();}
        }
        syncHandler.removeCallbacks(periodicAutomaticSync);
        requestAutomaticSync(AUTO_SYNC_DELAY_MS);
        syncHandler.postDelayed(periodicAutomaticSync, PERIODIC_SYNC_INTERVAL_MS);
    }

    @Override protected void onPause() {
        if (foreground.get() == this) foreground.clear();
        syncHandler.removeCallbacks(delayedAutomaticSync);
        syncHandler.removeCallbacks(periodicAutomaticSync);
        try { if(DriveAccount.available(this)&&store.pendingActions().length()>0)
            DriveSyncCoordinator.request(this,Math.max(AUTO_SYNC_DELAY_MS,store.remainingDeleteUndoMs()+200)); } catch(Exception ignored) {}
        super.onPause();
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(245,247,245));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
                root.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard));
            } else {
                root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            // Native padding owns the system-bar/keyboard space; do not apply it twice in WebView.
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        return root;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true); settings.setAllowContentAccess(true); settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new SpeechBridge(), "AndroidSpeech");
        webView.addJavascriptInterface(new DataBridge(), "AndroidData");
        webView.addJavascriptInterface(new PhoneBridge(), "AndroidPhone");
        webView.addJavascriptInterface(new GardenPhotosBridge(), "AndroidGardenPhotos");
        webView.setWebViewClient(new WebViewClient() {
            private boolean external(String url) {
                if (url != null && url.startsWith("file:///android_asset/")) return false;
                if (url == null || "about:blank".equals(url)) return false;
                Uri uri = Uri.parse(url);
                if (java.util.Arrays.asList("http", "https", "tel", "mailto", "geo").contains(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException error) { Toast.makeText(MainActivity.this,"Keine passende App verfügbar.",Toast.LENGTH_LONG).show(); }
                }
                return true; // Never expose AndroidData to external websites.
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return external(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return external(request.getUrl().toString()); }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null); fileCallback = callback;
                boolean gardenPhoto = gardenPickerPreparedUntil > android.os.SystemClock.elapsedRealtime();
                gardenPickerPreparedUntil = 0;
                if (gardenPhoto) {
                    if (!startGardenPhotoPicker()) {
                        callback.onReceiveValue(null); fileCallback = null;
                        Toast.makeText(MainActivity.this, "Keine Fotoauswahl verfügbar.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                if (params.isCaptureEnabled() && params.getMode() != FileChooserParams.MODE_OPEN_MULTIPLE) {
                    if (!startPhotoCapture()) {
                        // startPhotoCapture already reports the specific failure.
                        fileCallback = null; callback.onReceiveValue(null);
                    }
                    return true;
                }
                Intent intent = params.createIntent(); intent.setType("image/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(intent, FILE_REQUEST); }
                catch (ActivityNotFoundException error) { callback.onReceiveValue(null); fileCallback = null; Toast.makeText(MainActivity.this, "Keine Foto-App gefunden.", Toast.LENGTH_LONG).show(); }
                return true;
            }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this); input.setSingleLine(true); input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this).setTitle(message).setView(input)
                    .setPositiveButton("OK", (dialog, which) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("Abbrechen", (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel()).show(); return true;
            }
        });
    }

    private boolean startPhotoCapture() {
        clearPendingCapture(true);
        try {
            // Track the file before URI preparation so a failed hand-off cannot leak it.
            pendingCaptureFile=PhotoCaptureProvider.createTarget(this);
            pendingCaptureUri=PhotoCaptureProvider.uri(this,pendingCaptureFile);
        } catch (java.io.IOException error) {
            return failPhotoCapture("PREPARE_IO", "Die Fotodatei konnte nicht vorbereitet werden. Bitte freien Speicher prüfen und erneut versuchen.", error);
        } catch (SecurityException error) {
            return failPhotoCapture("PREPARE_DENIED", "Android hat den Zugriff auf den Foto-Zwischenspeicher blockiert.", error);
        } catch (RuntimeException error) {
            return failPhotoCapture("PREPARE_FAILED", "Die Fotoaufnahme konnte nicht vorbereitet werden. Bitte erneut versuchen.", error);
        }
        try {
            Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT,pendingCaptureUri);
            camera.setClipData(ClipData.newRawUri("Kamerafoto",pendingCaptureUri));
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // Do not gate the launch on package visibility / resolveActivity().
            startActivityForResult(camera,FILE_REQUEST);
            return true;
        } catch (ActivityNotFoundException error) {
            return failPhotoCapture("CAMERA_MISSING", "Keine Kamera-App für Fotoaufnahmen verfügbar.", error);
        } catch (SecurityException error) {
            return failPhotoCapture("LAUNCH_DENIED", "Android hat die Übergabe an die Kamera blockiert. Bitte Geräteeinschränkungen prüfen.", error);
        } catch (RuntimeException error) {
            return failPhotoCapture("LAUNCH_FAILED", "Die Kamera konnte nicht gestartet werden. Bitte erneut versuchen.", error);
        }
    }

    private boolean failPhotoCapture(String code,String message,Exception error) {
        logPhotoCaptureFailure(code,error);
        clearPendingCapture(true);
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        return false;
    }

    private static void logPhotoCaptureFailure(String code,Exception error) {
        // No file paths, URI contents, customer details or exception messages in logs.
        String cause=error.getCause()==null?"":"; cause="+error.getCause().getClass().getSimpleName();
        android.util.Log.e("HausGartenCamera",code+"; type="+error.getClass().getSimpleName()+cause);
    }

    private void clearPendingCapture(boolean delete) {
        Uri uri=pendingCaptureUri;File file=pendingCaptureFile;pendingCaptureUri=null;pendingCaptureFile=null;
        if(uri!=null)try{
            revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }catch(RuntimeException error){logPhotoCaptureFailure("REVOKE_FAILED",error);}
        if(delete&&file!=null)try{
            if(file.exists()&&!file.delete())android.util.Log.w("HausGartenCamera","TEMP_DELETE_FAILED");
        }catch(SecurityException error){logPhotoCaptureFailure("TEMP_DELETE_DENIED",error);}
    }

    // Only Gartenpirat opts into the cloud-aware single-image picker.
    // The established business-photo chooser and its callback remain unchanged.
    private boolean startGardenPhotoPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Intent picker = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
                picker.setType("image/*");
                startActivityForResult(picker, FILE_REQUEST);
                return true;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // Fall back to the user's installed photo/document provider.
            }
        }
        try {
            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.setType("image/*"); picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(picker, FILE_REQUEST);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    public class GardenPhotosBridge {
        @JavascriptInterface public void preparePicker() {
            // A short-lived marker scopes the next WebView file request to this feature.
            gardenPickerPreparedUntil = android.os.SystemClock.elapsedRealtime() + 3000;
        }
        @JavascriptInterface public void setFullscreen(boolean enabled) {
            runOnUiThread(() -> {
                View decor = getWindow().getDecorView();
                if (Build.VERSION.SDK_INT >= 30) {
                    android.view.WindowInsetsController controller = getWindow().getInsetsController();
                    if (controller != null) {
                        if (enabled) {
                            controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                            controller.hide(WindowInsets.Type.systemBars());
                        } else controller.show(WindowInsets.Type.systemBars());
                    }
                } else {
                    int flags = decor.getSystemUiVisibility();
                    int immersive = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                    decor.setSystemUiVisibility(enabled ? flags | immersive : flags & ~immersive);
                }
                decor.requestApplyInsets();
            });
        }
    }

    private void openMobile() { webView.loadUrl("file:///android_asset/mobile.html"); }

    public class PhoneBridge {
        @JavascriptInterface public void dial(String number) {
            if (number == null || number.trim().isEmpty()) return;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null));
                try { startActivity(intent); }
                catch (ActivityNotFoundException error) { Toast.makeText(MainActivity.this, "Keine Telefon-App verfügbar.", Toast.LENGTH_LONG).show(); }
            });
        }

        @JavascriptInterface public void openMaps(String address) {
            if (address == null || address.trim().isEmpty()) return;
            runOnUiThread(() -> {
                Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(address.trim()));
                try { startActivity(new Intent(Intent.ACTION_VIEW, geo)); }
                catch (ActivityNotFoundException error) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address.trim())))); }
                    catch (ActivityNotFoundException noBrowser) { Toast.makeText(MainActivity.this, "Google Maps kann nicht geöffnet werden.", Toast.LENGTH_LONG).show(); }
                }
            });
        }
    }

    public class DataBridge {
        @JavascriptInterface public String getState() { try{JSONObject state=store.getState();state.put("master",PixelMcpReadTools.effective(state)).put("ui_effective",true);JSONArray photos=state.getJSONObject("master").getJSONArray("photos");for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);String thumbnail=photo.optString("thumbnail_storage_name");if(thumbnail.matches("[0-9a-f]{64}\\.jpg"))photo.put("thumbnail_data","content://"+getPackageName()+".photothumbnails/"+thumbnail);}return state.toString();}catch(Exception e){return store.getState().toString();} }
        @JavascriptInterface public String ownerCall(String name,String arguments){
            try{if(!PixelMcpOwnerTools.read(name)&&!PixelMcpOwnerTools.write(name)&&!OfferTools.read(name)&&!OfferTools.write(name))throw new IllegalArgumentException("Unbekannte Eigentümerfunktion.");PixelMcpReadTools tools=new PixelMcpReadTools(()->store.getState(),store,new PixelOwnerAccess(MainActivity.this,store));return tools.call(name,new JSONObject(arguments)).toString();}
            catch(Exception e){try{return new JSONObject().put("ok",false).put("error",new JSONObject().put("code",e instanceof PixelMcpWriteTools.Rejected?((PixelMcpWriteTools.Rejected)e).code:"VALIDATION_ERROR").put("message",e instanceof IllegalArgumentException?e.getMessage():"Vorgang nicht abgeschlossen. Sicherung, Versionen und Berechtigungen prüfen.")).toString();}catch(Exception ignored){return "{}";}}
        }
        @JavascriptInterface public String getSyncStatus() { return syncStatus().toString(); }
        @JavascriptInterface public String getSyncDetails(){try{JSONObject s=store.getState();return DriveJournalRules.queueStatus(s.getJSONObject("master"),s.getJSONArray("pending")).toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public String getSyncConflict(String id){try{return PixelMcpOwnerTools.conflicts(store.getState(),new JSONObject().put("limit",1).put("client_id",id)).toString();}catch(Exception e){return "ERROR: Konflikt konnte nicht vollständig gelesen werden.";}}
        @JavascriptInterface public String resolveSyncConflict(String request){try{return new PixelOwnerAccess(MainActivity.this,store).execute("resolve_sync_conflict",new JSONObject(request)).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
        @JavascriptInterface public void connectDrive(){runOnUiThread(()->startActivity(new Intent(MainActivity.this,DriveConnectionActivity.class)));}
        @JavascriptInterface public void retryDrive(){preferences.edit().putInt("drive_retry_count",0).putBoolean("drive_user_action_required",false).putLong("drive_next_retry",0).commit();runOnUiThread(()->MainActivity.this.synchronize());}
        @JavascriptInterface public String getPixelTunnelStatus() { return PixelTunnelService.status(MainActivity.this).toString(); }
        @JavascriptInterface public String getWorkSession() {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).current().toString(); }
            catch (Exception error) { return "{\"state\":\"ERROR\",\"message\":"+JSONObject.quote(String.valueOf(error.getMessage()))+"}"; }
        }
        @JavascriptInterface public String startWorkSession(String value) {
            try { return store.startWorkSession(new JSONObject(value)).toString(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String pauseWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).pause(sessionId,requestId).toString(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String resumeWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).resume(sessionId,requestId).toString(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String stopWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).stop(sessionId,requestId).toString(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String continueWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).continueStopped(sessionId,requestId).toString(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public void requestWorkLocation(String requestId) { runOnUiThread(()->workLocation.request(requestId)); }
        @JavascriptInterface public void cancelWorkLocation() { runOnUiThread(()->workLocation.cancel()); }
        @JavascriptInterface public String discardWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).discard(sessionId,requestId).toString(); }
            catch(Exception error){return "ERROR: "+error.getMessage();}
        }
        @JavascriptInterface public String restoreWorkSession(String sessionId,String requestId) {
            try { return new WorkSessionStore(store.getWritableDatabase(),MainActivity.this).restore(sessionId,requestId).toString(); }
            catch(Exception error){return "ERROR: "+error.getMessage();}
        }
        @JavascriptInterface public String completeWorkSession(String sessionId,String value,String requestId) {
            try { String id=store.completeWorkSession(sessionId,new JSONObject(value),requestId);requestAutomaticSync(AUTO_SYNC_DELAY_MS);return id; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public void openPixelTunnelSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, PixelTunnelActivity.class)));
        }
        @JavascriptInterface public String getAppInfo() {
            JSONObject info = new JSONObject();
            try {
                android.content.pm.PackageInfo installed = getPackageManager().getPackageInfo(getPackageName(), 0);
                info.put("versionName", installed.versionName);
                info.put("versionCode", android.os.Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode);
            } catch (Exception ignored) {}
            return info.toString();
        }
        @JavascriptInterface public String saveWorklog(String value) {
            try { JSONObject payload=new JSONObject(value);String sessionId=payload.optString("_session_id");payload.remove("_session_id");String id=sessionId.isEmpty()?store.saveWorklog(payload):store.completeWorkSession(sessionId,payload,"ui-finalize-"+sessionId);requestAutomaticSync(AUTO_SYNC_DELAY_MS);return id; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String saveCustomer(String value) {
            try { String id = store.saveCustomer(new JSONObject(value)); requestAutomaticSync(AUTO_SYNC_DELAY_MS); return id; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String inspectOrder(String id) {
            try{return OrderReviewRules.inspect(store.getState(),id).toString();}
            catch(Exception error){return "ERROR: "+error.getMessage();}
        }
        @JavascriptInterface public String saveReviewedOrder(String value) {
            try{String id=store.saveReviewedOrder(new JSONObject(value));requestAutomaticSync(AUTO_SYNC_DELAY_MS);return new JSONObject().put("id",id).put("saved",true).toString();}
            catch(Exception error){return "ERROR: "+error.getMessage();}
        }
        @JavascriptInterface public String saveOrder(String value) {
            try { String id = store.saveOrder(new JSONObject(value)); requestAutomaticSync(AUTO_SYNC_DELAY_MS); return id; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String getPhotoStorageSummary(){try{return store.photoAttachmentStore().storageSummary().toString();}catch(Exception e){return "ERROR: Speicherstatus nicht verfügbar.";}}
        @JavascriptInterface public String getOwnerSettings(){return store.ownerValue("settings","{}");}
        @JavascriptInterface public String getPersonalPaid(){return "1".equals(store.ownerValue("personal_paid_migrated",""))?store.ownerValue("personal_paid","[]"):"";}
        @JavascriptInterface public String migratePersonalPaid(String value){
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{if(!"1".equals(store.ownerValue("personal_paid_migrated",""))){JSONArray old=new JSONArray(value),current=new JSONArray(store.ownerValue("personal_paid","[]"));Set<String> union=new java.util.LinkedHashSet<>();for(JSONArray list:new JSONArray[]{old,current})for(int i=0;i<list.length();i++){String id=list.getString(i);if(id.isEmpty()||id.length()>500)throw new IllegalArgumentException();union.add(id);}store.putOwnerValue("personal_paid",new JSONArray(union).toString());store.putOwnerValue("personal_paid_migrated","1");}db.setTransactionSuccessful();return "OK";}catch(Exception e){return "ERROR: Persönliche Übersicht bleibt erhalten; Übernahme nicht abgeschlossen.";}finally{db.endTransaction();}
        }
        @JavascriptInterface public String setPersonalPaid(String value){
            try{JSONArray ids=new JSONArray(value);JSONObject all=PixelMcpReadTools.effective(store.getState());for(int i=0;i<ids.length();i++)PixelMcpWriteTools.find(all.getJSONArray("worklogs"),ids.getString(i));store.putOwnerValue("personal_paid",ids.toString());return "OK";}catch(Exception e){return "ERROR: Persönliche Übersicht konnte nicht gespeichert werden.";}
        }
        @JavascriptInterface public String getInvoiceShareHandoff(){return store.ownerValue("invoice_share_handoff", "{}");}
        @JavascriptInterface public String sharePreparedInvoice(){
            try{JSONObject handoff=new JSONObject(store.ownerValue("invoice_share_handoff","{}")),all=PixelMcpReadTools.effective(store.getState()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),handoff.getString("invoice_id"));return shareInvoiceById(invoice.getString("id"),handoff.getString("channel"));}catch(Exception e){return "ERROR: Originalbeleg oder Versandvorbereitung fehlt.";}
        }
        @JavascriptInterface public String saveInvoice(String value) {
            try{String id=store.saveInvoiceDraft(new JSONObject(value));requestAutomaticSync(AUTO_SYNC_DELAY_MS);return id;}
            catch(Exception error){return "ERROR: "+error.getMessage();}
        }
        @JavascriptInterface public String requestInvoiceIssue(String identifier) {
            try{JSONObject all=PixelMcpReadTools.effective(store.getState()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),identifier);store.execute("request_invoice_issue",new JSONObject().put("request_id","ui-issue-"+UUID.randomUUID()).put("invoice_id",identifier).put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("invoice",invoice))).put("confirmed",true));return "OK";}catch(Exception e){return "ERROR: "+e.getMessage();}
        }
        private void assertEditableInvoice(String identifier) throws Exception {
            JSONObject snapshot=new JSONObject(store.snapshotText(readAsset("initial-data.json")));
            JSONArray invoices=snapshot.optJSONArray("invoices");
            if(invoices!=null)for(int i=0;i<invoices.length();i++){JSONObject row=invoices.getJSONObject(i);if(identifier.equals(row.optString("id"))&&(!row.optString("issued_at").isEmpty()||!"Entwurf".equals(row.optString("status"))))throw new Exception("Abgeschlossene Rechnungen bleiben unverändert.");}
            try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE kind='invoice_issue'",null)){
                while(cursor.moveToNext())if(identifier.equals(new JSONObject(cursor.getString(0)).optString("id")))throw new Exception("Abschluss bereits vorgemerkt. Bitte Drive-Abgleich abwarten.");
            }
        }
        @JavascriptInterface public void shareOffer(String id){
            new Thread(()->{try{JSONObject offer=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("offers"),id);byte[] bytes;try{bytes=new BusinessDocumentStore(MainActivity.this).read(offer);}catch(Exception missing){bytes=DriveSyncCoordinator.fetchDocument(MainActivity.this,offer);new BusinessDocumentStore(MainActivity.this).store(bytes,offer);}File dir=new File(getCacheDir(),"invoice-share");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Freigabeordner fehlt.");File target=new File(dir,"Angebot-"+UUID.randomUUID()+".pdf");try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){out.write(bytes);}Uri uri=Uri.parse("content://"+getPackageName()+".invoicefiles/"+target.getName());Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(android.content.ClipData.newRawUri("Angebot",uri));runOnUiThread(()->startActivity(Intent.createChooser(send,"Angebots-PDF teilen")));}catch(Exception error){runOnUiThread(()->Toast.makeText(MainActivity.this,"PDF noch nicht verfügbar. Dokument und Drive-Abgleich prüfen.",Toast.LENGTH_LONG).show());}}).start();
        }
        @JavascriptInterface public String shareInvoiceById(String id,String channel){try{JSONObject invoice=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices"),id);if(invoice.optString("issued_at").isEmpty())throw new IllegalArgumentException("Nur ausgestellte Originalbelege teilen.");byte[] bytes;try{bytes=new BusinessDocumentStore(MainActivity.this).read(invoice);}catch(IOException absent){bytes=DriveSyncCoordinator.fetchDocument(MainActivity.this,invoice);new BusinessDocumentStore(MainActivity.this).store(bytes,invoice);}invoice.put("pdf_base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));return shareInvoiceVia(invoice.toString(),channel);}catch(Exception e){return "ERROR: Original-PDF ist noch nicht vollständig verfügbar. Drive abgleichen.";}}
        @JavascriptInterface public String shareInvoice(String value) {
            return shareInvoiceVia(value,"share");
        }
        @JavascriptInterface public String shareInvoiceVia(String value, String channel) {
            try {
                if (!new HashSet<>(java.util.Arrays.asList("share","email","whatsapp")).contains(channel)) throw new Exception("Unbekannter Versandweg.");
                JSONObject invoice = new JSONObject(value);
                byte[] bytes = android.util.Base64.decode(invoice.getString("pdf_base64"), android.util.Base64.DEFAULT);
                if (bytes.length > 10 * 1024 * 1024 || bytes.length < 5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))
                    throw new Exception("Ungültiges Rechnungs-PDF.");
                java.io.File folder = new java.io.File(getCacheDir(), "invoice-share"); folder.mkdirs();
                java.io.File file = new java.io.File(folder, "Rechnung-" + UUID.randomUUID().toString() + ".pdf");
                try(java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) { stream.write(bytes); }
                Uri uri = Uri.parse("content://" + getPackageName() + ".invoicefiles/" + file.getName());
                Intent send = new Intent(Intent.ACTION_SEND); send.setType("application/pdf"); send.putExtra(Intent.EXTRA_STREAM,uri);
                send.setClipData(android.content.ClipData.newRawUri("Rechnung",uri)); send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.putExtra(Intent.EXTRA_SUBJECT, "Rechnung " + invoice.optString("invoice_number"));
                send.putExtra(Intent.EXTRA_TEXT,"Guten Tag,\n\nanbei Ihre Rechnung " + invoice.optString("invoice_number") + ".\n\nMit freundlichen Grüßen" + (invoice.optJSONObject("issuer")==null?"":"\n"+invoice.getJSONObject("issuer").optString("owner_name")));
                JSONObject recipient = invoice.optJSONObject("recipient");
                if(recipient != null && !recipient.optString("email").isEmpty())send.putExtra(Intent.EXTRA_EMAIL,new String[]{recipient.optString("email")});
                runOnUiThread(() -> {
                    try {
                        if ("whatsapp".equals(channel)) {
                            Intent whatsapp = new Intent(send);whatsapp.setPackage("com.whatsapp");
                            try {startActivity(whatsapp);} catch(ActivityNotFoundException missing){whatsapp.setPackage("com.whatsapp.w4b");startActivity(whatsapp);}
                        } else if ("email".equals(channel)) {
                            java.util.List<android.content.pm.ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")),0);
                            java.util.ArrayList<Intent> targets=new java.util.ArrayList<>();
                            for(android.content.pm.ResolveInfo app:apps){Intent mail=new Intent(send);mail.setComponent(new android.content.ComponentName(app.activityInfo.packageName,app.activityInfo.name));targets.add(mail);}
                            if(targets.isEmpty())throw new ActivityNotFoundException();
                            Intent chooser=Intent.createChooser(targets.remove(0),"Rechnung per E-Mail");chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,targets.toArray(new Intent[0]));startActivity(chooser);
                        } else startActivity(Intent.createChooser(send,"Rechnung versenden"));
                    } catch(Exception error){Toast.makeText(MainActivity.this,"Versand-App fehlt oder unterstützt diesen PDF-Anhang nicht. Bitte über PDF teilen eine andere App auswählen.",Toast.LENGTH_LONG).show();}
                });
                return "OK";
            } catch(Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String savePhoto(String value) {
            try { String id = store.savePhoto(new JSONObject(value)); requestAutomaticSync(AUTO_SYNC_DELAY_MS); return id; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String stagePendingWorkPhoto(String value) {
            try { return pendingWorkPhotos.stage(value); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String getPendingWorkPhoto() {
            try { return pendingWorkPhotos.read(); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String clearPendingWorkPhoto() {
            try { pendingWorkPhotos.clear(); return "OK"; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String getPhotoData(String photoId) {
            try { return store.loadPhotoData(photoId); }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String removeEntity(String entity, String id) {
            try { String actionId = store.removeEntity(entity, id); requestAutomaticSync(AUTO_SYNC_DELAY_MS); return actionId; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public String undoRemoveEntity(String entity, String id) {
            try { String result = store.undoRemoveEntity(entity, id); requestAutomaticSync(AUTO_SYNC_DELAY_MS); return result; }
            catch (Exception error) { return "ERROR: " + error.getMessage(); }
        }
        @JavascriptInterface public void synchronize() { runOnUiThread(() -> MainActivity.this.synchronize()); }
    }

    static class LocalStore extends SQLiteOpenHelper implements PixelMcpWriteTools.Sink {
        private final Context context;
        private final SharedPreferences preferences;
        private final PhotoAttachmentStore photoAttachments;
        LocalStore(Context context) {
            super(context, "haus-garten-mobile.db", null, 4);
            this.context = context;
            preferences = context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE);
            photoAttachments = new PhotoAttachmentStore(context);
            migrateLegacySnapshotPhotoAttachments();
        }
        private String readAsset(String name) throws Exception {
            try (InputStream input = context.getAssets().open(name); BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line); return out.toString();
            }
        }
        private String isoNow() { return java.time.Instant.now().toString(); }
        private void migrateLegacySnapshotPhotoAttachments(){
            try{
                String saved=preferences.getString("drive_snapshot","");if(saved.isEmpty())return;JSONObject snapshot=new JSONObject(saved);JSONArray photos=snapshot.optJSONArray("photos");boolean inline=false;
                for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null&&(photo.has("data")||photo.has("full_data"))){inline=true;break;}}
                if(!inline)return;recordSnapshotPhotoAttachments(snapshot);preferences.edit().putString("drive_snapshot",snapshot.toString()).commit();
            }catch(Exception ignored){/* Preserve the original snapshot; getState still withholds inline binary. */}
        }
        private void documentReferences(JSONObject snapshot)throws Exception{
            JSONArray invoices=snapshot.optJSONArray("invoices");for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject invoice=invoices.getJSONObject(i);String inline=invoice.optString("pdf_base64");if(inline.isEmpty())continue;if(inline.length()>28*1024*1024)throw new IOException("Original-PDF ist zu groß.");byte[] raw=android.util.Base64.decode(inline,android.util.Base64.DEFAULT);String hash=PhotoAttachmentStore.sha256(raw);if(!invoice.optString("pdf_sha256").isEmpty()&&!hash.equals(invoice.getString("pdf_sha256")))throw new IOException("Original-PDF hat eine abweichende Prüfsumme.");invoice.put("pdf_sha256",hash).put("pdf_storage_name",hash+".pdf").put("pdf_byte_size",raw.length).put("document_id","invoice:"+invoice.getString("id"));new BusinessDocumentStore(context).store(raw,invoice);invoice.remove("pdf_base64");}
        }
        private void removeSnapshotPhotoBinaries(JSONObject snapshot){
            JSONArray photos=snapshot.optJSONArray("photos");for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null){photo.remove("data");photo.remove("full_data");}}
        }
        @Override public void onCreate(SQLiteDatabase database) { database.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)"); WorkSessionStore.createSchema(database); createPhotoAttachmentSchema(database); createOwnerSchema(database); }
        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) { if(oldVersion<2) WorkSessionStore.createSchema(database);if(oldVersion<3){createPhotoAttachmentSchema(database);migrateLegacyPendingPhotoAttachments(database);}if(oldVersion<4)createOwnerSchema(database); }
        private void createOwnerSchema(SQLiteDatabase database){
            database.execSQL("CREATE TABLE IF NOT EXISTS owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS mcp_uploads(id TEXT PRIMARY KEY,metadata TEXT NOT NULL,data BLOB NOT NULL)");
        }
        String snapshotText(String fallback){
            String value=ownerValue("snapshot",null);return value==null?preferences.getString("drive_snapshot",fallback):value;
        }
        void putOwnerValue(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("owner_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
        String ownerValue(String key,String fallback){
            int count;try(Cursor row=getReadableDatabase().rawQuery("SELECT length(value) FROM owner_state WHERE key=?",new String[]{key})){if(!row.moveToFirst())return fallback;count=row.getInt(0);}
            StringBuilder out=new StringBuilder();for(int offset=0;offset<count;offset+=32768)try(Cursor row=getReadableDatabase().rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?",new String[]{Integer.toString(offset+1),key})){if(!row.moveToFirst())return fallback;out.append(row.getString(0));}return out.toString();
        }
        void commitSnapshot(JSONObject snapshot,Set<String> confirmed)throws Exception{
            SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{putOwnerValue("snapshot",snapshot.toString());acknowledge(confirmed);db.setTransactionSuccessful();}finally{db.endTransaction();}
        }
        private void createPhotoAttachmentSchema(SQLiteDatabase database){
            database.execSQL("CREATE TABLE IF NOT EXISTS photo_attachments(photo_id TEXT PRIMARY KEY,content_sha256 TEXT NOT NULL,mime_type TEXT NOT NULL,byte_size INTEGER NOT NULL,storage_name TEXT NOT NULL,role TEXT NOT NULL DEFAULT 'documentation',transfer_state TEXT NOT NULL DEFAULT 'local',created_at TEXT NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS ix_photo_attachments_hash ON photo_attachments(content_sha256)");
        }
        private void migrateLegacyPendingPhotoAttachments(SQLiteDatabase database){
            try(Cursor rows=database.rawQuery("SELECT payload FROM pending WHERE kind='photo' ORDER BY rowid",null)){
                while(rows.moveToNext())try{
                    JSONObject payload=new JSONObject(rows.getString(0));if(payload.optString("data").isEmpty()||payload.optString("id").isEmpty())continue;
                    PhotoAttachmentStore.Prepared prepared=photoAttachments.prepareLegacy(payload);saveAttachmentMetadata(database,payload.getString("id"),prepared.metadata,"legacy_inline");
                }catch(Exception ignored){/* Keep the immutable legacy action unchanged; the laptop can still validate it. */}
            }
        }
        JSONObject getState() {
            SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject result=getStateLocked();db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
        }
        private JSONObject getStateLocked() {
            JSONObject output = new JSONObject();
            try { JSONObject master=new JSONObject(snapshotText(readAsset("initial-data.json")));removeSnapshotPhotoBinaries(master);documentReferences(master);DriveJournalRules.validateSnapshot(master);if(master.getJSONArray("service_catalog").length()==0)master.put("service_catalog",new JSONArray(readAsset("service-catalog.json")));output.put("master",master); output.put("pending", DriveJournalRules.union(pendingActions(),output.getJSONObject("master").optJSONArray("drive_review_actions"))); output.put("last_drive_sync", preferences.getString("last_drive_sync", ""));output.put("work_session",new WorkSessionStore(getWritableDatabase(),context).current());output.put("sync_queue",DriveJournalRules.queueStatus(output.getJSONObject("master"),output.getJSONArray("pending"))); }
            catch (Exception error) { try { output.put("error", error.getMessage()); } catch (Exception ignored) {} }
            try{output.put("drive_sync_failed",preferences.getBoolean("drive_sync_failed",false)).put("last_sync_attempt",preferences.getString("last_sync_attempt","")).put("cloud_confirmed",preferences.getBoolean("drive_cloud_confirmed",false)&&output.getJSONObject("sync_queue").getInt("total")==0).put("confirmation_scope",preferences.getString("drive_confirmation_scope","local_only")).put("last_full_sync",preferences.getString("last_full_sync",""));}catch(Exception ignored){}
            return output;
        }
        String saveWorklog(JSONObject payload) throws Exception {
            SQLiteDatabase transaction=getWritableDatabase();transaction.beginTransaction();
            try {String result=saveWorklogLocked(payload);transaction.setTransactionSuccessful();return result;}
            finally{transaction.endTransaction();}
        }
        private String saveWorklogLocked(JSONObject payload) throws Exception {
            JSONObject current=PixelMcpReadTools.effective(getState());
            payload=ManualChangeRules.editor(payload,findById(current.getJSONArray("worklogs"),payload.optString("id")),"worklog");
            String customerId = payload.optString("customer_id").trim();
            if (customerId.isEmpty() || payload.optString("activity").trim().isEmpty()) throw new IllegalArgumentException("Kunde und Tätigkeit sind erforderlich.");
            boolean isNew = payload.optString("id").isEmpty();
            boolean requireOrder = payload.optBoolean("_require_order", false) || payload.optBoolean("require_order", false);
            String orderId = payload.isNull("order_id") ? "" : payload.optString("order_id").trim();
            JSONObject master = new JSONObject(snapshotText(readAsset("initial-data.json")));
            JSONArray customers = effectiveRows(master, "customers", "customer"), orders = effectiveRows(master, "orders", "order");
            JSONObject previous=findById(effectiveRows(master,"worklogs","worklog"),payload.optString("id"));
            isNew=previous==null;
            if (!containsId(customers, customerId)) throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
            if (isNew && requireOrder && orderId.isEmpty()) throw new IllegalArgumentException("Bitte zuerst einen Auftrag auswählen oder anlegen.");
            if (!orderId.isEmpty()) {
                JSONObject order = findById(orders, orderId);
                if (order == null) throw new IllegalArgumentException("Der ausgewählte Auftrag existiert nicht.");
                if (!customerId.equals(order.optString("customer_id"))) throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
                if (payload.optString("location").trim().isEmpty()) payload.put("location", order.optString("location"));
            }
            BillingRules.work(payload,previous,new JSONObject().put("customers",customers).put("orders",orders));
            WorklogRules.validate(payload, isNew);
            WorklogRelationRules.validate(payload,previous,effectiveRows(master,"photos","photo"),effectiveRows(master,"invoices","invoice"));
            String entityId = payload.optString("id");
            if (entityId.isEmpty()) entityId = "wrk_mobile_" + UUID.randomUUID().toString().replace("-", "");
            payload.put("id", entityId);
            if(payload.optString("source").trim().isEmpty())payload.put("source",previous==null?"Pixel offline":previous.optString("source","Pixel offline"));
            prepareManualChange("worklog",payload,previous);
            String actionId = "worklog_change_" + UUID.randomUUID().toString().replace("-", "");
            SQLiteDatabase database = getWritableDatabase(); database.beginTransaction();
            try {
                // Retain successive offline edits as a guarded action chain, not a lossy replacement.
                ContentValues values = new ContentValues(); values.put("id", actionId); values.put("kind", "worklog"); values.put("payload", payload.toString()); values.put("created_at", isoNow());
                database.insertOrThrow("pending", null, values); database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            return entityId;
        }
        JSONObject startWorkSession(JSONObject payload)throws Exception{
            JSONObject master=new JSONObject(snapshotText(readAsset("initial-data.json")));
            String customer=payload.optString("customer_id").trim(),order=payload.optString("order_id").trim();
            if(!customer.isEmpty()&&!containsId(effectiveRows(master,"customers","customer"),customer))throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
            if(!customer.isEmpty())BillingRules.work(payload,null,new JSONObject().put("customers",effectiveRows(master,"customers","customer")).put("orders",effectiveRows(master,"orders","order")));
            if(!order.isEmpty()){
                JSONObject row=findById(effectiveRows(master,"orders","order"),order);
                if(row==null||!customer.equals(row.optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
            }
            return new WorkSessionStore(getWritableDatabase(),context).start(payload);
        }
        String completeWorkSession(String sessionId,JSONObject payload,String requestId)throws Exception{
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try{
            WorkSessionStore sessions=new WorkSessionStore(getWritableDatabase(),context);JSONObject session=sessions.current(sessionId);
            if(!WorkSessionStore.STOPPED.equals(session.optString("state"))&&!WorkSessionStore.FINALIZED.equals(session.optString("state")))throw new IllegalStateException("Bitte die laufende Arbeit zuerst stoppen.");
            String stableId="wrk_session_"+sessionId.replace("ses_pixel_","");
            if(WorkSessionStore.FINALIZED.equals(session.optString("state"))){if(stableId.equals(session.optString("finalized_worklog_id")))return stableId;throw new IllegalStateException("Diese Arbeitssitzung ist bereits abgeschlossen.");}
            payload.remove("_require_order");payload.remove("require_order");payload.put("id",stableId).put("work_date",session.getString("work_date")).put("hours",session.getDouble("hours"));
            if(session.optBoolean("pause_unverified"))throw new IllegalArgumentException("Pausenzeiten wegen widersprüchlicher Geräteuhr bitte prüfen.");
            payload.put("break_minutes",session.optInt("break_minutes",0));payload.remove("start_time");payload.remove("end_time");
            java.time.format.DateTimeFormatter timerStamp=java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Vienna"));String timing="Timer-Nachweis (Europe/Vienna): "+timerStamp.format(java.time.Instant.ofEpochMilli(session.getLong("started_at_ms")))+" bis "+timerStamp.format(java.time.Instant.parse(session.getString("updated_at")))+"; "+session.getDouble("hours")+" h netto, "+session.optInt("break_minutes",0)+" min Pause.";if(!payload.optString("notes").contains(timing))payload.put("notes",(payload.optString("notes").isEmpty()?"":payload.getString("notes")+"\n")+timing);
            if(payload.optString("customer_id").trim().isEmpty())payload.put("customer_id",session.optString("customer_id"));
            if(!payload.has("order_id"))payload.put("order_id",session.optString("order_id"));
            if(!session.optString("order_id").isEmpty()&&!session.optString("order_id").equals(payload.optString("order_id")))throw new IllegalArgumentException("Der Auftrag der gestarteten Sitzung bleibt erhalten.");
            if(!session.optString("customer_id").isEmpty()&&!session.optString("customer_id").equals(payload.optString("customer_id")))throw new IllegalArgumentException("Die Sitzung gehört zu einem anderen Einsatzkunden.");
            if(!payload.has("billing_customer_id")&&!session.optString("billing_customer_id").isEmpty())payload.put("billing_customer_id",session.getString("billing_customer_id"));
            if(payload.optString("location").trim().isEmpty()&&payload.optString("customer_id").equals(session.optString("customer_id")))payload.put("location",session.optString("location"));
            String saved=saveWorklog(payload);
            JSONObject photos=PixelMcpReadTools.effective(getState());JSONArray sessionPhotos=photos.getJSONArray("photos");
            for(int i=0;i<sessionPhotos.length();i++){JSONObject old=sessionPhotos.getJSONObject(i);if(!sessionId.equals(old.optString("session_id"))||!old.optString("worklog_id").isEmpty())continue;
                JSONObject linked=DriveJournalRules.clean(old);linked.put("worklog_id",saved);prepareManualChange("photo",linked,old);saveAction("photo","pho_mobile_",linked);}
            sessions.finalizeSession(sessionId,saved,requestId);database.setTransactionSuccessful();return saved;
            }finally{database.endTransaction();}
        }
        // Business action and replay receipt commit together in the existing database.
        // Receipts are internal metadata, never exported as Drive actions.
        @Override public JSONObject execute(String tool, JSONObject args) throws Exception {
            if(OfferTools.write(tool)||PixelMcpOwnerTools.write(tool))return new PixelOwnerAccess(context,this).execute(tool,args);
            if(PixelMcpSessionTools.handles(tool))return executeSession(tool,args);
            if(PixelMcpWriteTools.handles(tool))PixelMcpWriteTools.validate(args, PixelMcpWriteTools.schema(tool));
            else if(PixelMcpEntityWriteTools.handles(tool))PixelMcpEntityWriteTools.validate(tool,args);
            else throw new PixelMcpWriteTools.Rejected("UNKNOWN_TOOL","Diese Schreibfunktion ist nicht freigegeben.");
            String clientId = "mcp_pixel_" + PixelMcpWriteTools.hash(args.getString("request_id")).substring(0,40);
            String fingerprint = PixelMcpWriteTools.hash(new JSONObject().put("tool",tool).put("args",args));
            SQLiteDatabase database = getWritableDatabase(); database.beginTransaction();
            JSONObject result;
            try {
                JSONObject receipt = receipt(clientId);
                if (receipt != null) {
                    if (!fingerprint.equals(receipt.optString("fingerprint"))) throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Diese Anfragekennung wurde bereits für andere Angaben verwendet. Bitte die ursprüngliche Anfrage prüfen.");
                    result = new JSONObject().put("entity_type",receipt.optString("entity_type","worklog")).put("entity_id",receipt.optString("entity_id",receipt.optString("work_record_id"))).put("replayed",true).put("sync",status(clientId,getState()));
                } else {
                    if (snapshotText("").isEmpty()) throw new PixelMcpWriteTools.Rejected("NO_DATA","Bitte zuerst einen Drive-Abgleich durchführen.");
                    JSONObject prepared=PixelMcpWriteTools.handles(tool)?new JSONObject().put("entity_type","worklog").put("payload",PixelMcpWriteTools.prepare(tool,args,getState(),clientId)):PixelMcpEntityWriteTools.prepare(tool,args,getState(),clientId);
                    String entityType=prepared.getString("entity_type");JSONObject payload=prepared.getJSONObject("payload");
                    String source = payload.optString("source","ChatGPT Sprache");
                    String changedAt=isoNow();payload.put("updated_at",changedAt);if(!payload.has("created_at"))payload.put("created_at",changedAt);
                    Set<String> existingActions=new HashSet<>();JSONArray priorActions=pendingActions();for(int i=0;i<priorActions.length();i++)existingActions.add(priorActions.getJSONObject(i).getString("client_id"));
                    String entityId;
                    switch(entityType){
                        case "customer":entityId=saveCustomer(payload);break;
                        case "order":entityId=saveOrder(payload);break;
                        case "invoice":entityId=saveInvoiceDraft(payload);break;
                        default:entityId=saveWorklog(payload);
                    }
                    payload.put("source",source);
                    // Replace only the queue action just allocated by the shared saver, inside this transaction.
                    try (Cursor rows = database.rawQuery("SELECT id,payload FROM pending WHERE kind=?",new String[]{entityType})) {
                        while(rows.moveToNext()) if(!existingActions.contains(rows.getString(0))&&entityId.equals(new JSONObject(rows.getString(1)).optString("id"))) database.delete("pending","id=?",new String[]{rows.getString(0)});
                    }
                    ContentValues action = new ContentValues(); action.put("id",clientId); action.put("kind",entityType); action.put("payload",payload.toString()); action.put("created_at",isoNow());database.insertOrThrow("pending",null,action);
                    JSONObject metadata = new JSONObject().put("fingerprint",fingerprint).put("tool",tool).put("entity_type",entityType).put("entity_id",entityId).put("work_record_id",entityType.equals("worklog")?entityId:"").put("uploaded",false);
                    ContentValues saved = new ContentValues();saved.put("id","receipt_"+clientId);saved.put("kind","mcp_receipt");saved.put("payload",metadata.toString());saved.put("created_at",isoNow());database.insertOrThrow("pending",null,saved);
                    result = new JSONObject().put("entity_type",entityType).put("entity_id",entityId).put("replayed",false).put("sync",status(clientId,getState()));
                }
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            if (context instanceof MainActivity || context instanceof PixelTunnelService) requestMcpSync(context);
            if("worklog".equals(result.optString("entity_type")))result.put("work_record_id",result.getString("entity_id"));
            result.put("sync",PixelMcpBusinessTools.sync(result.getJSONObject("sync"),getState())).put("local_state","local_saved").put("sync_state",result.getJSONObject("sync").optString("sync_state"));
            return new JSONObject().put("ok",true).put("data",result).put("message","Auf dem Pixel gesichert. Der mobile Drive-Abgleich und die App-Bestätigung werden getrennt geprüft.");
        }
        private JSONObject receipt(String clientId) throws Exception {
            try(Cursor row=getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE id=? AND kind='mcp_receipt'",new String[]{"receipt_"+clientId})) { return row.moveToFirst()?new JSONObject(row.getString(0)):null; }
        }
        private JSONObject executeSession(String tool,JSONObject args)throws Exception{
            PixelMcpWriteTools.validate(args,PixelMcpSessionTools.schema(tool));
            String clientId="mcp_pixel_"+PixelMcpWriteTools.hash(args.getString("request_id")).substring(0,40),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("tool",tool).put("args",args));
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();JSONObject result;
            try{
                JSONObject existing=receipt(clientId);if(existing!=null){if(!fingerprint.equals(existing.optString("fingerprint")))throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Anfragekennung wurde für andere Angaben verwendet.");result=new JSONObject(existing.getJSONObject("result").toString()).put("replayed",true);}
                else{
                    JSONObject payload=PixelMcpSessionTools.prepare(tool,args,getState()),session;WorkSessionStore timer=new WorkSessionStore(database,context);String id=args.optString("session_id"),request="mcp-session-"+clientId;
                    switch(tool){case "start_work":payload.put("request_id",request);session=startWorkSession(payload);break;case "start_break":session=timer.pause(id,request);break;case "end_break":session=timer.resume(id,request);break;case "stop_work":session=timer.stop(id,request);break;case "continue_work":session=timer.continueStopped(id,request);break;case "discard_work_session":session=timer.discard(id,request);break;case "restore_work_session":session=timer.restore(id,request);break;default:
                        payload.remove("request_id");payload.remove("session_id");String saved=completeWorkSession(id,payload,request);payload.put("id",saved).put("_mcp_create",true);
                        try(Cursor rows=database.rawQuery("SELECT id,payload FROM pending WHERE kind='worklog'",null)){while(rows.moveToNext())if(saved.equals(new JSONObject(rows.getString(1)).optString("id")))database.delete("pending","id=?",new String[]{rows.getString(0)});}
                        ContentValues action=new ContentValues();action.put("id",clientId);action.put("kind","worklog");action.put("payload",payload.toString());action.put("created_at",isoNow());database.insertOrThrow("pending",null,action);session=timer.current(id);
                    }
                    result=new JSONObject().put("client_id",clientId).put("session",session).put("replayed",false).put("local_state","local_saved").put("sync_state",tool.equals("complete_work_session")?"sync_pending":"not_applicable").put("requires_work_record_completion",tool.equals("stop_work"));
                    JSONObject meta=new JSONObject().put("fingerprint",fingerprint).put("tool",tool).put("entity_type",tool.equals("complete_work_session")?"worklog":"work_session").put("entity_id",tool.equals("complete_work_session")?session.optString("finalized_worklog_id"):session.getString("id")).put("work_record_id",session.optString("finalized_worklog_id")).put("uploaded",false).put("result",result);
                    ContentValues row=new ContentValues();row.put("id","receipt_"+clientId);row.put("kind","mcp_receipt");row.put("payload",meta.toString());row.put("created_at",isoNow());database.insertOrThrow("pending",null,row);
                }
                database.setTransactionSuccessful();
            }finally{database.endTransaction();}
            if(tool.equals("complete_work_session")){if(context instanceof MainActivity || context instanceof PixelTunnelService)requestMcpSync(context);result.put("sync",status(clientId,getState()));}
            return new JSONObject().put("ok",true).put("data",result).put("message",tool.equals("stop_work")?"Zeiterfassung lokal gestoppt. Tätigkeit prüfen und mit complete_work_session als Arbeitsaufzeichnung abschließen.":"Aktion dauerhaft auf dem Pixel gesichert. Nur Arbeitsaufzeichnungen werden über Drive übertragen; Timerstatus ist lokal.");
        }
        @Override public JSONObject status(String clientId,JSONObject state) throws Exception {
            JSONObject master=state.getJSONObject("master"),metadata=receipt(clientId);boolean ownerSaved=!ownerValue("receipt:"+clientId,"").isEmpty();
            if(metadata!=null&&"work_session".equals(metadata.optString("entity_type")))return new JSONObject().put("client_id",clientId).put("status","local_saved").put("sync_state","not_applicable").put("local_state","local_saved").put("saved_locally",true).put("confirmed_by_app",false).put("uploaded_to_drive",false).put("entity_id",metadata.optString("entity_id")).put("message","Timeraktion dauerhaft lokal gespeichert; keine Drive-Arbeitsaufzeichnung. Aktuellen Zustand mit get_active_work_session lesen.");
            String phase="unknown";JSONArray accepted=master.optJSONArray("accepted_ids");
            for(int i=0;accepted!=null&&i<accepted.length();i++)if(clientId.equals(accepted.optString(i)))phase="accepted";
            if(!phase.equals("accepted")) {
                JSONArray pending=state.optJSONArray("pending");boolean queued=false;
                for(int i=0;pending!=null&&i<pending.length();i++)if(clientId.equals(pending.getJSONObject(i).optString("client_id")))queued=true;
                if(queued)phase=metadata!=null&&metadata.optBoolean("uploaded")?"transferred":"queued";
                else if(metadata!=null)phase="superseded";
                if(DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId))phase="transferred";
                JSONArray errors=master.optJSONArray("sync_errors");for(int i=0;errors!=null&&i<errors.length();i++){JSONObject error=errors.optJSONObject(i);if(error!=null&&clientId.equals(error.optString("client_id")))phase="failed";}
                JSONArray journalErrors=master.optJSONArray("drive_journal_errors");for(int i=0;journalErrors!=null&&i<journalErrors.length();i++){JSONObject error=journalErrors.optJSONObject(i);if(error!=null&&clientId.equals(error.optString("client_id")))phase="failed";}
            }
            if(master.optJSONObject("drive_resolved_actions")!=null&&master.getJSONObject("drive_resolved_actions").has(clientId))phase="resolved";
            return PixelMcpBusinessTools.sync(new JSONObject().put("client_id",clientId).put("status",phase).put("confirmed_by_app",phase.equals("accepted"))
                .put("saved_locally",metadata!=null||ownerSaved).put("uploaded_to_drive",phase.equals("accepted")||phase.equals("transferred")||(metadata!=null&&metadata.optBoolean("uploaded")))
                .put("entity_type",metadata==null?"":metadata.optString("entity_type","worklog"))
                .put("entity_id",metadata==null?"":metadata.optString("entity_id",metadata.optString("work_record_id")))
                .put("work_record_id",metadata==null?"":metadata.optString("work_record_id"))
                .put("applied_on_pixel",DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId))
                .put("confirmation_scope",phase.equals("accepted")?"web_import_receipt":metadata!=null&&metadata.optBoolean("uploaded")?"drive_provider_readback":"local_only")
                .put("cloud_confirmed",false)
                .put("message",phase.equals("resolved")?"Die Konfliktentscheidung wurde übernommen. Die ursprüngliche Aktion bleibt im Journal erhalten.":phase.equals("accepted")?"Im Gesamtstand übernommen und bestätigt. Cloud-Upload wird durch Drive durchgeführt.":phase.equals("failed")?"Die Änderung benötigt eine Prüfung. Lokale Daten bleiben erhalten.":phase.equals("superseded")?"Die vorgemerkte Änderung wurde in der App ersetzt. Bitte den Eintrag öffnen.":phase.equals("transferred")?"Änderung an die Drive-App übergeben und zurückgelesen; auf dem Pixel nutzbar. Der Google-Cloud-Upload ist nicht unabhängig bestätigt.":phase.equals("queued")?"Auf dem Pixel gesichert. Drive-Abgleich steht noch aus.":"Für diese Anfrage liegt noch keine Bestätigung vor."),state);
        }
        void markUploaded(JSONArray actions) throws Exception {
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try {for(int i=0;i<actions.length();i++){String clientId=actions.getJSONObject(i).optString("client_id");JSONObject metadata=receipt(clientId);if(metadata!=null){metadata.put("uploaded",true);ContentValues value=new ContentValues();value.put("payload",metadata.toString());database.update("pending",value,"id=? AND kind='mcp_receipt'",new String[]{"receipt_"+clientId});}}database.setTransactionSuccessful();}finally{database.endTransaction();}
        }
        private JSONObject findById(JSONArray rows, String id) throws Exception {
            if (rows == null) return null;
            for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && id.equals(row.optString("id"))) return row; }
            return null;
        }
        private boolean containsId(JSONArray rows, String id) throws Exception { return findById(rows, id) != null; }

        // Validate against the same local snapshot + pending overlay displayed in the UI.
        // This allows a customer/order created offline to be used before the next Drive sync.
        private JSONArray effectiveRows(JSONObject master, String collection, String kind) throws Exception {
            java.util.LinkedHashMap<String, JSONObject> rows = new java.util.LinkedHashMap<>();
            JSONArray base = master.optJSONArray(collection);
            if (base != null) for (int i = 0; i < base.length(); i++) {
                JSONObject row = base.optJSONObject(i);
                if (row != null) rows.put(row.optString("id"), new JSONObject(row.toString()));
            }
            JSONArray actions = pendingActions();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i), payload = action.optJSONObject("payload");
                if (payload == null) continue;
                String id = payload.optString("id");
                if (kind.equals(action.optString("kind"))) {
                    JSONObject row = rows.containsKey(id) ? rows.get(id) : new JSONObject();
                    java.util.Iterator<String> keys = payload.keys();
                    while (keys.hasNext()) { String key = keys.next(); row.put(key, payload.get(key)); }
                    rows.put(id, row);
                }
            }
            // Keep deletion precedence identical to the existing merged() JavaScript view.
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i), payload = action.optJSONObject("payload");
                if (payload != null && "delete".equals(action.optString("kind")) && kind.equals(payload.optString("entity"))) rows.remove(payload.optString("id"));
            }
            JSONArray result = new JSONArray();
            for (JSONObject row : rows.values()) result.put(row);
            return result;
        }

        String saveCustomer(JSONObject payload) throws Exception {
            if (payload.optString("name").trim().isEmpty()) throw new IllegalArgumentException("Der Kundenname fehlt.");
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try {
            JSONObject master=new JSONObject(snapshotText(readAsset("initial-data.json")));
            JSONArray customers=effectiveRows(master,"customers","customer");
            payload=ManualChangeRules.editor(payload,findById(customers,payload.optString("id")),"customer");
            GardenRules.normalize(payload);
            BillingRules.customerConfig(payload,customers);
            if(findById(customers,payload.optString("id"))==null)EntityProtectionRules.validateNewCustomer(payload,customers);
            String entityId = payload.optString("id");
            if (entityId.isEmpty()) entityId = "cus_mobile_" + UUID.randomUUID().toString().replace("-", "");
            payload.put("id", entityId);
            prepareManualChange("customer",payload,findById(customers,entityId));
            String actionId = "customer_change_" + UUID.randomUUID().toString().replace("-", "");
            ContentValues values = new ContentValues(); values.put("id", actionId); values.put("kind", "customer"); values.put("payload", payload.toString()); values.put("created_at", isoNow());
            database.insertOrThrow("pending", null, values);database.setTransactionSuccessful();
            return entityId;
            } finally { database.endTransaction(); }
        }
        String saveReviewedOrder(JSONObject request)throws Exception {
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try{JSONObject payload=OrderReviewRules.prepare(getState(),request);String id=saveAction("order","ord_mobile_",payload);database.setTransactionSuccessful();return id;}
            finally{database.endTransaction();}
        }
        String saveOrder(JSONObject payload) throws Exception {
            SQLiteDatabase tx=getWritableDatabase();tx.beginTransaction();try{String result=saveOrderLocked(payload);tx.setTransactionSuccessful();return result;}finally{tx.endTransaction();}
        }
        private String saveOrderLocked(JSONObject payload) throws Exception {
            JSONObject current=PixelMcpReadTools.effective(getState());payload=ManualChangeRules.editor(payload,findById(current.getJSONArray("orders"),payload.optString("id")),"order");
            BillingRules.order(payload,current);
            if (payload.optString("customer_id").isEmpty() || payload.optString("title").trim().isEmpty()) throw new IllegalArgumentException("Kunde und Auftragsbezeichnung sind erforderlich.");
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try {
            JSONObject master = new JSONObject(snapshotText(readAsset("initial-data.json")));
            if (!containsId(effectiveRows(master,"customers","customer"), payload.optString("customer_id"))) throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
            if (!payload.optString("scheduled_date").isEmpty() && !payload.isNull("scheduled_date")) {
                try { java.time.LocalDate.parse(payload.optString("scheduled_date")); } catch (Exception error) { throw new IllegalArgumentException("Bitte ein gültiges Auftragsdatum auswählen."); }
            }
            String entityId = payload.optString("id");
            EntityProtectionRules.validateOrderChange(payload,findById(effectiveRows(master,"orders","order"),entityId),effectiveRows(master,"worklogs","worklog"),effectiveRows(master,"invoices","invoice"),effectiveRows(master,"photos","photo"));
            if (entityId.isEmpty()) entityId = "ord_mobile_" + UUID.randomUUID().toString().replace("-", "");
            payload.put("id", entityId); payload.put("source", "Pixel offline");
            prepareManualChange("order",payload,findById(effectiveRows(master,"orders","order"),entityId));
            String saved=saveAction("order", "ord_mobile_", payload);database.setTransactionSuccessful();return saved;
            } finally { database.endTransaction(); }
        }
        String saveInvoiceDraft(JSONObject payload) throws Exception {
            SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
            try{JSONObject all=PixelMcpReadTools.effective(getState());String id=payload.optString("id");if(id.isEmpty())id="inv_mobile_"+UUID.randomUUID().toString().replace("-","");payload.put("id",id);
                JSONObject old=findById(all.getJSONArray("invoices"),id);if(old!=null&&(all.optJSONObject("capabilities")==null||!all.getJSONObject("capabilities").optBoolean("invoice_compare_and_save")))throw new PixelMcpWriteTools.Rejected("REMOTE_GUARD_REQUIRED","Zuerst gemeinsame Web-/Drive-Verarbeitung aktualisieren und abgleichen. Rechnungsvergleichsschutz fehlt.");payload=ManualChangeRules.editor(payload,old,"invoice");
                for(int i=0;i<getState().getJSONArray("pending").length();i++){JSONObject a=getState().getJSONArray("pending").getJSONObject(i);if(java.util.Arrays.asList("invoice_issue","owner_invoice_issue").contains(a.optString("kind"))&&id.equals(a.getJSONObject("payload").optString("id")))throw new IllegalArgumentException("Rechnungsabschluss ist bereits vorgemerkt.");}
                InvoiceDraftRules.normalize(payload,old,all);if(payload.optString("invoice_number").isEmpty())payload.put("invoice_number","Entwurf-"+id);
                prepareManualChange("invoice",payload,old);String result=saveAction("invoice","inv_mobile_",payload);db.setTransactionSuccessful();return result;
            }finally{db.endTransaction();}
        }
        synchronized String savePhoto(JSONObject payload) throws Exception {
            if (payload.optString("customer_id").isEmpty() || payload.optString("data").isEmpty()) throw new IllegalArgumentException("Kunde und Foto sind erforderlich.");
            if(!payload.optString("capture_time").isEmpty())java.time.Instant.parse(payload.getString("capture_time"));
            JSONObject immutableRequest=new JSONObject(payload.toString());immutableRequest.remove("thumbnail_data");String request=payload.optString("_photo_request_id"),fingerprint=PixelMcpWriteTools.hash(immutableRequest);
            if(!request.isEmpty()&&!request.matches("[A-Za-z0-9_-]{8,128}"))throw new IllegalArgumentException("Ungültige Foto-Anfragekennung.");
            if(!request.isEmpty()){String saved=ownerValue("photo-ui:"+request,"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);if(!fingerprint.equals(receipt.getString("hash")))throw new IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.");return receipt.getString("id");}}
            String entityId=payload.optString("id");if(entityId.isEmpty())entityId="pho_mobile_"+UUID.randomUUID().toString().replace("-","");payload.put("id",entityId);
            PhotoAttachmentStore.Prepared prepared=null;boolean committed=false;
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try {
                if(!request.isEmpty()){String saved=ownerValue("photo-ui:"+request,"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);if(!fingerprint.equals(receipt.getString("hash")))throw new IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.");database.setTransactionSuccessful();committed=true;return receipt.getString("id");}}
                JSONObject master=new JSONObject(snapshotText(readAsset("initial-data.json")));
                if(!payload.optString("session_id").isEmpty()){
                    JSONObject session=new WorkSessionStore(database,context).current(payload.getString("session_id"));
                    if(!payload.getString("customer_id").equals(session.optString("customer_id"))||!payload.optString("order_id").equals(session.optString("order_id")))throw new IllegalArgumentException("Foto und gestartete Sitzung gehören nicht zum selben Kunden/Auftrag.");
                    if(!session.optString("finalized_worklog_id").isEmpty())payload.put("worklog_id",session.getString("finalized_worklog_id"));
                }
                EntityProtectionRules.validatePhoto(payload,effectiveRows(master,"customers","customer"),effectiveRows(master,"orders","order"),effectiveRows(master,"worklogs","worklog"));
                payload.put("_photo_quality",new JSONObject(ownerValue("settings","{}")).optString("photo_quality","optimized"));
                prepared=photoAttachments.prepare(payload);
                prepareManualChange("photo",payload,findById(effectiveRows(master,"photos","photo"),payload.optString("id")));
                for(java.util.Iterator<String> keys=prepared.metadata.keys();keys.hasNext();){String key=keys.next();payload.put(key,prepared.metadata.get(key));}
                String original=payload.optString("original_name","foto.jpg").replace('\\','/');int slash=original.lastIndexOf('/');if(slash>=0)original=original.substring(slash+1);if(original.length()>240)original=original.substring(0,240);payload.put("original_name",original);
                payload.remove("data");payload.remove("_photo_quality");payload.put("sync_state","local_saved");payload.put("filename",prepared.metadata.getString("storage_name"));
                payload.remove("thumbnail_data");
                saveAttachmentMetadata(database,entityId,prepared.metadata,"local");
                String saved=saveAction("photo", "pho_mobile_", payload);if(!request.isEmpty())putOwnerValue("photo-ui:"+request,new JSONObject().put("hash",fingerprint).put("id",saved).toString());database.setTransactionSuccessful();committed=true;return saved;
            } finally {
                database.endTransaction();
                if(!committed&&prepared!=null&&prepared.created){
                    try(Cursor referenced=getReadableDatabase().rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? LIMIT 1",new String[]{prepared.metadata.getString("content_sha256")})){
                        if(!referenced.moveToFirst())photoAttachments.discardCreated(prepared.metadata);
                    }catch(Exception ignored){/* A verified orphan is safer than masking the original failed save. */}
                }
            }
        }
        private void saveAttachmentMetadata(SQLiteDatabase database,String photoId,JSONObject metadata,String state)throws Exception{
            String hash=metadata.optString("content_sha256"),mime=PhotoAttachmentStore.canonicalMime(metadata.optString("mime_type")),name=metadata.optString("storage_name"),role=metadata.optString("role","documentation").trim().toLowerCase(Locale.ROOT);
            String extension=mime.equals("image/jpeg")?".jpg":mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":mime.equals("image/gif")?".gif":mime.equals("image/heic")?".heic":"";
            if(photoId==null||photoId.trim().isEmpty()||photoId.length()>180||!PhotoAttachmentStore.canonicalName(name)||!hash.matches("[0-9a-f]{64}")||!name.equals(hash+extension)||metadata.optLong("byte_size")<1||metadata.optLong("byte_size")>PhotoAttachmentStore.MAX_BYTES||!new HashSet<>(java.util.Arrays.asList("profile","documentation","before","during","after","damage","other")).contains(role))throw new IllegalArgumentException("Ungültige lokale Foto-Metadaten.");
            ContentValues values=new ContentValues();values.put("photo_id",photoId);values.put("content_sha256",hash);values.put("mime_type",mime);values.put("byte_size",metadata.getLong("byte_size"));values.put("storage_name",name);values.put("role",role);values.put("transfer_state",state);values.put("created_at",isoNow());
            database.insertWithOnConflict("photo_attachments",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        }
        void restoreAttachmentMetadata(String photoId,JSONObject metadata)throws Exception{saveAttachmentMetadata(getWritableDatabase(),photoId,metadata,"local");}
        JSONObject photoAttachment(String photoId)throws Exception{
            try(Cursor row=getReadableDatabase().rawQuery("SELECT photo_id,content_sha256,mime_type,byte_size,storage_name,role,transfer_state,created_at FROM photo_attachments WHERE photo_id=?",new String[]{photoId})){
                if(!row.moveToFirst())return null;return new JSONObject().put("photo_id",row.getString(0)).put("content_sha256",row.getString(1)).put("mime_type",row.getString(2)).put("byte_size",row.getLong(3)).put("storage_name",row.getString(4)).put("role",row.getString(5)).put("transfer_state",row.getString(6)).put("created_at",row.getString(7));
            }
        }
        PhotoAttachmentStore photoAttachmentStore(){return photoAttachments;}
        private Set<String> persistedConfirmedActionIds(){
            Set<String> confirmed=new HashSet<>();
            try{
                JSONObject snapshot=new JSONObject(preferences.getString("drive_snapshot","{}"));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids")));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
                confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
                JSONObject resolved=snapshot.optJSONObject("drive_resolved_actions");
                if(resolved!=null){Iterator<String> keys=resolved.keys();while(keys.hasNext())confirmed.add(keys.next());}
            }catch(Exception ignored){/* Missing/corrupt receipts must never make an unconfirmed attachment skippable. */}
            return confirmed;
        }
        JSONArray pendingPhotoAttachments(JSONArray actions)throws Exception{
            JSONArray result=new JSONArray();Set<String> confirmed=persistedConfirmedActionIds();for(int i=0;i<actions.length();i++){JSONObject action=actions.getJSONObject(i),payload=action.optJSONObject("payload");if(!"photo".equals(action.optString("kind"))||payload==null||!payload.optString("data").isEmpty())continue;JSONObject metadata=photoAttachment(payload.optString("id"));if(metadata==null){
                    // The committed Drive snapshot is durable before confirmed-delete GC.
                    // If the process died before acknowledge(), a stale confirmed create
                    // may remain in pending without its now-deleted private attachment.
                    if(confirmed.contains(action.optString("client_id")))continue;
                    result.put(new JSONObject().put("photo_id",payload.optString("id")).put("missing",true));continue;
                }for(String key:new String[]{"thumbnail_storage_name","thumbnail_sha256"})if(payload.has(key))metadata.put(key,payload.get(key));result.put(metadata);}
            return result;
        }
        void markPhotoAttachmentTransferred(String photoId){ContentValues values=new ContentValues();values.put("transfer_state","provider_readback");getWritableDatabase().update("photo_attachments",values,"photo_id=?",new String[]{photoId});}
        void recordSnapshotPhotoAttachments(JSONObject snapshot)throws Exception{
            JSONArray photos=snapshot.optJSONArray("photos");if(photos==null)return;SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try{
                for(int i=0;i<photos.length();i++){
                    JSONObject photo=photos.optJSONObject(i);if(photo==null||photo.optString("id").isEmpty())continue;
                    JSONObject local=photoAttachment(photo.optString("id"));
                    String inline=photo.optString("data");if(inline.isEmpty())inline=photo.optString("full_data");
                    if(!inline.isEmpty()){
                        PhotoAttachmentStore.Prepared prepared=photoAttachments.prepareLegacy(new JSONObject(photo.toString()).put("data",inline));
                        if(local!=null&&!local.getString("content_sha256").equals(prepared.metadata.getString("content_sha256"))){
                            if(prepared.created)photoAttachments.discardCreated(prepared.metadata);
                            throw new IllegalArgumentException("Die Foto-ID verweist auf verschiedene Originale. Abgleich angehalten.");
                        }
                        if(local==null){saveAttachmentMetadata(database,photo.getString("id"),prepared.metadata,"legacy_inline_snapshot");local=new JSONObject(prepared.metadata.toString()).put("photo_id",photo.getString("id"));}
                    }
                    photo.remove("data");photo.remove("full_data");
                    if(local!=null&&(!photo.optString("content_sha256").matches("[0-9a-f]{64}")||!PhotoAttachmentStore.canonicalName(photo.optString("storage_name")))){
                        for(String key:new String[]{"content_sha256","mime_type","byte_size","storage_name","role"})photo.put(key,local.get(key));
                    }
                    if(!photo.optString("content_sha256").matches("[0-9a-f]{64}")||!PhotoAttachmentStore.canonicalName(photo.optString("storage_name")))continue;
                    boolean keepExisting=false;
                    try(Cursor existing=database.rawQuery("SELECT content_sha256,transfer_state FROM photo_attachments WHERE photo_id=?",new String[]{photo.getString("id")})){
                        if(existing.moveToFirst()){
                            boolean sameHash=existing.getString(0).equals(photo.getString("content_sha256"));
                            boolean authoritativeLocal=!"remote_available".equals(existing.getString(1));
                            keepExisting=sameHash||authoritativeLocal;
                        }
                    }
                    if(keepExisting)continue;
                    JSONObject metadata=new JSONObject().put("content_sha256",photo.getString("content_sha256")).put("mime_type",photo.getString("mime_type")).put("byte_size",photo.getLong("byte_size")).put("storage_name",photo.getString("storage_name")).put("role",photo.optString("role","documentation"));
                    saveAttachmentMetadata(database,photo.getString("id"),metadata,"remote_available");
                }
                database.setTransactionSuccessful();
            }finally{database.endTransaction();}
        }
        void cleanupConfirmedPhotoDeletes(JSONArray submitted,Set<String> confirmed,JSONObject snapshot)throws Exception{
            Set<String> active=new HashSet<>(),protectedPending=new HashSet<>(),candidates=new HashSet<>();
            JSONArray photos=snapshot.optJSONArray("photos");for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null&&!photo.optString("id").isEmpty())active.add(photo.optString("id"));}
            for(int i=0;i<submitted.length();i++){
                JSONObject action=submitted.optJSONObject(i),payload=action==null?null:action.optJSONObject("payload");if(payload==null)continue;
                String client=action.optString("client_id"),kind=action.optString("kind"),id=payload.optString("id");
                if("photo".equals(kind)&&!confirmed.contains(client)&&!id.isEmpty())protectedPending.add(id);
                if("delete".equals(kind)&&"photo".equals(payload.optString("entity"))&&confirmed.contains(client)&&!id.isEmpty())candidates.add(id);
            }
            candidates.removeAll(active);candidates.removeAll(protectedPending);
            if(candidates.isEmpty())return;
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try{
                for(String photoId:candidates){
                    JSONObject metadata=photoAttachment(photoId);if(metadata==null)continue;String hash=metadata.getString("content_sha256");boolean shared;
                    try(Cursor references=database.rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? AND photo_id<>? LIMIT 1",new String[]{hash,photoId})){shared=references.moveToFirst();}
                    // Physical originals are removed only after the delete receipt is confirmed,
                    // the committed snapshot no longer contains the photo, and no other row shares it.
                    if(!shared)photoAttachments.discardCreated(metadata);
                    database.delete("photo_attachments","photo_id=?",new String[]{photoId});
                }
                database.setTransactionSuccessful();
            }finally{database.endTransaction();}
        }
        String loadPhotoData(String photoId)throws Exception{
            JSONObject metadata=photoAttachment(photoId);if(metadata==null)throw new IllegalArgumentException("Für dieses Foto ist noch kein dauerhaftes Original hinterlegt.");
            try{return photoAttachments.dataUri(metadata);}catch(Exception missing){if(!DriveSyncCoordinator.fetchPhoto(context,metadata,photoAttachments))throw missing;return photoAttachments.dataUri(metadata);}
        }
        private void prepareManualChange(String kind,JSONObject payload,JSONObject previous)throws Exception{
            ManualChangeRules.prepare(kind,payload,previous,isoNow());
            JSONArray queued=pendingActions(),dependencies=new JSONArray();String last="";
            for(int i=0;i<queued.length();i++){JSONObject a=queued.getJSONObject(i),p=a.getJSONObject("payload");String type=a.optString("kind");if(kind.equals(type)&&payload.optString("id").equals(p.optString("id")))last=a.getString("client_id");
                if(p.optBoolean("_mcp_create"))for(String[] ref:new String[][]{{"customer_id","customer"},{"billing_customer_id","customer"},{"order_id","order"},{"worklog_id","worklog"}})if(ref[1].equals(type)&&!payload.optString(ref[0]).isEmpty()&&payload.optString(ref[0]).equals(p.optString("id")))dependencies.put(a.getString("client_id"));}
            if(!last.isEmpty())dependencies.put(last);if(dependencies.length()>0)payload.put("_sync_predecessors",dependencies);
        }
        String removeEntity(String entity, String entityId) throws Exception {
            if(entity.equals("customer")){
                JSONObject all=PixelMcpReadTools.effective(getState());
                for(String type:new String[]{"customers","orders","worklogs"}){JSONArray rows=all.optJSONArray(type);for(int i=0;rows!=null&&i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(entityId.equals(BillingRules.text(r,type.equals("customers")?"default_billing_customer_id":"billing_customer_id"))&&!entityId.equals(r.optString("customer_id")))throw new IllegalArgumentException("Dieser Kunde wird als Rechnungsempfänger verwendet. Bitte zuerst die Abrechnungszuordnungen prüfen.");}}
            }
            if (!new HashSet<>(java.util.Arrays.asList("customer", "order", "worklog", "invoice", "photo")).contains(entity))
                throw new IllegalArgumentException("Dieser Eintrag kann hier nicht entfernt werden.");
            if (entityId == null || entityId.trim().isEmpty()) throw new IllegalArgumentException("Der Eintrag fehlt.");
            SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
            try {
            if("invoice".equals(entity)){
                JSONObject master=new JSONObject(snapshotText(readAsset("initial-data.json")));
                EntityProtectionRules.validateInvoiceDelete(findById(effectiveRows(master,"invoices","invoice"),entityId));
            }
            JSONObject current=OwnerJournalRules.findOrNull(PixelMcpReadTools.effective(getState()).getJSONArray(OwnerJournalRules.group(entity)),entityId);if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt.");
            OwnerJournalRules.validateArchive(entity,current,PixelMcpReadTools.effective(getState()));
            JSONObject payload=new JSONObject().put("entity",entity).put("id",entityId).put("expected_record_hash",OwnerJournalRules.version(current)).put("_record_base",DriveJournalRules.guard(current,entity)).put("_archive_record",current).put("updated_at",isoNow()).put("_undo_until",System.currentTimeMillis()+UNDO_DELETE_WINDOW_MS);
            JSONArray prior=pendingActions(),deps=new JSONArray();for(int i=0;i<prior.length();i++){JSONObject a=prior.getJSONObject(i);if(entity.equals(a.optString("kind"))&&entityId.equals(a.getJSONObject("payload").optString("id")))deps.put(a.getString("client_id"));}if(deps.length()>0)payload.put("_sync_predecessors",deps);
            String saved=saveAction("archive", "archive_mobile_", payload);database.setTransactionSuccessful();return saved;
            } finally { database.endTransaction(); }
        }
        String undoRemoveEntity(String entity, String entityId) throws Exception {
            SQLiteDatabase database = getWritableDatabase();
            try (Cursor cursor = database.rawQuery("SELECT id,payload FROM pending WHERE kind IN ('delete','archive') ORDER BY created_at DESC", null)) {
                while (cursor.moveToNext()) {
                    JSONObject payload = new JSONObject(cursor.getString(1));
                    if (entity.equals(payload.optString("entity")) && entityId.equals(payload.optString("id"))) {
                        database.delete("pending", "id=?", new String[]{cursor.getString(0)});
                        return "CANCELLED";
                    }
                }
            }
            throw new IllegalStateException("Der Eintrag ist bereits abgeglichen oder kann nicht mehr rückgängig gemacht werden.");
        }
        long remainingDeleteUndoMs() {
            long remaining = 0;
            try (Cursor cursor = getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE kind IN ('delete','archive')", null)) {
                while (cursor.moveToNext()) {
                    JSONObject payload = new JSONObject(cursor.getString(0));
                    remaining = Math.max(remaining, payload.optLong("_undo_until", 0) - System.currentTimeMillis());
                }
            } catch (Exception ignored) {}
            return Math.max(0, remaining);
        }
        String saveAction(String kind, String prefix, JSONObject payload) throws Exception {
            String id = payload.optString("id"); if (id.isEmpty()) id = prefix + UUID.randomUUID().toString().replace("-", "");
            payload.put("id", id);
            // Entity IDs are stable. Each change must have a fresh receipt ID, including deletions.
            ContentValues values = new ContentValues(); values.put("id", kind + "_change_" + UUID.randomUUID()); values.put("kind", kind); values.put("payload", payload.toString()); values.put("created_at", isoNow());
            getWritableDatabase().insertOrThrow("pending", null, values); return id;
        }
        JSONArray pendingActions() throws Exception {
            return pendingActions(false);
        }
        JSONArray pendingActionsForDrive() throws Exception {
            return pendingActions(true);
        }
        private JSONArray pendingActions(boolean forDrive) throws Exception {
            JSONArray result = new JSONArray();
            try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,kind,payload,created_at FROM pending WHERE kind!='mcp_receipt' ORDER BY created_at ASC,rowid ASC", null)) {
                while (cursor.moveToNext()) {
                    String kind=cursor.getString(1);JSONObject payload=new JSONObject(cursor.getString(2));
                    if(forDrive&&("delete".equals(kind)||"archive".equals(kind))&&payload.optLong("_undo_until",0)>System.currentTimeMillis())continue;
                    payload.remove("_undo_until");
                    if(!forDrive&&"photo".equals(kind)){
                        // WebView/MCP state stays lightweight. Legacy inline data remains only in the
                        // immutable Drive journal until acknowledged; originals are read on demand.
                        JSONObject metadata=photoAttachment(payload.optString("id"));
                        if(metadata!=null)for(Iterator<String> keys=metadata.keys();keys.hasNext();){String key=keys.next();if(!key.equals("photo_id")&&!key.equals("transfer_state")&&!key.equals("created_at"))payload.put(key,metadata.get(key));}
                        payload.remove("data");
                    }
                    JSONObject item=new JSONObject();item.put("client_id",cursor.getString(0));item.put("kind",kind);item.put("payload",payload);item.put("created_at",cursor.getString(3));result.put(item);
                }
            }
            return result;
        }
        void acknowledge(Set<String> ids) { SQLiteDatabase database = getWritableDatabase(); for (String id : ids) database.delete("pending", "id=?", new String[]{id}); }
    }

    private void synchronize() {
        if(DriveAccount.configured(this)){performDriveSync(Uri.EMPTY,true);return;}
        String saved = preferences.getString("drive_tree_uri", "");
        if (saved.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if(driveFolderPicking)return;driveFolderPicking=true;
            try{startActivityForResult(intent, DRIVE_FOLDER_REQUEST);}
            catch(ActivityNotFoundException|SecurityException error){driveFolderPicking=false;finishDriveSelection(false,"Ordnerauswahl nicht verfügbar. Lokale Daten bleiben erhalten.");}
        } else performDriveSync(Uri.parse(saved), true);
    }

    private void requestAutomaticSync(long delayMs) {
        long undoDelay = store == null ? 0 : store.remainingDeleteUndoMs();
        syncHandler.removeCallbacks(delayedAutomaticSync);
        syncHandler.postDelayed(delayedAutomaticSync, Math.max(delayMs, undoDelay > 0 ? undoDelay + 200 : 0));
    }

    private void automaticSyncIfConfigured() {
        if(preferences.getBoolean("drive_user_action_required",false)||preferences.getInt("drive_retry_count",0)>=8||preferences.getLong("drive_next_retry",0)>System.currentTimeMillis())return;
        if(DriveAccount.configured(this)){performDriveSync(Uri.EMPTY,false);return;}
        String saved = preferences.getString("drive_tree_uri", "");
        if (!saved.isEmpty()) performDriveSync(Uri.parse(saved), false);
    }

    private void performDriveSync(Uri treeUri, boolean showToast) {
        if (!syncInProgress.compareAndSet(false, true)) { syncAgain.set(true); return; }
        if (showToast) Toast.makeText(this, "Drive-Abgleich läuft …", Toast.LENGTH_SHORT).show();
        callback("window.onDriveSyncStarted", syncStatus());
        new Thread(() -> {
            JSONObject result;
            try {result=DriveSyncCoordinator.run(this,store);}
            catch(Exception error){result=new JSONObject();try{result.put("ok",false).put("error",DriveSyncCoordinator.friendly(error));}catch(Exception ignored){}}
            finally{syncInProgress.set(false);}
            final JSONObject completed=result;
            runOnUiThread(()->{
                try{completed.put("automatic",!showToast).put("sync",syncStatus());}catch(Exception ignored){}
                callback("window.onDriveSync",completed);
                if(syncAgain.getAndSet(false))requestAutomaticSync(500);
                else if(store.remainingDeleteUndoMs()>0)requestAutomaticSync(store.remainingDeleteUndoMs()+200);
            });
        }).start();
    }
    private void finishDriveSelection(boolean cancelled,String message){
        JSONObject result=new JSONObject();try{result.put("ok",false).put("cancelled",cancelled).put("error",message).put("state",store.getState()).put("sync",syncStatus());}catch(Exception ignored){}
        callback("window.onDriveSync",result);
    }

    private JSONObject syncStatus() {
        JSONObject status = new JSONObject();
        try {
            status.put("configured", DriveAccount.available(this));
            if(DriveAccount.configured(this)){String progress=store.ownerValue("drive-meta:"+preferences.getString("drive_folder_id","")+":progress","");if(!progress.isEmpty())status.put("transfer_progress",new JSONObject(progress));}
            status.put("transport",DriveAccount.configured(this)?"direct_drive":"android_provider").put("user_action_required",preferences.getBoolean("drive_user_action_required",false)).put("retry_count",preferences.getInt("drive_retry_count",0)).put("next_retry",preferences.getLong("drive_next_retry",0)).put("last_full_sync",preferences.getString("last_full_sync",""));
            status.put("syncing", syncInProgress.get()||DriveSyncCoordinator.running()||driveFolderPicking);
            status.put("last_sync", preferences.getString("last_drive_sync", ""));
            JSONObject current=store.getState(),queue=current.getJSONObject("sync_queue");
            status.put("pending",queue.getInt("pending_upload")).put("pending_total",queue.getInt("total")).put("review",queue.getInt("review"));
            int duplicateFiles=preferences.getInt("drive_duplicate_files",0);
            status.put("duplicate_files",duplicateFiles);
            status.put("warning",duplicateFiles>0?"Gleichnamige Änderungsdateien erkannt. Alle Aktionen bleiben erhalten; keine Datei automatisch gelöscht.":"");
            int conflicts=preferences.getInt("drive_conflict_count",0);status.put("conflicts",conflicts);
            if(conflicts>0)status.put("warning",conflicts+" Änderung(en) benötigen Prüfung. Gültige Daten sind erhalten; widersprüchliche Angaben werden nicht überschrieben.");
            status.put("last_error",preferences.getString("drive_last_error",""));
            status.put("confirmation_scope",preferences.getString("drive_confirmation_scope",""));
            status.put("cloud_confirmed",preferences.getBoolean("drive_cloud_confirmed",false)&&queue.getInt("total")==0);
        } catch (Exception ignored) {}
        return status;
    }

    private String readAsset(String name) throws Exception { return readStream(getAssets().open(name)); }
    private String readStream(InputStream stream) throws Exception { StringBuilder result = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) result.append(line); } return result.toString(); }
    private String isoNow() { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(new Date()); }
    private void callback(String function, JSONObject data) { runOnUiThread(() -> webView.evaluateJavascript(function + "(" + data.toString() + ")", null)); }

    public class SpeechBridge {
        @JavascriptInterface public void start() { runOnUiThread(() -> { Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN.toLanguageTag()); intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Arbeitsaufzeichnung sprechen"); try { startActivityForResult(intent, SPEECH_REQUEST); } catch (ActivityNotFoundException error) { Toast.makeText(MainActivity.this, "Keine Spracheingabe eingerichtet.", Toast.LENGTH_LONG).show(); } }); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==DRIVE_FOLDER_REQUEST){
            driveFolderPicking=false;
            if(resultCode!=RESULT_OK||data==null||data.getData()==null){finishDriveSelection(true,"Ordnerauswahl abgebrochen. Deine Daten bleiben lokal gespeichert.");return;}
        }
        if (requestCode == DRIVE_FOLDER_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri treeUri = data.getData();
            try {
                int permissions = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                if ((data.getFlags() & permissions) != permissions) throw new SecurityException();
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                preferences.edit().putString("drive_tree_uri", treeUri.toString()).apply(); performDriveSync(treeUri, true);
            } catch (SecurityException error) {
                Toast.makeText(this,"Bitte einen Ordner mit Lese- und Schreibzugriff freigeben. Deine Daten bleiben auf dem Gerät.",Toast.LENGTH_LONG).show();
                finishDriveSelection(false,"Drive-Zugriff fehlt. Bitte einen Ordner mit Lese- und Schreibzugriff auswählen.");
            }
        }
        if (requestCode == FILE_REQUEST) {
            Uri[] result = null;
            if (pendingCaptureUri != null) {
                Uri captured=pendingCaptureUri;File capturedFile=pendingCaptureFile;
                boolean valid=resultCode==RESULT_OK&&capturedFile!=null&&capturedFile.isFile()&&capturedFile.length()>0&&capturedFile.length()<=PhotoAttachmentStore.MAX_BYTES;
                // File inputs may be submitted much later (for example after completing a work form).
                // Keep a successful capture for the bounded 24-hour cache window; cancellation is immediate.
                if(valid){result=new Uri[]{captured};clearPendingCapture(false);syncHandler.postDelayed(()->{if(capturedFile.isFile())capturedFile.delete();},24L*60L*60L*1000L);}
                else {clearPendingCapture(true);if(resultCode==RESULT_OK)Toast.makeText(this,"Das Kamerafoto ist leer oder größer als 20 MB.",Toast.LENGTH_LONG).show();}
            } else if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    android.content.ClipData selected = data.getClipData();
                    ArrayList<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < selected.getItemCount(); i++) if (selected.getItemAt(i).getUri() != null) uris.add(selected.getItemAt(i).getUri());
                    if (!uris.isEmpty()) result = uris.toArray(new Uri[0]);
                } else if (data.getData() != null) result = new Uri[]{data.getData()};
            }
            ValueCallback<Uri[]> completed=fileCallback;fileCallback=null;
            if(completed!=null)completed.onReceiveValue(result);
        }
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) { ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if (matches != null && !matches.isEmpty()) webView.evaluateJavascript("window.acceptNativeSpeech(" + JSONObject.quote(matches.get(0)) + ")", null); }
    }
    private void navigateBack() {
        String currentUrl = webView.getUrl();
        if (currentUrl != null && currentUrl.startsWith("file:///android_asset/")) {
            webView.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : false", value -> {
                if (!"true".equals(value)) finish();
            });
            return;
        }
        if (webView.canGoBack()) webView.goBack(); else finish();
    }
    // Lint does not model the separately registered native API-33+ dispatcher.
    // This override is retained solely for the supported API-26–32 fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) navigateBack();
    }
    @Override protected void onDestroy() {
        if(workLocation!=null)workLocation.destroy();
        syncHandler.removeCallbacks(delayedAutomaticSync);
        syncHandler.removeCallbacks(periodicAutomaticSync);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(workLocation!=null)workLocation.permissionResult(code);
    }
}
