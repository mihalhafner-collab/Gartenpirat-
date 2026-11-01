package at.hausgarten.service;
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.time.Instant;
/** Read-only Gmail evidence adapter. It NEVER sends mail or trusts a client-supplied receipt. */
final class OfferDeliveryClient {
 private final CalendarClient.Token token;private final HttpTransport.Wire wire;
 OfferDeliveryClient(CalendarClient.Token token,HttpTransport.Wire wire){this.token=token;this.wire=wire;}
 JSONObject read(String path)throws Exception {HttpTransport.Reply r=wire.send("GET","https://www.googleapis.com/gmail/v1/users/me/"+path,Collections.singletonMap("Authorization","Bearer "+token.get()),null,28*1024*1024);if(r.status==401||r.status==403)throw new PixelMcpWriteTools.Rejected("MAIL_AUTH_REQUIRED","Gmail-Lesefreigabe für Versandbelege am Gerät bestätigen.");if(r.status==429||r.status>=500)throw new HttpTransport.Failure("MAIL_TEMPORARY_UNAVAILABLE","Mailanbieter vorübergehend nicht verfügbar. Später erneut prüfen; kein Versand wiederholen.",HttpTransport.retryAfter(r.header("Retry-After")),false);if(r.status!=200)throw new PixelMcpWriteTools.Rejected("MAIL_READBACK_REQUIRED","Versand nicht vom Mailanbieter bestätigt; kein sent-Status.");return r.json();}
 JSONObject verify(String id,JSONObject offer)throws Exception {
  if(!id.matches("[A-Za-z0-9_-]{1,200}"))throw new IllegalArgumentException("Ungültige Nachrichtenkennung.");JSONObject message=read("messages/"+id+"?format=full");
  if(!id.equals(message.optString("id"))||!message.getJSONArray("labelIds").toString().contains("\"SENT\""))throw new IllegalArgumentException("Nachricht ist nicht als versendet bestätigt.");
  JSONObject payload=message.getJSONObject("payload");Map<String,String> headers=new HashMap<>();JSONArray h=payload.getJSONArray("headers");for(int i=0;i<h.length();i++){String key=h.getJSONObject(i).getString("name").toLowerCase(Locale.ROOT);if((key.equals("to")||key.equals("from"))&&headers.containsKey(key))throw new IllegalArgumentException("Mehrdeutige Versandköpfe.");headers.put(key,h.getJSONObject(i).getString("value"));}
  String recipient=offer.optString("delivery_recipient"),sender=offer.getJSONObject("issuer").optString("business_email");
  if(recipient.isEmpty()||!oneAddress(headers.get("to")).equalsIgnoreCase(recipient)||sender.isEmpty()||!oneAddress(headers.get("from")).equalsIgnoreCase(sender))throw new IllegalArgumentException("Versandempfänger oder Aussteller stimmt nicht mit dem bestätigten Dokument überein.");
  boolean found=part(payload,id,offer.getString("pdf_sha256"),0,new int[]{0});if(!found)throw new IllegalArgumentException("Original-PDF im Anbieterbeleg nicht nachgewiesen.");
  return new JSONObject().put("provider","gmail").put("message_id",id).put("recipient_email",recipient).put("pdf_sha256",offer.getString("pdf_sha256")).put("sent_at",Instant.ofEpochMilli(Long.parseLong(message.getString("internalDate"))).toString()).put("verified_at",Instant.now().toString()).put("verified_by","pixel_gmail_readback");
 }
 private boolean part(JSONObject p,String message,String hash,int depth,int[] count)throws Exception {
  if(depth>8||++count[0]>100)throw new IllegalArgumentException("Mailstruktur überschreitet Prüfgrenze.");
  if(p.optString("mimeType").equals("application/pdf")){JSONObject body=p.getJSONObject("body");if(body.optLong("size")>20*1024*1024)throw new IllegalArgumentException("PDF zu groß.");String data=body.optString("data");if(data.isEmpty()&&!body.optString("attachmentId").isEmpty()){String id=body.getString("attachmentId");if(!id.matches("[A-Za-z0-9_-]{1,1000}"))throw new IllegalArgumentException("Ungültige Anhangkennung.");data=read("messages/"+message+"/attachments/"+id).getString("data");}if(data.length()>28*1024*1024)throw new IllegalArgumentException("Anhang überschreitet Prüfgrenze.");byte[] raw=Base64.getUrlDecoder().decode(data);if(raw.length>0&&raw.length<=20*1024*1024&&HttpTransport.sha256(raw).equals(hash))return true;}
  JSONArray parts=p.optJSONArray("parts");for(int i=0;parts!=null&&i<parts.length();i++)if(part(parts.getJSONObject(i),message,hash,depth+1,count))return true;return false;
 }
 static String oneAddress(String value){if(value==null||value.contains(",")||value.contains("\n")||value.contains("\r"))return "";int l=value.indexOf('<'),r=value.indexOf('>');String address=l>=0&&r>l?value.substring(l+1,r):value.trim();return address.matches("[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+")?address:"";}
}
