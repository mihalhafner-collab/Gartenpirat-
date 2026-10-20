package at.hausgarten.service;

import android.accounts.AccountManager;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Deliberate device setup; no account contents, tokens or API keys enter the WebView. */
public final class PlanningConnectionActivity extends Activity {
    private TextView status,accountLabel;private String account="";private CheckBox busy,weather,notifications,calendar;private EditText from,to,travel;private final List<CheckBox> days=new ArrayList<>();private LinearLayout root;private volatile boolean working;private String[] openedSettingsHash;private boolean connectAfterAccount;
    private TextView text(String value){TextView v=new TextView(this);v.setText(value);root.addView(v);return v;}
    private EditText input(String label,String value,boolean number){text(label);EditText v=new EditText(this);v.setText(value);v.setSingleLine(true);if(number)v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);root.addView(v);return v;}
    private CheckBox check(String label,boolean value){CheckBox v=new CheckBox(this);v.setText(label);v.setChecked(value);root.addView(v);return v;}
    private void button(String title,Runnable run){Button b=new Button(this);b.setText(title);root.addView(b);b.setOnClickListener(v->{if(!working)run.run();});}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle("Planung einrichten");root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        android.content.SharedPreferences p=PlanningAccount.prefs(this);account=p.getString("account","");
        text("Termine bleiben in Haus & Garten. Google Kalender zeigt verbundene Einsätze; Wetterhinweise verschieben nichts automatisch.");status=text("");accountLabel=text(account.isEmpty()?"Kalender nicht verbunden":account);
        LinearLayout normal=root,advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(android.view.View.GONE);
        button("Erweitert / Diagnose · Konten und Wetterdetails",()->advanced.setVisibility(advanced.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));normal.addView(advanced);root=advanced;
        button("Google-Konto auswählen",this::chooseAccount);
        busy=check("Auch Belegtzeiten meines Hauptkalenders prüfen (zusätzliche Lesefreigabe)",p.getBoolean("read_busy",false));
        button("Google Kalender verbinden",()->new AlertDialog.Builder(this).setMessage("Kalenderrechte für das gewählte Konto freigeben und den App-Kalender Haus & Garten – Einsätze verwenden bzw. einmalig erstellen? Gespeicherte aktive Termine werden danach übertragen. Keine Kundeneinladungen werden gesendet.").setNegativeButton("Abbrechen",null).setPositiveButton("Verbinden",(d,w)->connect()).show());
        button("Kalenderverbindung trennen",()->new AlertDialog.Builder(this).setMessage("Nur Verbindung trennen? Gespeicherte App-Termine und Google-Einträge bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Trennen",(d,w)->{p.edit().remove("calendar_id").apply();status.setText("Getrennt. Termine bleiben erhalten.");}).show());
        JSONObject settings=new JSONObject();try(PixelLocalStore store=new PixelLocalStore(this)){settings=AppointmentRuntime.publicSettings(this,store);}catch(Exception unavailable){status.setText("Einstellungen nicht lesbar. Keine Änderung gespeichert.");return;}
        try{openedSettingsHash=new String[]{PixelMcpWriteTools.hash(settings)};}catch(Exception invalid){status.setText("Einstellungen nicht prüfbar.");return;}
        root=normal;calendar=check("Google Kalender · EIN / AUS",settings.optBoolean("calendar_enabled"));weather=check("Wetterhinweise · EIN / AUS",settings.optBoolean("weather_enabled"));notifications=check("Benachrichtigungen · EIN / AUS",settings.optBoolean("notifications"));root=advanced;
        calendar.setOnCheckedChangeListener((view,enabled)->{if(enabled&&!working){if(account.isEmpty()){connectAfterAccount=true;chooseAccount();}else connect();}});
        text("Arbeitszeiten für Vorschläge");String[] labels={"Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag","Sonntag"};JSONArray configured=settings.optJSONArray("workdays");for(int i=1;i<=7;i++){boolean selected=false;for(int j=0;configured!=null&&j<configured.length();j++)if(configured.optInt(j)==i)selected=true;days.add(check(labels[i-1],selected));}
        from=input("Arbeitsbeginn (HH:mm)",settings.optString("day_start","08:00"),false);to=input("Arbeitsende (HH:mm)",settings.optString("day_end","17:00"),false);travel=input("Anfahrtpuffer in Minuten",Integer.toString(settings.optInt("travel_minutes")),true);
        text("Wetterhinweise überwachen neue Termine automatisch, solange EIN gewählt ist. GeoSphere Austria · CC BY 4.0. Kein Konto oder API-Schlüssel. Erst näher am Termin ist eine belastbare Vorhersage verfügbar; fehlende Daten bedeuten unbekannt.");

        text("Wetterhinweise verwenden einstellbare betriebliche Schwellen, keine Sicherheitsfreigabe. Regen1 mm/h, Böen40 km/h, Frost0 °C und Hitze30 °C sind Ausgangswerte. Technische Quellen- und Prüfdetails bleiben im Hintergrund.");
        Map<String,EditText> thresholds=new LinkedHashMap<>();for(String[] item:new String[][]{{"rain_mm","Regen mm/h","1"},{"rain_probability","Regenwahrscheinlichkeit %","60"},{"gust_kmh","Böen km/h","40"},{"frost_c","Frost °C","0"},{"heat_c","Hitze °C","30"}})thresholds.put(item[0],input(item[1],settings.has(item[0])?settings.optString(item[0]):item[2],true));
        root=normal;
        button("Einstellungen speichern",()->{try(PixelLocalStore store=new PixelLocalStore(this)){
            if(calendar.isChecked()&&!PlanningAccount.connected(this)){status.setText("Google Kalender braucht zuerst deine Freigabe. Termine bleiben in der App gespeichert.");return;}
            JSONObject s=AppointmentRuntime.settings(store);JSONArray selected=new JSONArray();for(int i=0;i<days.size();i++)if(days.get(i).isChecked())selected.put(i+1);s.put("workdays",selected).put("day_start",from.getText().toString()).put("day_end",to.getText().toString()).put("travel_minutes",Integer.parseInt(travel.getText().toString()));for(Map.Entry<String,EditText> e:thresholds.entrySet())s.put(e.getKey(),Double.parseDouble(e.getValue().getText().toString()));
            s.put("calendar_enabled",calendar.isChecked()).put("weather_enabled",weather.isChecked()).put("weather_license_confirmed",true).put("notifications",notifications.isChecked());AppointmentRuntime.saveSettings(this,store,s,openedSettingsHash[0]);openedSettingsHash[0]=PixelMcpWriteTools.hash(AppointmentRuntime.publicSettings(this,store));
            p.edit().putBoolean("weather_enabled",weather.isChecked()).putBoolean("weather_license_confirmed",true).putBoolean("notifications",notifications.isChecked()).putBoolean("calendar_blocked",false).putBoolean("weather_blocked",false).putInt("calendar_failures",0).putInt("weather_failures",0).remove("calendar_retry_at").remove("weather_retry_at").apply();
            if(notifications.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);
            PlanningJob.schedule(this,1000);status.setText("Einstellungen gespeichert. Fehlende Gerätefreigaben werden bei Bedarf angefragt.");
        }catch(Exception e){status.setText(e instanceof IllegalArgumentException?e.getMessage():"Einstellungen nicht vollständig gespeichert. Angaben und Gerätefreigaben prüfen.");}});
        button("Zurück",this::finish);
    }
    private void connect(){
        if(account.isEmpty()){status.setText("Bitte Google-Konto auswählen.");return;}working=true;calendar.setEnabled(false);status.setText("Google-Freigabe und bestehenden App-Kalender prüfen …");final String selected=account;final boolean readBusy=busy.isChecked();
        new Thread(()->{try(PixelLocalStore store=new PixelLocalStore(this)){
            String token=PlanningAccount.token(this,selected,readBusy,this);CalendarClient client=new CalendarClient(new CalendarClient.Token(){public String get(){return token;}public void invalidate(String rejected){AccountManager.get(PlanningConnectionActivity.this).invalidateAuthToken("com.google",rejected);}},new PlanningWire());android.content.SharedPreferences p=PlanningAccount.prefs(this);
            String marker=p.getString("calendar_marker","");if(marker.isEmpty()){String device=new PixelTunnelConfig(this).id();marker="hausgarten.app.calendar.v1:"+(device.isEmpty()?UUID.randomUUID().toString():device);if(!p.edit().putString("calendar_marker",marker).commit())throw new java.io.IOException("Kalenderzuordnung konnte nicht gespeichert werden.");}
            final String pending="calendar-create:"+PixelMcpWriteTools.hash(selected+marker);
            JSONObject found=client.ensureOwnCalendar(marker,new CalendarClient.CalendarSetup(){public boolean pending(){return p.getBoolean(pending,false);}public void pending(boolean value)throws Exception{if(!p.edit().putBoolean(pending,value).commit())throw new java.io.IOException("Kalenderabsicht konnte nicht gesichert werden.");}});
            String calendarId=found.getString("id");if(!"owner".equals(found.optString("accessRole","owner")))throw new IllegalArgumentException("App-Kalender benötigt Eigentümerzugriff.");
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(!PixelMcpWriteTools.hash(AppointmentRuntime.publicSettings(this,store)).equals(openedSettingsHash[0]))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Planung wurde inzwischen geändert. Einstellungen erneut öffnen; Termine bleiben erhalten.");
            // Reset only transport mappings when deliberately connecting a different calendar.
            boolean different=!calendarId.equals(p.getString("calendar_id",""));if(different){store.putOwnerValue("calendar:token","");store.putOwnerValue("calendar:index","[]");store.putOwnerValue("calendar:busy","{}");store.putOwnerValue("calendar:dirty","{}");store.putOwnerValue("calendar:binding_namespace",calendarId);}
            JSONObject config=AppointmentRuntime.settings(store);config.put("calendar_enabled",true);AppointmentRuntime.saveSettings(this,store,config,openedSettingsHash[0]);
            if(!p.edit().putString("account",selected).putString("calendar_id",calendarId).putBoolean("read_busy",readBusy).putBoolean("calendar_blocked",false).putBoolean("weather_blocked",false).putInt("calendar_failures",0).putInt("weather_failures",0).remove("calendar_retry_at").remove("weather_retry_at").commit())throw new java.io.IOException("Kalenderfreigabe konnte nicht gespeichert werden.");openedSettingsHash[0]=PixelMcpWriteTools.hash(AppointmentRuntime.publicSettings(this,store));db.setTransactionSuccessful();
            }finally{db.endTransaction();}PlanningJob.schedule(this,1000);
            runOnUiThread(()->{working=true;calendar.setChecked(true);working=false;calendar.setEnabled(true);status.setText("Google Kalender: EIN. Gespeicherte Termine werden automatisch übertragen.");});
        }catch(Exception e){runOnUiThread(()->status.setText(e instanceof IllegalArgumentException?e.getMessage():"Google Kalender braucht deine Freigabe oder ist derzeit nicht erreichbar. Deine Termine bleiben in der App gespeichert."));}finally{working=false;runOnUiThread(()->calendar.setEnabled(true));}}).start();
    }
    private void chooseAccount(){try{startActivityForResult(AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},null,null,null,null),1);}catch(Exception unavailable){calendar.setChecked(false);status.setText("Google-Kontoauswahl auf diesem Gerät nicht verfügbar.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null){account=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);if(account==null)account="";accountLabel.setText(account);if(connectAfterAccount){connectAfterAccount=false;connect();}}else if(request==1&&connectAfterAccount){connectAfterAccount=false;calendar.setChecked(false);status.setText("Google Kalender: AUS. Termine bleiben in der App gespeichert.");}}
}
