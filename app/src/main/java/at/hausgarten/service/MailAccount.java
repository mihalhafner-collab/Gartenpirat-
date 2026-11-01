package at.hausgarten.service;
import android.accounts.*;import android.app.Activity;import android.content.Context;import android.os.Bundle;
/** Separate, explicit readonly mail consent; neither Drive nor ChatGPT consent grants this. */
final class MailAccount {
 static String token(Context c,String account,Activity consent)throws Exception {
  if(account.isEmpty())throw new PixelMcpWriteTools.Rejected("MAIL_AUTH_REQUIRED","Unter Drive-Verbindung ein Google-Konto wählen und Gmail-Versandbelege freigeben.");
  AccountManager manager=AccountManager.get(c);Account selected=new Account(account,"com.google");String scope="oauth2:https://www.googleapis.com/auth/gmail.readonly";
  Bundle b=consent==null?manager.getAuthToken(selected,scope,null,false,null,null).getResult():manager.getAuthToken(selected,scope,null,consent,null,null).getResult();String token=b.getString(AccountManager.KEY_AUTHTOKEN,"");
  if(token.isEmpty())throw new PixelMcpWriteTools.Rejected("MAIL_AUTH_REQUIRED","Gmail-Versandbelege am Gerät freigeben.");return token;
 }
 static OfferDeliveryClient client(Context c){String account=c.getSharedPreferences("haus_garten_mail",0).getString("account","");return new OfferDeliveryClient(()->token(c,account,null),new PlanningWire());}
 private MailAccount(){}
}
