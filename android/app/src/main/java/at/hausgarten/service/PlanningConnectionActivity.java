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
    private TextView status,accountLabel;private String account="";private CheckBox busy,weather,license,notifications;private EditText key,from,to,travel;private final List<CheckBox> days=new ArrayList<>();private LinearLayout root;private boolean working;
    private TextView text(String value){TextView v=new TextView(this);v.setText(value);root.addView(v);return v;}
    private EditText input(String label,String value,boolean number){text(label);EditText v=new EditText(this);v.setText(value);v.setSingleLine(true);if(number)v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);root.addView(v);return v;}
    private CheckBox check(String label,boolean value){CheckBox v=new CheckBox(this);v.setText(label);v.setChecked(value);root.addView(v);return v;}
    private void button(String title,Runnable run){Button b=new Button(this);b.setText(title);root.addView(b);b.setOnClickListener(v->{if(!working)run.run();});}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle("Planung einrichten");root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        android.content.SharedPreferences p=PlanningAccount.prefs(this);account=p.getString("account","");
        text("Termine bleiben in Haus & Garten. Google Kalender zeigt verbundene Einsätze; Wetterhinweise verschieben nichts automatisch.");status=text("");accountLabel=text(account.isEmpty()?"Kalender nicht verbunden":account);
        button("Google-Konto auswählen",()->{try{startActivityForResult(AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},null,null,null,null),1);}catch(Exception e){status.setText("Google-Kontoauswahl auf diesem Gerät nicht verfügbar.");}});
        busy=check("Auch Belegtzeiten meines Hauptkalenders prüfen (zusätzliche Lesefreigabe)",p.getBoolean("read_busy",false));
        button("Google Kalender verbinden",()->new AlertDialog.Builder(this).setMessage("Kalenderrechte für das gewählte Konto freigeben und den App-Kalender Haus & Garten – Einsätze verwenden bzw. einmalig erstellen? Gespeicherte aktive Termine werden danach übertragen. Keine Kundeneinladungen werden gesendet.").setNegativeButton("Abbrechen",null).setPositiveButton("Verbinden",(d,w)->connect()).show());
        button("Kalenderverbindung trennen",()->new AlertDialog.Builder(this).setMessage("Nur Verbindung trennen? Gespeicherte App-Termine und Google-Einträge bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Trennen",(d,w)->{p.edit().remove("calendar_id").apply();status.setText("Getrennt. Termine bleiben erhalten.");}).show());
        JSONObject settings=new JSONObject();try(MainActivity.LocalStore store=new MainActivity.LocalStore(this)){settings=AppointmentRuntime.publicSettings(this,store);}catch(Exception unavailable){status.setText("Einstellungen nicht lesbar. Keine Änderung gespeichert.");return;}
        final String[] openedSettingsHash;try{openedSettingsHash=new String[]{PixelMcpWriteTools.hash(settings)};}catch(Exception invalid){status.setText("Einstellungen nicht prüfbar.");return;}
        text("Arbeitszeiten für Vorschläge");String[] labels={"Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag","Sonntag"};JSONArray configured=settings.optJSONArray("workdays");for(int i=1;i<=7;i++){boolean selected=false;for(int j=0;configured!=null&&j<configured.length();j++)if(configured.optInt(j)==i)selected=true;days.add(check(labels[i-1],selected));}
        from=input("Arbeitsbeginn (HH:mm)",settings.optString("day_start","08:00"),false);to=input("Arbeitsende (HH:mm)",settings.optString("day_end","17:00"),false);travel=input("Anfahrtpuffer in Minuten",Integer.toString(settings.optInt("travel_minutes")),true);
        text("Wetter: Open-Meteo für gewerbliche Nutzung. Hier wird kein Vertrag abgeschlossen. Vorhandenen freigegebenen Kundenschlüssel nur hier am Gerät eingeben. Schlüssel bleibt privat und wird nicht an ChatGPT ausgegeben.");
        weather=check("Wetterprüfung aktivieren",settings.optBoolean("weather_enabled"));license=check("Gewerbliche Nutzung dieses Wetterzugangs ist freigegeben",settings.optBoolean("weather_license_confirmed"));key=input(p.contains("weather_secret")?"Neuen API-Schlüssel eingeben oder leer lassen, um vorhandenen zu behalten":"API-Schlüssel des bestehenden Wetterzugangs","",false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        notifications=check("Bei neuen Wetterrisiken benachrichtigen (Ruhezeit21–6Uhr)",settings.optBoolean("notifications"));
        text("Wetterhinweise verwenden einstellbare betriebliche Schwellen, keine Sicherheitsfreigabe. Regen1mm/h bei60%, Böen40km/h, Frost0°C und Hitze30°C sind Ausgangswerte. API-Herkunft: Open-Meteo; amtliche Warnungen: GeoSphere Austria, CC BY4.0.");
        Map<String,EditText> thresholds=new LinkedHashMap<>();for(String[] item:new String[][]{{"rain_mm","Regen mm/h","1"},{"rain_probability","Regenwahrscheinlichkeit %","60"},{"gust_kmh","Böen km/h","40"},{"frost_c","Frost °C","0"},{"heat_c","Hitze °C","30"}})thresholds.put(item[0],input(item[1],settings.has(item[0])?settings.optString(item[0]):item[2],true));
        button("Einstellungen speichern",()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(this)){
            if(weather.isChecked()&&!license.isChecked())throw new IllegalArgumentException("Gewerbliche Wetterfreigabe zuerst bestätigen.");if(weather.isChecked()&&key.getText().length()==0&&!p.contains("weather_secret"))throw new IllegalArgumentException("Wetterzugang fehlt. Planung kann ohne Wetter verwendet werden.");
            JSONObject s=AppointmentRuntime.settings(store);JSONArray selected=new JSONArray();for(int i=0;i<days.size();i++)if(days.get(i).isChecked())selected.put(i+1);s.put("workdays",selected).put("day_start",from.getText().toString()).put("day_end",to.getText().toString()).put("travel_minutes",Integer.parseInt(travel.getText().toString()));for(Map.Entry<String,EditText> e:thresholds.entrySet())s.put(e.getKey(),Double.parseDouble(e.getValue().getText().toString()));
            // Validate before retaining a new credential; secret input is never mirrored to JS.
            s.put("weather_enabled",weather.isChecked()).put("weather_license_confirmed",license.isChecked()).put("notifications",notifications.isChecked());AppointmentRuntime.saveSettings(this,store,s,openedSettingsHash[0]);openedSettingsHash[0]=PixelMcpWriteTools.hash(AppointmentRuntime.publicSettings(this,store));if(key.getText().length()>0){PlanningAccount.weatherKey(this,key.getText().toString().trim());key.setText("");}
            p.edit().putBoolean("weather_enabled",weather.isChecked()).putBoolean("weather_license_confirmed",license.isChecked()).putBoolean("notifications",notifications.isChecked()).putBoolean("calendar_blocked",false).putBoolean("weather_blocked",false).putInt("calendar_failures",0).putInt("weather_failures",0).remove("calendar_retry_at").remove("weather_retry_at").apply();
            if(notifications.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);
            PlanningJob.schedule(this,1000);status.setText("Gespeichert. Kalender, Wetter und Benachrichtigungsrechte werden getrennt geprüft.");
        }catch(Exception e){status.setText(e instanceof IllegalArgumentException?e.getMessage():"Einstellungen nicht vollständig gespeichert. Angaben und Gerätefreigaben prüfen.");}});
        button("Zurück",this::finish);
    }
    private void connect(){
        if(account.isEmpty()){status.setText("Bitte Google-Konto auswählen.");return;}working=true;status.setText("Google-Freigabe und bestehenden App-Kalender prüfen …");final String selected=account;final boolean readBusy=busy.isChecked();
        new Thread(()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(this)){
            String token=PlanningAccount.token(this,selected,readBusy,this);CalendarClient client=new CalendarClient(()->token,new PlanningWire());android.content.SharedPreferences p=PlanningAccount.prefs(this);
            String marker="hausgarten.app.calendar.v1:"+new PixelTunnelConfig(this).id();JSONArray calendars=client.calendars();JSONObject found=null;
            for(int i=0;i<calendars.length();i++){JSONObject row=calendars.getJSONObject(i);if(marker.equals(row.optString("description"))){if(found!=null)throw new IllegalArgumentException("Mehrere App-Kalender gefunden. Google-Kalenderzuordnung zuerst prüfen; keine weitere Anlage.");found=row;}}
            if(found==null){String pending="calendar-create:"+PixelMcpWriteTools.hash(selected+marker);if(p.getBoolean(pending,false))throw new IllegalArgumentException("Frühere Kalenderanlage unbestätigt. Google-Kalenderliste erneut prüfen; kein zweiter Kalender wird angelegt.");p.edit().putBoolean(pending,true).commit();try{found=client.createCalendar(marker);}catch(DriveRestClient.Failure rejected){if(rejected.userAction)p.edit().remove(pending).commit();throw rejected;}}
            String calendar=found.getString("id");if(!"owner".equals(found.optString("accessRole","owner")))throw new IllegalArgumentException("App-Kalender benötigt Eigentümerzugriff.");
            // Reset only transport mappings when deliberately connecting a different calendar.
            boolean different=!calendar.equals(p.getString("calendar_id",""));if(different){store.putOwnerValue("calendar:token","");store.putOwnerValue("calendar:index","[]");store.putOwnerValue("calendar:busy","{}");store.putOwnerValue("calendar:dirty","{}");store.putOwnerValue("calendar:binding_namespace",calendar);}
            JSONObject config=AppointmentRuntime.settings(store);config.put("calendar_enabled",true);AppointmentRuntime.saveSettings(store,config);
            p.edit().putString("account",selected).putString("calendar_id",calendar).putBoolean("read_busy",readBusy).putBoolean("calendar_blocked",false).putBoolean("weather_blocked",false).putInt("calendar_failures",0).putInt("weather_failures",0).remove("calendar_retry_at").remove("weather_retry_at").commit();PlanningJob.schedule(this,1000);
            runOnUiThread(()->status.setText("Verbunden. Übertragung läuft nach Geräteplanung; Bestätigung in der App prüfen."));
        }catch(Exception e){runOnUiThread(()->status.setText(e instanceof IllegalArgumentException?e.getMessage():"Kalender nicht verbunden. Calendar API, OAuth-Zuordnung, Konto-/Organisationsfreigabe und Netz prüfen."));}finally{working=false;}}).start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null){account=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);if(account==null)account="";accountLabel.setText(account);}}
}
