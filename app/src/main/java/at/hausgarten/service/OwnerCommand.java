package at.hausgarten.service;
import org.json.JSONObject;

/** Application operation contract; services do not depend on their router. */
interface OwnerCommand {
    JSONObject perform(String name, JSONObject args, String request) throws Exception;
}
