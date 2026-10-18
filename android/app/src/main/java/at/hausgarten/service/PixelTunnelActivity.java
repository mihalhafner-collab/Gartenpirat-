package at.hausgarten.service;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;

/** Native setup screen: credentials never cross the JavaScript bridge. */
public final class PixelTunnelActivity extends Activity {
    private TextView status,battery;
    private EditText id,key,organization;
    private CheckBox write;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,2000);}};
    private PixelTunnelConfig config;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);config=new PixelTunnelConfig(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*getResources().getDisplayMetrics().density);view.setPadding(pad,pad,pad,pad);scroll.addView(view);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getInsetsController().setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;view.setPadding(pad+bars.left,pad+bars.top,pad+bars.right,pad+Math.max(bars.bottom,keyboard));
            }else view.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.requestApplyInsets();
        heading(view,"ChatGPT auf dem Pixel");
        text(view,"Kunden, Aufträge und Arbeitsberichte lesen. Optional Arbeitsaufzeichnungen anlegen und korrigieren – auch ohne Auftrag. Die Verbindung nutzt den zuletzt auf diesem Gerät gespeicherten Drive-Stand.");
        status=text(view,"Status wird geprüft …");battery=text(view,"");
        heading(view,"Einmalig einrichten");
        text(view,"Die bestehende private Pixel-Verbindung verwenden. Ein eigener Runtime-Schlüssel wird verschlüsselt auf diesem Gerät gespeichert.");
        text(view,"Pixel-Tunnel-ID");id=new EditText(this);id.setSingleLine(true);id.setText(config.id());id.setHint("tunnel_…");view.addView(id);
        text(view,"OpenAI-Organisation des Tunnels");organization=new EditText(this);organization.setSingleLine(true);
        organization.setText(config.organization());
        organization.setHint("org-…");view.addView(organization);
        text(view,"Tunnel und Schlüssel müssen zur selben OpenAI-Organisation gehören. Die vorbelegte Angabe wird erst mit Verbindung starten gespeichert. Ein gültiger Schlüssel aus einem anderen Konto funktioniert hier nicht.");
        text(view,"Runtime-Schlüssel");key=new EditText(this);key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);key.setHint(config.configured()?"Bereits gespeichert · leer lassen":"Schlüssel hier einfügen");view.addView(key);
        write=new CheckBox(this);write.setText("ChatGPT darf fachliche Eigentümerfunktionen lesen und schreiben");write.setChecked(config.writeEnabled());view.addView(write);
        text(view,"Freigabe für Kunden, Aufträge, Arbeitszeiten, Fotos, Entwürfe, Konfliktprüfung, Archiv und Sicherungen. Originalbelege bleiben unveränderlich; Abschluss und Korrekturen benötigen den geschützten Pixel-/Drive-Nummernkreis. Kontozugänge und Android-Rechte werden hier nicht erweitert. Änderungen werden lokal gespeichert und mit Drive abgeglichen. Die Freigabe gilt nach Verbindung starten.");
        button(view,"Verbindung starten",()->startConnection());
        button(view,"Verbindung beenden",()->{config.enabled(false);stopService(new Intent(this,PixelTunnelService.class));refreshStatus();});
        heading(view,"Hintergrundbetrieb");
        text(view,"Damit Android die Verbindung seltener unterbricht, Hintergrundnutzung zulassen und diese App vom extremen Energiesparen ausnehmen. Eine sichtbare Statusmeldung bleibt aktiv.");
        button(view,"Akku-Einstellungen öffnen",()->open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,null));
        button(view,"App- und Hintergrunddaten-Einstellungen",()->open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        button(view,"OpenAI-Tunnel verwalten",()->open(Intent.ACTION_VIEW,Uri.parse("https://platform.openai.com/settings/organization/tunnels")));
        button(view,"Zurück zur Haus & Garten App",this::finish);
    }
    private TextView text(LinearLayout view,String value){TextView label=new TextView(this);label.setText(value);label.setTextSize(16);label.setTextColor(Color.rgb(30,60,43));label.setPadding(0,12,0,12);view.addView(label);return label;}
    private void heading(LinearLayout view,String value){TextView label=text(view,value);label.setTextSize(23);label.setTypeface(null,Typeface.BOLD);}
    private void button(LinearLayout view,String title,Runnable action){Button button=new Button(this);button.setAllCaps(false);button.setText(title);button.setMinHeight((int)(52*getResources().getDisplayMetrics().density));view.addView(button);button.setOnClickListener(v->action.run());}
    private void open(String action,Uri uri){try{Intent intent=new Intent(action);if(uri!=null)intent.setData(uri);startActivity(intent);}catch(ActivityNotFoundException error){Toast.makeText(this,"Diese Einstellung ist auf dem Gerät nicht verfügbar.",Toast.LENGTH_LONG).show();}}
    private void startConnection(){
        try{
            String entered=key.getText().toString().trim(),tunnel=id.getText().toString().trim(),org=organization.getText().toString().trim();PixelTunnelConfig.validateId(tunnel);PixelTunnelConfig.validateOrganization(org);
            if(!entered.isEmpty()){
                stopService(new Intent(this,PixelTunnelService.class));
                config.save(tunnel,org,entered);key.setText("");entered=null;
            }else if(!config.configured()||!config.id().equals(tunnel))throw new IllegalArgumentException("Bitte für den neuen Tunnel den Runtime-Schlüssel eingeben.");
            else if(!config.organization().equals(org)){stopService(new Intent(this,PixelTunnelService.class));config.organization(org);}
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},281);return;}
            launch();
        }catch(Exception error){new AlertDialog.Builder(this).setTitle("Verbindung noch nicht eingerichtet").setMessage(error instanceof IllegalArgumentException?error.getMessage():"Einstellungen konnten nicht sicher gespeichert werden. Bitte erneut versuchen.").setPositiveButton("OK",null).show();}
    }
    private void launch(){try{if(config.writeEnabled()!=write.isChecked())stopService(new Intent(this,PixelTunnelService.class));config.writeEnabled(write.isChecked());config.enabled(true);startForegroundService(new Intent(this,PixelTunnelService.class));refreshStatus();}catch(RuntimeException denied){config.enabled(false);Toast.makeText(this,"Android hat den Dienststart eingeschränkt. Bitte die Akku-Einstellungen prüfen.",Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==281){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)launch();else Toast.makeText(this,"Bitte Benachrichtigungen freigeben, damit du die laufende Verbindung sehen und beenden kannst.",Toast.LENGTH_LONG).show();}}
    private void refreshStatus(){try{org.json.JSONObject info=PixelTunnelService.status(this);status.setText(info.optString("detail"));battery.setText(info.optBoolean("battery_exempt")?"Akku-Ausnahme ist aktiv.":"Akku-Ausnahme noch nicht aktiv. Bei Energiesparen kann die Verbindung unterbrochen werden.");}catch(Exception ignored){}}
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
