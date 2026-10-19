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

public class MainActivity extends Activity implements McpSyncHost {
    @Override public void requestMcpSync(){requestMcpSync(this);}
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
    private PixelLocalStore store;
    private PendingWorkPhotoStore pendingWorkPhotos;
    private WorkLocationController workLocation;
    private static java.lang.ref.WeakReference<MainActivity> foreground = new java.lang.ref.WeakReference<>(null);
    static void requestMcpSync(Context context) {
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
        store = new PixelLocalStore(this);
        DriveSyncJob.install(this);
        if(PlanningAccount.connected(this)||PlanningAccount.prefs(this).getBoolean("weather_enabled",false))PlanningJob.install(this);
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

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(webView!=null)webView.evaluateJavascript("window.PlanningUI&&window.PlanningUI.handleLaunch&&window.PlanningUI.handleLaunch()",null);}

    @Override protected void onResume() {
        foreground = new java.lang.ref.WeakReference<>(this);
        super.onResume();
        PlanningJob.schedule(this,1500);
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
        @JavascriptInterface public void connectPlanning(){runOnUiThread(()->startActivity(new Intent(MainActivity.this,PlanningConnectionActivity.class)));}
        @JavascriptInterface public String consumePlanningOpenContext(){try{if(!getIntent().getBooleanExtra("open_planning",false))return "{}";JSONObject result=new JSONObject().put("open",true).put("appointment_id",getIntent().getStringExtra("planning_appointment_id")).put("action",getIntent().getStringExtra("planning_action"));getIntent().removeExtra("open_planning");return result.toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public boolean consumePlanningOpen(){boolean open=getIntent().getBooleanExtra("open_planning",false);getIntent().removeExtra("open_planning");return open;}
        @JavascriptInterface public String planningTime(String value,String zone,String offset){try{return AppointmentRules.local(value,zone,offset).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
        @JavascriptInterface public String ownerCall(String name,String arguments){
            try{if(!PixelMcpOwnerTools.read(name)&&!PixelMcpOwnerTools.write(name)&&!OfferTools.read(name)&&!OfferTools.write(name)&&!AppointmentTools.read(name)&&!AppointmentTools.write(name))throw new IllegalArgumentException("Unbekannte Eigentümerfunktion.");PixelMcpReadTools tools=new PixelMcpReadTools(()->store.getState(),store,new PixelOwnerAccess(MainActivity.this,store));return tools.call(name,new JSONObject(arguments)).toString();}
            catch(Exception e){try{return new JSONObject().put("ok",false).put("error",new JSONObject().put("code",e instanceof PixelMcpWriteTools.Rejected?((PixelMcpWriteTools.Rejected)e).code:"VALIDATION_ERROR").put("message",e instanceof IllegalArgumentException?e.getMessage():"Vorgang nicht abgeschlossen. Sicherung, Versionen und Berechtigungen prüfen.")).toString();}catch(Exception ignored){return "{}";}}
        }
        @JavascriptInterface public String getSyncStatus() { return syncStatus().toString(); }
        @JavascriptInterface public String getSyncDetails(){try{JSONObject s=store.getState();return DriveJournalRules.queueStatus(s.getJSONObject("master"),s.getJSONArray("pending")).toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public String getSyncConflict(String id){try{return PixelMcpOwnerTools.conflicts(store.getState(),new JSONObject().put("limit",1).put("client_id",id)).toString();}catch(Exception e){return "ERROR: Konflikt konnte nicht vollständig gelesen werden.";}}
        @JavascriptInterface public String resolveSyncConflict(String request){try{return new PixelOwnerAccess(MainActivity.this,store).execute("resolve_sync_conflict",new JSONObject(request)).toString();}catch(Exception e){return "ERROR: "+e.getMessage();}}
        @JavascriptInterface public void connectDrive(){runOnUiThread(()->startActivity(new Intent(MainActivity.this,DriveConnectionActivity.class)));}
        @JavascriptInterface public void retryDrive(){runOnUiThread(()->MainActivity.this.synchronize());}
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
                info.put("build_fingerprint",BuildProvenance.describe());info.put("versionName", installed.versionName);
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
            try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE kind IN ('invoice_issue','owner_invoice_issue','pixel_invoice_issue_v1')",null)){
                while(cursor.moveToNext())if(identifier.equals(new JSONObject(cursor.getString(0)).optString("id")))throw new Exception("Abschluss bereits vorgemerkt. Bitte Drive-Abgleich abwarten.");
            }
        }
        @JavascriptInterface public void shareOffer(String id){
            new Thread(()->{try{JSONObject offer=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("offers"),id);TestDataRules.noDelivery(offer,PixelMcpReadTools.effective(store.getState()));byte[] bytes;try{bytes=new BusinessDocumentStore(MainActivity.this).read(offer);}catch(Exception missing){bytes=DriveSyncCoordinator.fetchDocument(MainActivity.this,offer);new BusinessDocumentStore(MainActivity.this).store(bytes,offer);}File dir=new File(getCacheDir(),"invoice-share");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Freigabeordner fehlt.");File target=new File(dir,"Angebot-"+UUID.randomUUID()+".pdf");try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){out.write(bytes);}Uri uri=Uri.parse("content://"+getPackageName()+".invoicefiles/"+target.getName());Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(android.content.ClipData.newRawUri("Angebot",uri));runOnUiThread(()->startActivity(Intent.createChooser(send,"Angebots-PDF teilen")));}catch(Exception error){runOnUiThread(()->Toast.makeText(MainActivity.this,"PDF noch nicht verfügbar. Dokument und Drive-Abgleich prüfen.",Toast.LENGTH_LONG).show());}}).start();
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


    private void synchronize() {
        try{DriveSyncCoordinator.prepareExplicit(this,"ui-sync-"+UUID.randomUUID());}catch(Exception ignored){}
        if(DriveAccount.configured(this)){performDriveSync(Uri.EMPTY,true);return;}
        String saved = preferences.getString("drive_tree_uri", "");
        if (saved.isEmpty()||!DriveAccount.providerPermission(this)) {
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
        try{if(!DriveSyncCoordinator.automaticAllowed(this))return;}catch(Exception invalid){return;}
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
        try {
            JSONObject current=store.getState(),status=DriveSyncCoordinator.status(this,current.getJSONObject("sync_queue"));
            status=SyncPolicy.describe(status,current.getJSONObject("sync_queue"),syncInProgress.get()||DriveSyncCoordinator.running()||driveFolderPicking,System.currentTimeMillis());
            int duplicates=preferences.getInt("drive_duplicate_files",0);status.put("duplicate_files",duplicates).put("warning",duplicates>0?"Gleichnamige Dateien prüfen. Originale bleiben erhalten.":"");
            if(DriveAccount.configured(this)){String progress=store.ownerValue("drive-meta:"+preferences.getString("drive_folder_id","")+":progress","");if(!progress.isEmpty())status.put("transfer_progress",new JSONObject(progress));}
            return status;
        }catch(Exception error){return new JSONObject();}
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
