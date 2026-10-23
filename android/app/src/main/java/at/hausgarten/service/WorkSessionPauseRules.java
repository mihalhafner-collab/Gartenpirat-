package at.hausgarten.service;
import org.json.*;
import java.time.Instant;

/** Pause evidence from immutable boundaries; legacy closed intervals are preserved. */
final class WorkSessionPauseRules {
    static JSONObject calculate(JSONArray events,String state,long now)throws Exception{
        return calculate(events,state,now,null);
    }
    static JSONObject calculate(JSONArray events,String state,long wall,long elapsed,String boot)throws Exception{
        return calculate(events,state,wall,new JSONObject().put("wall_ms",wall).put("elapsed_ms",elapsed).put("boot_marker",boot));
    }
    private static JSONObject calculate(JSONArray events,String state,long now,JSONObject currentClock)throws Exception{
        long total=0;JSONObject opened=null;boolean uncertain=false;
        for(int i=0;i<events.length();i++){
            JSONObject event=events.getJSONObject(i);String type=event.getString("event_type");
            if(type.equals("TIME_REVIEW")){
                total=Math.multiplyExact(event.getJSONObject("payload").getLong("break_minutes"),60000L);opened=null;uncertain=false;
            }else if(type.equals("PAUSE")||type.equals("RESTORE")){
                if(opened!=null&&type.equals("PAUSE"))uncertain=true;opened=event;
            }else if(type.equals("RESUME")||type.equals("CONTINUE")||type.equals("STOP")||type.equals("DISCARD")){
                if(opened!=null){long delta=duration(opened,event);if(delta<0)uncertain=true;else total=Math.addExact(total,delta);opened=null;}
                if(type.equals("STOP"))opened=event;
            }else if(type.equals("FINALIZE"))opened=null;
        }
        if(opened!=null&&state.equals("PAUSED")){
            JSONObject end=new JSONObject().put("occurred_at",Instant.ofEpochMilli(now).toString());
            if(currentClock!=null)end.put("payload",new JSONObject().put("_clock",currentClock));
            long delta=duration(opened,end);if(delta<0)uncertain=true;else total=Math.addExact(total,delta);
        }
        return new JSONObject().put("pause_ms",total).put("break_minutes",Math.round(total/60000.0)).put("pause_unverified",uncertain);
    }
    private static JSONObject clock(JSONObject event){JSONObject p=event.optJSONObject("payload");return p==null?null:p.optJSONObject("_clock");}
    private static long duration(JSONObject start,JSONObject end)throws Exception{
        JSONObject a=clock(start),b=clock(end);
        if(a!=null&&b!=null){
            String review=WorkSessionTimingRules.reviewReason(a.getLong("wall_ms"),a.getLong("elapsed_ms"),a.getString("boot_marker"),b.getLong("wall_ms"),b.getLong("elapsed_ms"),b.getString("boot_marker"));
            return review.isEmpty()?b.getLong("elapsed_ms")-a.getLong("elapsed_ms"):-1;
        }
        if(a!=null||b!=null)return -1;
        long delta=Instant.parse(end.getString("occurred_at")).toEpochMilli()-Instant.parse(start.getString("occurred_at")).toEpochMilli();
        return delta<0?-1:delta;
    }
}
