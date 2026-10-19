package at.hausgarten.service;

import android.accounts.*;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Explicit device consent. Connection validation is read-only and never modifies a Drive folder. */
public final class DriveConnectionActivity extends Activity {
    private EditText folder;private TextView status,accountLabel;private Button connect;private String account="";
    @Override public void onCreate(Bundle state){super.onCreate(state);setTitle("Google Drive verbinden");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        TextView explanation=new TextView(this);explanation.setText(R.string.drive_connection_1);root.addView(explanation);
        accountLabel=new TextView(this);root.addView(accountLabel);Button choose=new Button(this);choose.setText(R.string.drive_connection_2);root.addView(choose);choose.setOnClickListener(v->{try{startActivityForResult(AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},null,null,null,null),1);}catch(Exception e){status.setText(R.string.drive_connection_3);}});
        folder=new EditText(this);folder.setSingleLine(true);folder.setHint("Link des bestehenden Drive-Ordners");folder.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);root.addView(folder);
        android.content.SharedPreferences p=getSharedPreferences("haus_garten",0);account=p.getString("drive_account","");accountLabel.setText(account.isEmpty()?getString(R.string.drive_connection_no_account):account);folder.setText(p.getString("drive_folder_id",""));
        connect=new Button(this);connect.setText(R.string.drive_connection_4);root.addView(connect);status=new TextView(this);root.addView(status);
        TextView note=new TextView(this);note.setText(R.string.drive_connection_5);root.addView(note);
        Button mail=new Button(this);mail.setText("Gmail-Versandbelege freigeben (nur Lesen)");root.addView(mail);mail.setOnClickListener(v->{String chosen=account;new Thread(()->{try{MailAccount.token(this,chosen,this);getSharedPreferences("haus_garten_mail",0).edit().putString("account",chosen).commit();runOnUiThread(()->status.setText("Gmail-Versandbelege lesbar. Kein Versand ausgelöst."));}catch(Exception e){runOnUiThread(()->status.setText("Gmail-Freigabe nicht bestätigt; Kontorechte am Gerät prüfen."));}}).start();});
        connect.setOnClickListener(v->validate());Button close=new Button(this);close.setText(R.string.drive_connection_6);root.addView(close);close.setOnClickListener(v->finish());
    }
    private void validate(){
        final String id;try{if(account.isEmpty())throw new IllegalArgumentException("Bitte zuerst ein Google-Konto auswählen.");id=DriveRestClient.folderId(folder.getText().toString());}catch(Exception e){status.setText(e.getMessage());return;}
        connect.setEnabled(false);status.setText(R.string.drive_connection_7);final String selected=account;
        new Thread(()->{try(MainActivity.LocalStore store=new MainActivity.LocalStore(this)){
            String granted=DriveAccount.token(this,selected,this);DriveRestClient client=new DriveRestClient(id,()->granted,new DriveRestClient.HttpWire(),new DriveRestClient.Checkpoints(){public String get(String k){return "";}public void put(String k,String v){}});client.validateFolder();
            JSONObject file=client.unique(id,"HausGarten-Laptop.json");if(file==null)throw new IllegalArgumentException("Der bestehende Gesamtstand HausGarten-Laptop.json fehlt. Es wird kein leerer Ersatz angelegt.");
            String content=new String(client.read(file.getString("id"),DriveRestClient.MAX_JSON),StandardCharsets.UTF_8);DriveJournalRules.validateSnapshot(new JSONObject(content));
            String prior=getSharedPreferences("haus_garten",0).getString("drive_tree_uri","");if(!prior.isEmpty()){
                String old=new DriveSyncCoordinator.Files(this,android.net.Uri.parse(prior),getSharedPreferences("haus_garten",0)).read("HausGarten-Laptop.json");
                if(old==null||!PixelMcpWriteTools.hash(new JSONObject(old)).equals(PixelMcpWriteTools.hash(new JSONObject(content))))throw new IllegalArgumentException("Der gewählte Ordner stimmt nicht mit dem bisherigen Gesamtstand überein. Bestehende Verbindung bleibt erhalten.");
            }
            if(!getSharedPreferences("haus_garten",0).edit().putString("drive_account",selected).putString("drive_folder_id",id).putString("drive_transport","rest").putInt("drive_retry_count",0).putBoolean("drive_user_action_required",false).putBoolean("drive_cloud_confirmed",false).putLong("drive_next_retry",0).putString("drive_last_error","").commit())throw new IllegalArgumentException("Verbindung konnte nicht dauerhaft gespeichert werden.");
            DriveSyncJob.install(this);DriveSyncCoordinator.request(this,500);runOnUiThread(()->{status.setText(R.string.drive_connection_8);connect.setEnabled(true);});
        }catch(Exception e){runOnUiThread(()->{status.setText(e instanceof DriveRestClient.Failure?e.getMessage():e instanceof IllegalArgumentException?e.getMessage():"Google-Verbindung konnte nicht bestätigt werden. Konto-, App- und Ordnerfreigabe prüfen. Bestehende Daten und Verbindung bleiben erhalten.");connect.setEnabled(true);});}}).start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null){account=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);if(account==null)account="";accountLabel.setText(account);}}
}
