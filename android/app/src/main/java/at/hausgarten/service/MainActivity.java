package at.hausgarten.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements McpSyncHost {
    @Override public void requestMcpSync(){requestMcpSync(this);}
    private static final int FILE_REQUEST = 41;
    private static final int SPEECH_REQUEST = 42;
    private static final int DRIVE_FOLDER_REQUEST = 43;
    private static final long AUTO_SYNC_DELAY_MS = 0;
    private static final long UNDO_DELETE_WINDOW_MS = 8000;
    private static final long PERIODIC_SYNC_INTERVAL_MS = SyncWakeup.ACTIVE_POLL_MS;

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
    private static final java.util.concurrent.ExecutorService BACKUP_CHECKPOINTS=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"backup-browser-checkpoint");thread.setDaemon(true);return thread;});
    private volatile String backupPageToken=UUID.randomUUID().toString();
    private volatile String backupDocumentUrl="";
    private volatile long backupLastSequence;
    private volatile long backupReceivedSequence;
    private volatile boolean backupCheckpointActive;
    private final java.util.Map<String,Integer> backupPendingCaptures=new java.util.HashMap<>();
    private final java.util.Set<String> backupRecoveryWakeups=new java.util.HashSet<>();
    private static final java.util.regex.Pattern BACKUP_SECRET_NAME=java.util.regex.Pattern.compile("password|passwd|secret|credential|token|authorization|oauth|recovery.?code|api.?key",java.util.regex.Pattern.CASE_INSENSITIVE);
    private static java.lang.ref.WeakReference<MainActivity> foreground = new java.lang.ref.WeakReference<>(null);
    static void refreshPlanningView(){MainActivity activity=foreground.get();if(activity!=null)activity.runOnUiThread(()->{if(activity.webView!=null)activity.webView.evaluateJavascript("window.PlanningUI&&window.PlanningUI.refresh();window.StatusUI&&window.StatusUI.refresh()",null);});}
    static void refreshBusinessView(){MainActivity activity=foreground.get();if(activity!=null)activity.runOnUiThread(()->{if(activity.webView!=null)activity.webView.evaluateJavascript("window.onLocalBusinessChanged&&window.onLocalBusinessChanged()",null);});}
    static void requestMcpSync(Context context) {
        if(BuildConfig.UI_PREVIEW)return;
        refreshBusinessView();MainActivity activity=foreground.get();
        if(activity!=null)activity.runOnUiThread(()->activity.requestAutomaticSync(0));
        else DriveSyncCoordinator.request(context,0);
    }
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicBoolean syncAgain = new AtomicBoolean(false);
    private final SyncWakeup.Window automaticWakeup=new SyncWakeup.Window();
    private android.net.ConnectivityManager.NetworkCallback syncNetworkCallback;
    private final Runnable delayedAutomaticSync = ()->{automaticWakeup.clear();automaticSyncIfConfigured();};
    private final Runnable periodicAutomaticSync = new Runnable() {
        @Override public void run() {
            DriveSyncCoordinator.poll(MainActivity.this);
            syncHandler.postDelayed(this, PERIODIC_SYNC_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(RevierApplication.recoveryRequired()){startActivity(new Intent(this,RestoreRecoveryActivity.class));finish();return;}
        preferences = getSharedPreferences("haus_garten", MODE_PRIVATE);
        store = new PixelLocalStore(this);
        if(!BuildConfig.UI_PREVIEW){
            try{DriveSyncJob.install(this);}catch(RuntimeException unavailable){backgroundState(true,"BACKGROUND_SCHEDULING_FAILED");}
            try{if(PlanningAccount.connected(this)||PlanningAccount.prefs(this).getBoolean("weather_enabled",false))PlanningJob.install(this);}catch(RuntimeException unavailable){backgroundState(true,"BACKGROUND_SCHEDULING_FAILED");}
        }
        new Thread(()->{try{if(store.compactPurgedTestActions()>0)refreshBusinessView();}catch(Exception failure){runOnUiThread(()->Toast.makeText(this,"Testbereinigung benötigt Prüfung. Bestehende Daten bleiben erhalten.",Toast.LENGTH_LONG).show());}},"test-cleanup").start();
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
            backCallback = Build.VERSION.SDK_INT>=34 ? leftEdgeMenuCallback() : this::navigateBack;
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
        if(preferences!=null&&(backgroundPending()||localCleanupPending()))requestAutomaticSync(0);
        refreshPlanningView();
        if(BuildConfig.UI_PREVIEW)return;
        android.net.ConnectivityManager connectivity=getSystemService(android.net.ConnectivityManager.class);
        if(connectivity!=null&&syncNetworkCallback==null){syncNetworkCallback=new android.net.ConnectivityManager.NetworkCallback(){
            private android.net.Network ready;
            @Override public void onCapabilitiesChanged(android.net.Network network,android.net.NetworkCapabilities caps){
                if(caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)){if(!network.equals(ready)){ready=network;runOnUiThread(()->{requestAutomaticSync(0);PlanningJob.schedule(MainActivity.this,1000);});}}else if(network.equals(ready))ready=null;
            }
            @Override public void onLost(android.net.Network network){if(network.equals(ready))ready=null;}
        };try{connectivity.registerDefaultNetworkCallback(syncNetworkCallback);}catch(RuntimeException unavailable){syncNetworkCallback=null;}}
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
        installBackupCheckpoint(false,null);
    }

    @Override protected void onPause() {
        if(backupCheckpointActive)installBackupCheckpoint(false,null);
        if (foreground.get() == this) foreground.clear();
        syncHandler.removeCallbacks(delayedAutomaticSync);
        automaticWakeup.clear();
        syncHandler.removeCallbacks(periodicAutomaticSync);
        if(syncNetworkCallback!=null){try{getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(syncNetworkCallback);}catch(RuntimeException ignored){}syncNetworkCallback=null;}
        try { if(!BuildConfig.UI_PREVIEW&&DriveAccount.available(this)&&store.pendingActions().length()>0)
            DriveSyncCoordinator.request(this,Math.max(AUTO_SYNC_DELAY_MS,store.remainingDeleteUndoMs()+200)); } catch(Exception ignored) {}
        super.onPause();
    }

    private volatile int systemGestureLeftDp=32;

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(245,247,245));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1,-1));
        // Older Android releases allow only a 200dp system-gesture exclusion.
        // API34+ also routes the remaining left edge through BackEvent above.
        if(Build.VERSION.SDK_INT>=29)webView.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{
            int width=(int)Math.ceil(32*getResources().getDisplayMetrics().density),height=(int)Math.ceil(200*getResources().getDisplayMetrics().density),top=Math.max(0,(b-t-height)/2);
            webView.setSystemGestureExclusionRects(java.util.Collections.singletonList(new android.graphics.Rect(0,top,width,Math.min(b-t,top+height))));
        });
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            if(Build.VERSION.SDK_INT>=29)systemGestureLeftDp=Math.max(32,(int)Math.ceil(insets.getSystemGestureInsets().left/getResources().getDisplayMetrics().density)+8);
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
        float fontScale=getResources().getConfiguration().fontScale;
        settings.setTextZoom(fontScale>0?Math.round(100*fontScale):100);
        // Bundled android_asset URLs remain accessible without general file access.
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowContentAccess(true); // Existing private photo providers.
        settings.setBlockNetworkLoads(true);settings.setBlockNetworkImage(true); // Network adapters are native; bridged renderer loads only bundled/private content.
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new SpeechBridge(), "AndroidSpeech");
        webView.addJavascriptInterface(new DataBridge(), "AndroidData");
        webView.addJavascriptInterface(new BrowserMediaBridge(this,store,this::trustedBackupPage,()->backupPageToken),"AndroidBrowserMedia");
        webView.addJavascriptInterface(new PhoneBridge(), "AndroidPhone");
        webView.addJavascriptInterface(new GardenPhotosBridge(), "AndroidGardenPhotos");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){backupDocumentUrl=url==null?"":url;backupPageToken=UUID.randomUUID().toString();backupLastSequence=0;backupReceivedSequence=0;backupCheckpointActive=false;}
            @Override public void onPageCommitVisible(WebView view,String url){if(trustedBackupPage())installBackupCheckpoint(false,null);}
            @Override public void onPageFinished(WebView view,String url){if(trustedBackupPage())installBackupCheckpoint(false,null);}
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

    private void openMobile() {try{if(new RestoredBrowserCheckpoint(this,store).pending()){webView.loadUrl("file:///android_asset/backup-restore-drafts.html");return;}}catch(Exception held){webView.loadUrl("file:///android_asset/backup-restore-drafts.html");return;}webView.loadUrl("file:///android_asset/mobile.html"); }

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

    public class DataBridge extends BusinessDataBridge {
        public DataBridge(){super(MainActivity.this,MainActivity.this.store,MainActivity.this.pendingWorkPhotos,()->requestAutomaticSync(AUTO_SYNC_DELAY_MS));}

        @JavascriptInterface public void connectPlanning(){if(previewConnectionBlocked())return;runOnUiThread(()->startActivity(new Intent(MainActivity.this,PlanningConnectionActivity.class)));}
        @JavascriptInterface public String consumePlanningOpenContext(){try{if(!getIntent().getBooleanExtra("open_planning",false))return "{}";JSONObject result=new JSONObject().put("open",true).put("appointment_id",getIntent().getStringExtra("planning_appointment_id")).put("action",getIntent().getStringExtra("planning_action"));getIntent().removeExtra("open_planning");return result.toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public boolean consumePlanningOpen(){boolean open=getIntent().getBooleanExtra("open_planning",false);getIntent().removeExtra("open_planning");return open;}

        @JavascriptInterface public String getSyncStatus() { return syncStatus().toString(); }

        @JavascriptInterface public void connectDrive(){if(previewConnectionBlocked())return;runOnUiThread(()->startActivity(new Intent(MainActivity.this,DriveConnectionActivity.class)));}
        @JavascriptInterface public void openFullBackup() {if(previewConnectionBlocked())return;runOnUiThread(()->installBackupCheckpoint(true,()->startActivity(new Intent(MainActivity.this,FullBackupActivity.class))));}
        @JavascriptInterface public void checkpointFullBackup(String snapshot) {if(BuildConfig.UI_PREVIEW)return;runOnUiThread(()->{if(trustedBackupPage()&&backupCheckpointActive)installBackupCheckpoint(false,null);});}
        @JavascriptInterface public String restoredDraftReview(String raw) {
            try{
                if(BuildConfig.UI_PREVIEW||!trustedBackupPage()||raw==null||raw.length()>1024*1024)throw new IOException();JSONObject args=new JSONObject(raw);if(!(args.opt("formatVersion") instanceof Integer)||args.getInt("formatVersion")!=1)throw new IOException();String operation=args.getString("op");RestoredBrowserCheckpoint review=new RestoredBrowserCheckpoint(MainActivity.this,store);JSONObject result;
                switch(operation){
                    case "status":result=review.status(restoredBrowserGate()&&!backupPageToken.isEmpty()&&backupPageToken.equals(args.optString("page_token")));break;
                    case "open":if(!review.available())throw new IOException();runOnUiThread(()->openRestoredBrowserReview());result=new JSONObject().put("accepted",true);break;
                    case "choose":if(!restoredBrowserGate()||!backupPageToken.equals(args.optString("page_token")))throw new IOException();result=review.choose(args);break;
                    case "return":if(review.pending())throw new IOException();result=new JSONObject().put("accepted",true);runOnUiThread(()->webView.loadUrl("file:///android_asset/mobile.html"));break;
                    case "complete_media":if(!restoredBrowserGate()||!backupPageToken.equals(args.optString("page_token")))throw new IOException();result=review.completeMedia(args);break;
                    case "complete":if(!restoredBrowserGate()||!backupPageToken.equals(args.optString("page_token")))throw new IOException();result=review.complete(args);runOnUiThread(()->webView.loadUrl("file:///android_asset/mobile.html"));break;
                    default:throw new IOException();
                }return new JSONObject().put("ok",true).put("data",result).toString();
            }catch(Exception held){return "{\"ok\":false,\"error\":{\"code\":\"BACKUP_BROWSER_RESTORE_REVIEW_REQUIRED\",\"message\":\"Die erhaltenen Entwürfe bleiben unverändert. Bitte den aktuellen Prüfstand erneut öffnen.\"}}";}
        }
        @JavascriptInterface public void retryDrive(){runOnUiThread(()->MainActivity.this.synchronize());}
        @JavascriptInterface public String getPixelTunnelStatus() { return PixelTunnelService.status(MainActivity.this).toString(); }

        @JavascriptInterface public void requestWorkLocation(String requestId) { runOnUiThread(()->workLocation.request(requestId)); }
        @JavascriptInterface public void cancelWorkLocation() { runOnUiThread(()->workLocation.cancel()); }

        @JavascriptInterface public void openPixelTunnelSettings() {
            if(previewConnectionBlocked())return;
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, PixelTunnelActivity.class)));
        }
        @JavascriptInterface public String getAppInfo() {
            JSONObject info = new JSONObject();
            try {
                android.content.pm.PackageInfo installed = getPackageManager().getPackageInfo(getPackageName(), 0);
                info.put("uiPreview",BuildConfig.UI_PREVIEW);info.put("applicationId",getPackageName());
                info.put("systemGestureLeftCss",systemGestureLeftDp);info.put("build_fingerprint",BuildProvenance.describe());info.put("versionName", installed.versionName);
                info.put("versionCode", android.os.Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode);
            } catch (Exception ignored) {}
            return info.toString();
        }

        @JavascriptInterface public String sharePreparedInvoice(){
            try{JSONObject handoff=new JSONObject(store.ownerValue("invoice_share_handoff","{}")),all=PixelMcpReadTools.effective(store.getState()),invoice=PixelMcpWriteTools.find(all.getJSONArray("invoices"),handoff.getString("invoice_id"));return shareInvoiceById(invoice.getString("id"),handoff.getString("channel"));}catch(Exception e){return "ERROR: Originalbeleg oder Versandvorbereitung fehlt.";}
        }

        @JavascriptInterface public void shareOffer(String id){
            new Thread(()->{try{JSONObject offer=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("offers"),id);if(LocalAuthorityGuard.local(MainActivity.this)&&offer.optString("finalized_at").isEmpty())throw new IOException("Nur finalisierte Originalangebote teilen.");TestDataRules.noDelivery(offer,PixelMcpReadTools.effective(store.getState()));byte[] bytes;try{bytes=new BusinessDocumentStore(MainActivity.this).read(offer);}catch(Exception missing){if(LocalAuthorityGuard.local(MainActivity.this))throw new IOException("Lokales Original-PDF fehlt oder wurde verändert.");bytes=DriveSyncCoordinator.fetchDocument(MainActivity.this,offer);new BusinessDocumentStore(MainActivity.this).store(bytes,offer);}File dir=new File(getCacheDir(),"invoice-share");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Freigabeordner fehlt.");File target=new File(dir,"Angebot-"+UUID.randomUUID()+".pdf");try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){out.write(bytes);}Uri uri=Uri.parse("content://"+getPackageName()+".invoicefiles/"+target.getName());Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(android.content.ClipData.newRawUri("Angebot",uri));runOnUiThread(()->startActivity(Intent.createChooser(send,"Angebots-PDF teilen")));}catch(Exception error){runOnUiThread(()->Toast.makeText(MainActivity.this,LocalAuthorityGuard.local(MainActivity.this)?"Lokales Original-PDF fehlt oder wurde verändert. Dokumentjob prüfen; keine Übergabe vorbereitet.":"PDF noch nicht verfügbar. Dokument und Drive-Abgleich prüfen.",Toast.LENGTH_LONG).show());}}).start();
        }
        @JavascriptInterface public String shareInvoiceById(String id,String channel){try{JSONObject invoice=PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices"),id);if(LocalAuthorityGuard.local(MainActivity.this)?!IssuedInvoiceRules.isIssued(invoice):invoice.optString("issued_at").isEmpty())throw new IllegalArgumentException("Nur ausgestellte Originalbelege teilen.");byte[] bytes;try{bytes=new BusinessDocumentStore(MainActivity.this).read(invoice);}catch(IOException absent){if(LocalAuthorityGuard.local(MainActivity.this))throw new IOException("Lokales Original-PDF fehlt oder wurde verändert.");bytes=DriveSyncCoordinator.fetchDocument(MainActivity.this,invoice);new BusinessDocumentStore(MainActivity.this).store(bytes,invoice);}invoice.put("pdf_base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));return shareInvoiceVia(invoice.toString(),channel);}catch(Exception e){return LocalAuthorityGuard.local(MainActivity.this)?"ERROR: Lokales Original-PDF fehlt oder wurde verändert. Dokumentjob prüfen; keine Übergabe vorbereitet.":"ERROR: Original-PDF ist noch nicht vollständig verfügbar. Drive abgleichen.";}}
        @JavascriptInterface public String shareInvoice(String value) {
            return shareInvoiceVia(value,"share");
        }
        @JavascriptInterface public String shareInvoiceVia(String value, String channel) {
            try {
                if (!new HashSet<>(java.util.Arrays.asList("share","email","whatsapp")).contains(channel)) throw new Exception("Unbekannter Versandweg.");
                JSONObject invoice=new JSONObject(value);boolean local=LocalAuthorityGuard.local(MainActivity.this);byte[] bytes;
                if(local){VerifiedInvoiceShare original=verifiedLocalInvoiceShare(MainActivity.this,store,invoice);invoice=original.invoice;bytes=original.bytes;}else bytes=android.util.Base64.decode(invoice.getString("pdf_base64"),android.util.Base64.DEFAULT);
                if (bytes.length > (local?20:10) * 1024 * 1024 || bytes.length < 5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))
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

        @JavascriptInterface public void synchronize() { runOnUiThread(() -> MainActivity.this.synchronize()); }
    }

    static final class VerifiedInvoiceShare {final JSONObject invoice;final byte[] bytes;VerifiedInvoiceShare(JSONObject invoice,byte[] bytes){this.invoice=invoice;this.bytes=bytes;}}
    static VerifiedInvoiceShare verifiedLocalInvoiceShare(Context context,PixelLocalStore store,JSONObject supplied)throws Exception {
        JSONObject all=PixelMcpReadTools.effective(store.getState()),invoice=new JSONObject(PixelMcpWriteTools.find(all.getJSONArray("invoices"),supplied.getString("id")).toString());
        if(!IssuedInvoiceRules.isIssued(invoice))throw new IOException("Nur ausgestellte Originalbelege teilen.");TestDataRules.noDelivery(invoice,all);
        return new VerifiedInvoiceShare(invoice,new BusinessDocumentStore(context).read(invoice));
    }

    private boolean trustedBackupPage(){return webView!=null&&backupDocumentUrl.startsWith("file:///android_asset/");}
    private boolean restoredBrowserGate(){return webView!=null&&"file:///android_asset/backup-restore-drafts.html".equals(backupDocumentUrl);}
    private void openRestoredBrowserReview(){
        if(!trustedBackupPage())return;final String page=backupPageToken;
        webView.evaluateJavascript("window.HGBackupSnapshot?JSON.stringify(window.HGBackupSnapshot.capture()):null",raw->{try{Object decoded=new org.json.JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new IOException();JSONObject browser=sanitizeBackupCheckpoint(new JSONObject((String)decoded));BACKUP_CHECKPOINTS.execute(()->{try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){RestoredBrowserCheckpoint review=new RestoredBrowserCheckpoint(getApplicationContext(),local);review.reopen();review.preserveDevice(browser);runOnUiThread(()->{if(!isDestroyed()&&page.equals(backupPageToken))webView.loadUrl("file:///android_asset/backup-restore-drafts.html");});}catch(Exception held){runOnUiThread(()->Toast.makeText(this,"Die bestehenden Entwürfe bleiben erhalten. Die Prüfung konnte noch nicht geöffnet werden.",Toast.LENGTH_LONG).show());}});}catch(Exception held){Toast.makeText(this,"Aktuelle Formularwerte zuerst speichern oder die Ansicht erneut öffnen. Vorhandene Entwürfe bleiben erhalten.",Toast.LENGTH_LONG).show();}});
    }
    private void prepareRestoredBrowserReview(String page){
        try{String script;try(java.io.InputStream input=getAssets().open("backup-browser-state.js");java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)output.write(block,0,n);script=new String(output.toByteArray(),StandardCharsets.UTF_8);}String expression=script+";window.HGBackupSnapshot.bind("+JSONObject.quote(page)+",false);JSON.stringify(window.HGBackupSnapshot.capture());";runOnUiThread(()->{if(isDestroyed()||!restoredBrowserGate()||!page.equals(backupPageToken))return;webView.evaluateJavascript(expression,raw->{try{Object decoded=new org.json.JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new IOException();JSONObject browser=sanitizeBackupCheckpoint(new JSONObject((String)decoded));BACKUP_CHECKPOINTS.execute(()->{try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){if(!page.equals(backupPageToken))return;new RestoredBrowserCheckpoint(getApplicationContext(),local).preserveDevice(browser);runOnUiThread(()->{if(!isDestroyed()&&restoredBrowserGate()&&page.equals(backupPageToken))webView.evaluateJavascript("window.HGBackupSnapshot.prepareMedia(true).then(()=>window.HGBackupSnapshot.reviewRestored()).catch(()=>window.HGBackupSnapshot.reviewRestored())",null);});}catch(Exception held){runOnUiThread(()->webView.evaluateJavascript("document.getElementById('restore-draft-status').textContent='Die Entwürfe bleiben geschützt. Bitte die App erneut öffnen; der Prüfstand wird nicht überschrieben.'",null));}});}catch(Exception held){/* The gate remains closed; no checkpoint or browser value was replaced. */}});});}catch(Exception held){/* No writer is enabled by a failed review bootstrap. */}
    }
    private void installBackupCheckpoint(boolean explicit,Runnable after){
        if(BuildConfig.UI_PREVIEW||!trustedBackupPage()){if(explicit)Toast.makeText(this,"Sicherung kann nur aus der geöffneten App eingerichtet werden.",Toast.LENGTH_LONG).show();return;}
        final String page=backupPageToken;
        BACKUP_CHECKPOINTS.execute(()->{
            try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){
                if(restoredBrowserGate()){prepareRestoredBrowserReview(page);return;}
                if(new RestoredBrowserCheckpoint(getApplicationContext(),local).pending()){runOnUiThread(()->{if(!isDestroyed()&&page.equals(backupPageToken))webView.loadUrl("file:///android_asset/backup-restore-drafts.html");});return;}
                JSONObject config=new BackupSettingsRepository(getApplicationContext(),local).read();boolean active=config.optBoolean("contextMatches")&&config.optBoolean("keyConfigured")&&!config.optString("folderId").isEmpty();
                if(active||explicit)markBackupCapturePending(local,page);
                String script;try(java.io.InputStream input=getAssets().open("backup-browser-state.js");java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);script=new String(output.toByteArray(),StandardCharsets.UTF_8);}
                runOnUiThread(()->{
                    if(isDestroyed()||!trustedBackupPage()||!page.equals(backupPageToken))return;backupCheckpointActive=active;
                    String expression=script+";window.HGBackupSnapshot.bind("+JSONObject.quote(page)+","+active+");window.HGBackupSnapshot.prepareMedia(false).catch(()=>{});"+((active||explicit)?"JSON.stringify(window.HGBackupSnapshot.capture());":"null;");
                    webView.evaluateJavascript(expression,value->{if(active||explicit){try{Object decoded=new org.json.JSONTokener(value).nextValue();if(!(decoded instanceof String))throw new IOException();enqueueBackupCheckpoint((String)decoded,explicit,after,true);}catch(Exception invalid){recordBackupCaptureFailure("BACKUP_BROWSER_CAPTURE_REQUIRED",explicit,after);}}});
                });
                try{if(!FullBackupJob.install(getApplicationContext()))backgroundState(true,"BACKGROUND_SCHEDULER_UNAVAILABLE");}catch(Exception ignored){/* A missing schedule never changes local business data. */}
            }catch(Exception failure){if(explicit)recordBackupCaptureFailure("BACKUP_BROWSER_CAPTURE_REQUIRED",true,after);}
        });
    }
    private void markBackupCapturePending(PixelLocalStore local,String page)throws Exception {
        JSONObject previous=new JSONObject(local.ownerValue("full_backup_browser_status","{}"));if(!previous.optString("code").isEmpty()&&(!previous.optBoolean("capturePending")||!backupPendingCaptures.containsKey(page)))backupRecoveryWakeups.add(page);
        backupPendingCaptures.put(page,backupPendingCaptures.getOrDefault(page,0)+1);local.putOwnerValue("full_backup_browser_status",new JSONObject().put("code","BACKUP_BROWSER_CAPTURE_REQUIRED").put("capturePending",true).put("at",java.time.Instant.now().toString()).toString());
    }
    private void completeBackupCapture(PixelLocalStore local,String page,JSONObject config)throws Exception {
        int remaining=Math.max(0,backupPendingCaptures.getOrDefault(page,1)-1);if(remaining==0)backupPendingCaptures.remove(page);else backupPendingCaptures.put(page,remaining);
        if(remaining==0&&page.equals(backupPageToken)&&backupLastSequence>0&&backupLastSequence>=backupReceivedSequence){
            local.putOwnerValue("full_backup_browser_status",new JSONObject().put("code","").put("at",java.time.Instant.now().toString()).toString());boolean wake=backupRecoveryWakeups.remove(page);JSONObject scheduler=new JSONObject(local.ownerValue("full_backup_scheduler_status","{}"));
            if(java.util.Arrays.asList("BACKUP_BROWSER_CAPTURE_REQUIRED","BACKUP_BROWSER_STATE_REQUIRED_OPEN_APP","BACKUP_BROWSER_WRITE_CONFLICT","BACKUP_BROWSER_EDITOR_STATE_MISMATCH","BACKUP_BROWSER_STATE_TOO_LARGE").contains(scheduler.optString("code"))){local.putOwnerValue("full_backup_scheduler_status",new JSONObject().put("code","").put("needsReview",false).put("at",java.time.Instant.now().toString()).toString());wake=true;}
            if(wake&&config.optBoolean("enabled"))try{FullBackupJob.schedule(getApplicationContext(),0);}catch(Exception ignored){}
        }
    }
    private void enqueueBackupCheckpoint(String raw,boolean explicit,Runnable after,boolean alreadyPending){
        if(!trustedBackupPage())return;final String page=backupPageToken;
        if(raw==null||raw.length()>16*1024*1024){recordBackupCaptureFailure("BACKUP_BROWSER_STATE_TOO_LARGE",explicit,after);return;}
        BACKUP_CHECKPOINTS.execute(()->{
            try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){
                JSONObject value=new JSONObject(raw),checkpoint=value.getJSONObject("_checkpoint");long sequence=checkpoint.getLong("sequence");
                if(!page.equals(checkpoint.getString("pageToken"))||!page.equals(backupPageToken))return;
                BackupSettingsRepository repository=new BackupSettingsRepository(getApplicationContext(),local);JSONObject config=repository.read();
                if(!explicit&&!(config.optBoolean("contextMatches")&&config.optBoolean("keyConfigured")&&!config.optString("folderId").isEmpty()))return;
                if(!alreadyPending)markBackupCapturePending(local,page);backupReceivedSequence=Math.max(backupReceivedSequence,sequence);
                if(sequence<=backupLastSequence){completeBackupCapture(local,page,config);return;}
                value.remove("_checkpoint");value=sanitizeBackupCheckpoint(value);
                boolean unchanged=false;try{unchanged=PixelMcpWriteTools.hash(value).equals(repository.browser().optString("contentSha256"));}catch(Exception missing){/* First explicit checkpoint or a restored epoch needs its own commit. */}
                if(!unchanged)repository.saveBrowser(UUID.randomUUID().toString(),repository.browserRevision(),value);
                backupLastSequence=sequence;completeBackupCapture(local,page,config);
                if(after!=null)runOnUiThread(()->{if(!isDestroyed())after.run();});
            }catch(Exception failure){String message=failure.getMessage(),code="BACKUP_BROWSER_CAPTURE_REQUIRED";if("BACKUP_BROWSER_STATE_TOO_LARGE".equals(message)||"BACKUP_BROWSER_WRITE_CONFLICT".equals(message))code=message;recordBackupCaptureFailure(code,explicit,after);}
        });
    }
    static JSONObject sanitizeBackupCheckpoint(JSONObject source)throws Exception {
        if(source.optInt("formatVersion")!=1||source.optJSONObject("storage")==null)throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");JSONObject value=new JSONObject().put("formatVersion",1).put("storage",new JSONObject(source.getJSONObject("storage").toString())),storage=value.getJSONObject("storage");
        for(java.util.Iterator<String> keys=storage.keys();keys.hasNext();){String name=keys.next();if(BACKUP_SECRET_NAME.matcher(name).find())keys.remove();else if(!(storage.get(name) instanceof String))throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");}
        Object drafts=source.opt("drafts");if(drafts!=null&&drafts!=JSONObject.NULL)value.put("drafts",sanitizeBackupDraftValue(drafts));
        String mirror="hausgarten.fullbackup.formdrafts.v1";if(storage.has(mirror))try{JSONObject original=new JSONObject(storage.getString(mirror));Object sanitized=sanitizeBackupDraftValue(original);if(!PixelMcpWriteTools.hash(original).equals(PixelMcpWriteTools.hash(sanitized)))storage.put(mirror,sanitized.toString());}catch(org.json.JSONException invalid){throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");}
        // Preserve the native capture binding. Do not normalize opaque editor strings:
        // their exact bytes are compared with the same captured SQLite generation.
        if(source.has("editor_state")){
            JSONObject state=source.optJSONObject("editor_state"),identity=state==null?null:state.optJSONObject("context");
            if(state==null||state.length()!=4||!(state.opt("formatVersion") instanceof Integer)||state.getInt("formatVersion")!=1||!(state.opt("review_required") instanceof Boolean)||!(state.opt("record_hash") instanceof String)||!state.getString("record_hash").matches("[0-9a-f]{64}")||identity==null||identity.length()!=2)throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");
            for(String name:new String[]{"installationId","restoreEpoch"})if(!(identity.opt(name) instanceof String)||!identity.getString(name).matches("[0-9a-f-]{36}"))throw new IOException("BACKUP_BROWSER_CAPTURE_REQUIRED");
            value.put("editor_state",new JSONObject().put("formatVersion",1).put("context",new JSONObject(identity.toString())).put("review_required",state.getBoolean("review_required")).put("record_hash",state.getString("record_hash")));
        }
        return value;
    }
    private static Object sanitizeBackupDraftValue(Object value)throws Exception {
        if(value instanceof org.json.JSONArray){org.json.JSONArray safe=new org.json.JSONArray(),rows=(org.json.JSONArray)value;for(int i=0;i<rows.length();i++){Object row=rows.get(i);if(row instanceof JSONObject){JSONObject object=(JSONObject)row;String type=object.optString("type"),name=object.optString("id")+"."+object.optString("name")+"."+object.optString("autocomplete");if(type.equalsIgnoreCase("password")||type.equalsIgnoreCase("file")||BACKUP_SECRET_NAME.matcher(name).find())continue;}safe.put(sanitizeBackupDraftValue(row));}return safe;}
        if(value instanceof JSONObject){JSONObject safe=new JSONObject();for(java.util.Iterator<String> keys=((JSONObject)value).keys();keys.hasNext();){String name=keys.next();if(!BACKUP_SECRET_NAME.matcher(name).find())safe.put(name,sanitizeBackupDraftValue(((JSONObject)value).get(name)));}return safe;}return value;
    }
    private void recordBackupCaptureFailure(String code,boolean explicit,Runnable after){
        Runnable record=()->{backupPendingCaptures.remove(backupPageToken);try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){local.putOwnerValue("full_backup_browser_status",new JSONObject().put("code",code).put("capturePending",false).put("at",java.time.Instant.now().toString()).toString());}catch(Exception ignored){}if(explicit)runOnUiThread(()->{if(isDestroyed())return;Toast.makeText(this,"Der aktuelle Formularentwurf konnte noch nicht für die Vollsicherung erfasst werden. Bitte Sicherungsanzeige prüfen.",Toast.LENGTH_LONG).show();if(after!=null)after.run();});};if(Thread.currentThread().getName().equals("backup-browser-checkpoint"))record.run();else BACKUP_CHECKPOINTS.execute(record);
    }

    private boolean previewConnectionBlocked(){
        if(!BuildConfig.UI_PREVIEW)return false;
        runOnUiThread(()->Toast.makeText(this,"Test-App: Verbindungen bleiben zum Schutz der Produktivdaten gesperrt.",Toast.LENGTH_LONG).show());
        return true;
    }

    private void synchronize() {
        if(previewConnectionBlocked())return;
        if(!LocalAuthorityGuard.legacySyncAllowed(this)){requestAutomaticSync(0);Toast.makeText(this,"Lokal gespeichert. Hintergrundsicherung erneut eingeplant; Sicherungsstatus prüfen.",Toast.LENGTH_LONG).show();return;}
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

    private boolean localCleanupPending(){try{return new JSONObject(store.ownerValue("local_media_cleanup","{}")).optBoolean("pending");}catch(Exception invalid){return true;}}
    private void localCleanupState(boolean pending,String code){try{store.putOwnerValue("local_media_cleanup",new JSONObject().put("formatVersion",1).put("pending",pending).put("code",code).toString());}catch(Exception ignored){}}
    private void scheduleLocalMaintenance(){String generation=java.util.UUID.randomUUID().toString();try{store.putOwnerValue("local_media_cleanup",new JSONObject().put("formatVersion",1).put("pending",true).put("generation",generation).toString());BACKUP_CHECKPOINTS.execute(()->{try(PixelLocalStore local=new PixelLocalStore(getApplicationContext())){JSONObject result=TrashMediaCleanup.runLocal(getApplicationContext(),local);android.database.sqlite.SQLiteDatabase db=local.getWritableDatabase();db.beginTransaction();try{JSONObject marker=new JSONObject(local.ownerValue("local_media_cleanup","{}"));if(generation.equals(marker.optString("generation")))local.putOwnerValue("local_media_cleanup",marker.put("pending",result.optInt("pending")>0).put("code",result.optString("error_code")).toString());db.setTransactionSuccessful();}finally{db.endTransaction();}}catch(Exception deferred){localCleanupState(true,"LOCAL_MEDIA_CLEANUP_PENDING");}});}catch(Exception unavailable){localCleanupState(true,"LOCAL_MEDIA_CLEANUP_SCHEDULER_UNAVAILABLE");}}
    private void backgroundState(boolean pending,String code) {
        try{if(store!=null)store.putOwnerValue("background_work_dispatch",new JSONObject().put("formatVersion",1).put("pending",pending).put("code",code).toString());}catch(Exception unavailable){/* Business receipt remains durable independently. */}
        try{preferences.edit().putBoolean("background_work_pending",pending).putString("background_work_error_code",code).commit();}catch(RuntimeException unavailable){/* The SQLite state is the primary resume marker. */}
    }
    private boolean backgroundPending(){try{String raw=store.ownerValue("background_work_dispatch","");if(!raw.isEmpty())return new JSONObject(raw).getBoolean("pending");}catch(Exception unavailable){}return preferences.getBoolean("background_work_pending",false);}
    private String backgroundCode(){try{String raw=store.ownerValue("background_work_dispatch","");if(!raw.isEmpty())return new JSONObject(raw).getString("code");}catch(Exception unavailable){}return preferences.getString("background_work_error_code","");}
    private void requestAutomaticSync(long delayMs) {
        if(BuildConfig.UI_PREVIEW)return;
        BackgroundWorkDispatch.request(delayMs,new BackgroundWorkDispatch.Host(){
            public void state(boolean pending,String code){backgroundState(pending,code);}
            public boolean post(Runnable task){return syncHandler.post(task);}
            public boolean localMode(){boolean local=!LocalAuthorityGuard.legacySyncAllowed(MainActivity.this);if(local)scheduleLocalMaintenance();return local;}
            public boolean localReady()throws Exception {JSONObject config=new BackupSettingsRepository(MainActivity.this,store).read();return config.optBoolean("enabled")&&config.optBoolean("keyConfigured")&&FullBackupController.validTarget(config);}
            public boolean local(long delay)throws Exception {return FullBackupJob.schedule(MainActivity.this,delay);}
            public boolean legacy(long delay){DriveSyncCoordinator.noteRequest();long undoDelay=store==null?0:store.remainingDeleteUndoMs(),now=System.currentTimeMillis();long due=automaticWakeup.request(now,delay,Math.max(preferences.getLong("drive_next_retry",0),undoDelay>0?now+undoDelay+200:0));syncHandler.removeCallbacks(delayedAutomaticSync);return syncHandler.postDelayed(delayedAutomaticSync,Math.max(0,due-now))&&DriveSyncJob.schedule(MainActivity.this,Math.max(0,due-now));}
        });
    }

    private void automaticSyncIfConfigured() {
        if(BuildConfig.UI_PREVIEW)return;
        try{if(!DriveSyncCoordinator.automaticAllowed(this)||!DriveSyncCoordinator.hasNetwork(this))return;}catch(Exception invalid){return;}
        if(DriveAccount.configured(this)){performDriveSync(Uri.EMPTY,false);return;}
        String saved = preferences.getString("drive_tree_uri", "");
        if (!saved.isEmpty()) performDriveSync(Uri.parse(saved), false);
    }

    private void performDriveSync(Uri treeUri, boolean showToast) {
        if(BuildConfig.UI_PREVIEW)return;
        if (!syncInProgress.compareAndSet(false, true)) { syncAgain.set(true); return; }
        if (showToast) Toast.makeText(this, "Drive-Abgleich läuft …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // Snapshot/queue calculation can be expensive: never do it on the UI looper.
            callback("window.onDriveSyncStarted", syncStatus());
            JSONObject result;
            try {result=DriveSyncCoordinator.run(this,store);}
            catch(Exception error){result=new JSONObject();try{result.put("ok",false).put("error",DriveSyncCoordinator.friendly(error));}catch(Exception ignored){}}
            finally{syncInProgress.set(false);}
            final JSONObject completed=result;
            try{completed.put("automatic",!showToast).put("sync",syncStatus());}catch(Exception ignored){}
            runOnUiThread(()->{
                callback("window.onDriveSync",completed);
                if(syncAgain.getAndSet(false))requestAutomaticSync(0);
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
            int duplicates=preferences.getInt("drive_duplicate_files",0);status.put("local_cleanup_pending",localCleanupPending()).put("background_work_pending",backgroundPending()).put("background_work_error_code",backgroundCode()).put("duplicate_files",duplicates).put("warning",duplicates>0?"Gleichnamige Dateien prüfen. Originale bleiben erhalten.":"");
            if(DriveAccount.configured(this)){String progress=store.ownerValue("drive-meta:"+preferences.getString("drive_folder_id","")+":progress","");if(!progress.isEmpty())status.put("transfer_progress",new JSONObject(progress));}
            return status;
        }catch(Exception error){return new JSONObject();}
    }

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
                // A submitted file may still belong to an unsaved draft; age alone is no deletion proof.
                if(valid){result=new Uri[]{captured};clearPendingCapture(false);}
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
    @android.annotation.TargetApi(34)
    private android.window.OnBackInvokedCallback leftEdgeMenuCallback() {
        return new android.window.OnBackAnimationCallback() {
            private boolean leftEdge;
            @Override public void onBackStarted(android.window.BackEvent event) { leftEdge=event.getSwipeEdge()==android.window.BackEvent.EDGE_LEFT; }
            @Override public void onBackProgressed(android.window.BackEvent event) {}
            @Override public void onBackCancelled() { leftEdge=false; }
            @Override public void onBackInvoked() {
                boolean openMenu=leftEdge;leftEdge=false;
                if(restoredBrowserGate()){navigateBack();return;}
                if(openMenu) { if(webView!=null)webView.evaluateJavascript("window.HGGestures&&window.HGGestures.openMenu()",null); }
                else navigateBack();
            }
        };
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
