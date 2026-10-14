package at.hausgarten.service;
import org.json.*;
import java.time.Instant;

/** Derives rounded pause information from existing events; never changes net worked_ms. */
final class WorkSessionPauseRules {
    static JSONObject calculate(JSONArray events,String state,long now)throws Exception{
        long total=0,opened=-1;boolean uncertain=false;
        for(int i=0;i<events.length();i++){
            JSONObject event=events.getJSONObject(i);String type=event.getString("event_type");long at=Instant.parse(event.getString("occurred_at")).toEpochMilli();
            if(type.equals("PAUSE")||type.equals("RESTORE")){if(opened>=0&&type.equals("PAUSE"))uncertain=true;opened=at;}
            else if(type.equals("RESUME")||type.equals("CONTINUE")||type.equals("STOP")||type.equals("DISCARD")){
                if(opened>=0){if(at<opened)uncertain=true;else total+=at-opened;opened=-1;}
                // A subsequent CONTINUE excludes the stopped gap; a final STOP does not add review time.
                if(type.equals("STOP"))opened=at;
            }else if(type.equals("FINALIZE"))opened=-1;
        }
        if(opened>=0&&state.equals("PAUSED")){if(now<opened)uncertain=true;else total+=now-opened;}
        return new JSONObject().put("pause_ms",total).put("break_minutes",Math.round(total/60000.0)).put("pause_unverified",uncertain);
    }
}
