package at.hausgarten.service;

import org.json.JSONObject;

/** Shared existing mobile duration/default rules; does not allocate or change any ID. */
final class WorklogRules {
    static void validate(JSONObject payload, boolean isNew) throws Exception {
        double pause = payload.optDouble("break_minutes", 0), km = payload.optDouble("kilometers", 0), hours = payload.optDouble("hours", 0);
        if (!Double.isFinite(pause) || !Double.isFinite(km) || !Double.isFinite(hours) || pause < 0 || km < 0 || hours < 0 || pause != Math.floor(pause))
            throw new IllegalArgumentException("Bitte gültige Stunden, Kilometer und eine Pause in ganzen Minuten eingeben.");
        String day = payload.optString("work_date");
        if (day.isEmpty()) { day = java.time.LocalDate.now().toString(); payload.put("work_date", day); }
        try { java.time.LocalDate.parse(day); } catch (Exception error) { throw new IllegalArgumentException("Bitte ein gültiges Arbeitsdatum auswählen."); }
        String start = payload.isNull("start_time") ? "" : payload.optString("start_time"), end = payload.isNull("end_time") ? "" : payload.optString("end_time");
        if (start.isEmpty() != end.isEmpty()) throw new IllegalArgumentException("Bitte Beginn und Ende gemeinsam angeben oder nur die Arbeitsdauer.");
        if (!start.isEmpty()) {
            int minutes = timeMinutes(end) - timeMinutes(start);
            if (minutes < 0 && payload.optBoolean("allow_overnight", false)) minutes += 1440;
            if (minutes <= 0) throw new IllegalArgumentException("Die Endzeit muss nach dem Beginn liegen.");
            if (pause >= minutes) throw new IllegalArgumentException("Die Pause muss kürzer als der gesamte Einsatz sein.");
            hours = Math.round((minutes - pause) / 60.0 * 100.0) / 100.0;
            payload.put("hours", hours);
        }
        if (isNew && hours <= 0) throw new IllegalArgumentException("Bitte Arbeitsdauer oder Beginn und Ende eintragen.");
        payload.put("break_minutes", (int)pause).put("kilometers", km).put("billed", payload.optInt("billed", 0)).put("customer_visible", payload.optInt("customer_visible", 1));
    }
    private static int timeMinutes(String value) {
        if (!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("Bitte gültige Uhrzeiten im Format HH:MM eingeben.");
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
    }
}
