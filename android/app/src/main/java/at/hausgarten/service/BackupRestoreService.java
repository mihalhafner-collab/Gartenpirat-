package at.hausgarten.service;

import android.content.*;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Xml;
import org.json.*;
import org.xmlpull.v1.XmlSerializer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Fully authenticated preview, then a protected cold-start exchange. Never merges two inventories. */
final class BackupRestoreService {
    interface Protection {
        AutoCloseable enterExclusiveStartup()throws Exception;
        ActivePaths activePaths()throws Exception;
        JSONObject preserveCurrent(String requestId,String incomingRecoveryCode)throws Exception;
        void blockBeforeActivation(String requestId,JSONObject restoredManifest,JSONObject currentBackupProof)throws Exception;
        void afterActivation(String requestId,JSONObject restoredManifest,File restoredDatabase)throws Exception;
    }
    interface Source {InputStream open()throws Exception;}
    static final class ActivePaths {
        final LinkedHashMap<String,File> paths=new LinkedHashMap<>();
        ActivePaths(File database,File filesDir,File cacheDir,File noBackupDir,Map<String,File> preferenceFiles)throws Exception {
            paths.put("database_wal",new File(database+"-wal"));paths.put("database_shm",new File(database+"-shm"));paths.put("database_journal",new File(database+"-journal"));paths.put("database",database);
            paths.put("files",filesDir);paths.put("photo_capture",new File(cacheDir,"photo-capture"));paths.put("invoice_share",new File(cacheDir,"invoice-share"));paths.put("deletion_backups",new File(noBackupDir,"deletion-backups"));paths.put("legacy_conflict_backups",new File(noBackupDir,"legacy-conflict-backups"));
            for(String group:BackupPreferences.GROUPS){File file=preferenceFiles.get(group);if(file==null)throw new IOException("RESTORE_PREFERENCE_PATH_REQUIRED");paths.put("preferences_"+group+"_bak",new File(file+".bak"));paths.put("preferences_"+group,file);}
            ArrayList<File> seen=new ArrayList<>();for(File value:paths.values()){File canonical=value.getCanonicalFile();if(Files.isSymbolicLink(value.toPath()))throw new IOException("RESTORE_ACTIVE_PATH_LINK_REJECTED");for(File other:seen)if(canonical.equals(other)||canonical.toPath().startsWith(other.toPath())||other.toPath().startsWith(canonical.toPath()))throw new IOException("RESTORE_ACTIVE_PATH_OVERLAP");seen.add(canonical);}
        }
        File database(){return paths.get("database");}
    }
    private static final Object LOCK=new Object();
    private static final Set<String> PENDING=new HashSet<>(Arrays.asList("REQUESTED","ACTIVATING","SWAPPED","ROLLING_BACK","FAILED_HOLD"));
    private final Context context;private final File root;
    BackupRestoreService(Context context)throws Exception {this.context=context;root=new File(context.getNoBackupFilesDir(),"full-restore");if(Files.isSymbolicLink(root.toPath()))throw new IOException("RESTORE_STAGING_LINK_REJECTED");}
    JSONObject preview(String requestId,Uri archiveUri,String recoveryCode)throws Exception {
        if(archiveUri==null||!"content".equals(archiveUri.getScheme()))throw new IOException("RESTORE_DOCUMENT_REQUIRED");
        return previewFrom(requestId,archiveUri.toString(),recoveryCode,()->{InputStream input=context.getContentResolver().openInputStream(archiveUri);if(input==null)throw new IOException("RESTORE_SOURCE_UNAVAILABLE");return input;});
    }
    JSONObject previewFrom(String requestId,String sourceId,String recoveryCode,Source source)throws Exception {
        request(requestId);byte[] material=BackupCrypto.recoveryKey(recoveryCode);String keyHash;
        try{keyHash=hex(MessageDigest.getInstance("SHA-256").digest(material));}finally{Arrays.fill(material,(byte)0);}
        JSONObject identity=new BackupDeviceGuard(context).current();String restoreId="restore_"+PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("installationId",identity.getString("installationId")).put("restoreEpoch",identity.getString("restoreEpoch"))).substring(0,36);
        String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("source",sourceId).put("recovery_binding",keyHash).put("identity",identity));
        synchronized(LOCK){
            File work=work(restoreId);File info=new File(work,"preview.json");JSONObject record=readOptional(info);
            if(record!=null&&record.optInt("formatVersion")!=1)throw new IOException("RESTORE_PREVIEW_SCHEMA_UNSUPPORTED");
            if(record!=null&&!record.getString("request_fingerprint").equals(fingerprint))throw new IOException("RESTORE_REPLAY_MISMATCH");
            if(record!=null&&record.optString("phase").equals("VALIDATED")){validateStage(work,record);return previewView(record);}
            if(record!=null&&!Arrays.asList("VALIDATING","FAILED").contains(record.optString("phase")))throw new IOException("RESTORE_PREVIEW_NOT_REUSABLE");
            record=new JSONObject().put("formatVersion",1).put("restore_id",restoreId).put("request_id",requestId).put("request_fingerprint",fingerprint).put("installation_id",identity.getString("installationId")).put("expected_restore_epoch",identity.getString("restoreEpoch")).put("phase","VALIDATING").put("started_at",Instant.now().toString());
            String directory="stage-"+UUID.randomUUID();record.put("directory",directory);write(info,record);
            EncryptedBackupArchive.Verified staged=null;
            try{
                BackupStorageBudget.require(work,0);staged=EncryptedBackupArchive.verifyAndStage(source.open(),recoveryCode,child(work,directory),Math.max(0,work.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES),PixelLocalStore.SCHEMA_VERSION);
                LocalBackupDataset.inspectStaged(new File(staged.directory,"data"),staged.manifest);
                write(new File(work,"manifest.json"),staged.manifest);String manifestHash=EncryptedBackupArchive.sha256(new File(work,"manifest.json"));
                record.put("manifest_sha256",manifestHash).put("preview_hash",PixelMcpWriteTools.hash(new JSONObject().put("restore_id",restoreId).put("manifest_sha256",manifestHash).put("request_fingerprint",fingerprint))).put("phase","VALIDATED").put("validated_at",Instant.now().toString());
                BackupRecoveryKeyStore.scoped(context,"restore_"+restoreId).confirmAndStore(requestId,recoveryCode,recoveryCode);write(info,record);return previewView(record);
            }catch(Exception failure){if(staged!=null)try{staged.close();}catch(Exception ignored){}record.put("phase","FAILED").put("last_error_code",safeCode(failure));write(info,record);throw failure;}
        }
    }
    JSONObject previousPreservation()throws Exception {
        synchronized(LOCK){JSONObject state=readOptional(activationFile());if(state==null||!state.has("current_backup_proof"))return new JSONObject().put("available",false);activationVersion(state);JSONObject proof=state.getJSONObject("current_backup_proof");File archive=previousArchive(state.getString("request_id"),proof);return new JSONObject().put("available",archive.isFile()).put("activation_request_id",state.getString("request_id")).put("restore_id",state.getString("restore_id")).put("backup_id",proof.getString("backup_id")).put("captured_at",proof.getString("captured_at")).put("byte_size",proof.getLong("byte_size"));}
    }
    JSONObject previewPrevious(String requestId,String activationRequestId,String recoveryCode)throws Exception {
        request(requestId);request(activationRequestId);synchronized(LOCK){JSONObject state=readOptional(receipt(activationRequestId));if(state==null){JSONObject current=readOptional(activationFile());if(current!=null&&current.optString("request_id").equals(activationRequestId))state=current;}if(state==null)throw new IOException("RESTORE_PREVIOUS_PROOF_REQUIRED");activationVersion(state);JSONObject proof=state.getJSONObject("current_backup_proof");File archive=previousArchive(activationRequestId,proof);if(!archive.isFile()||archive.length()!=proof.getLong("byte_size")||!EncryptedBackupArchive.sha256(archive).equals(proof.getString("archive_sha256")))throw new IOException("RESTORE_PREVIOUS_ARCHIVE_CHANGED");return previewFrom(requestId,"previous-preservation:"+activationRequestId+":"+proof.getString("archive_sha256"),recoveryCode,()->new FileInputStream(archive));}
    }
    private File previousArchive(String activationRequestId,JSONObject proof)throws Exception {
        validateProof(proof);String relative="before-restore/"+PixelMcpWriteTools.hash(activationRequestId)+"/generation.hgb";if(!relative.equals(proof.optString("private_archive_ref")))throw new IOException("RESTORE_PREVIOUS_PROOF_REQUIRED");File archive=new File(context.getNoBackupFilesDir(),relative);if(Files.isSymbolicLink(archive.toPath())||!archive.getCanonicalPath().startsWith(context.getNoBackupFilesDir().getCanonicalPath()+File.separator))throw new IOException("RESTORE_PREVIOUS_ARCHIVE_CHANGED");return archive;
    }
    JSONObject requestActivation(String requestId,String restoreId,String expectedPreviewHash,boolean confirmed)throws Exception {
        return requestActivation(requestId,restoreId,expectedPreviewHash,confirmed,false);
    }
    JSONObject requestActivation(String requestId,String restoreId,String expectedPreviewHash,boolean confirmed,boolean legacyMediaCoverageConfirmed)throws Exception {
        request(requestId);if(!confirmed)throw new IOException("RESTORE_CONFIRMATION_REQUIRED");hash(expectedPreviewHash);
        synchronized(LOCK){
            JSONObject intent=new JSONObject().put("request_id",requestId).put("restore_id",restoreId).put("preview_hash",expectedPreviewHash).put("confirmed",true);if(legacyMediaCoverageConfirmed)intent.put("legacy_media_coverage_confirmed",true);String fingerprint=PixelMcpWriteTools.hash(intent);File receipt=receipt(requestId);
            if(receipt.exists()||new File(receipt+".bak").exists()){JSONObject previous=read(receipt);if(!previous.getString("request_fingerprint").equals(fingerprint))throw new IOException("RESTORE_ACTIVATION_REPLAY_MISMATCH");JSONObject current=readOptional(activationFile());return activationView(current!=null&&current.optString("request_id").equals(requestId)?current:previous);}
            JSONObject pending=readOptional(activationFile());if(pending!=null)activationVersion(pending);
            if(pending!=null&&pending.optString("request_id").equals(requestId)){if(!pending.getString("request_fingerprint").equals(fingerprint))throw new IOException("RESTORE_ACTIVATION_REPLAY_MISMATCH");write(receipt,pending);return activationView(pending);}
            if(pending!=null&&PENDING.contains(pending.optString("phase")))throw new IOException("RESTORE_OTHER_ACTIVATION_PENDING");
            File work=work(restoreId);JSONObject record=read(new File(work,"preview.json"));if(!record.getString("phase").equals("VALIDATED")||!record.getString("preview_hash").equals(expectedPreviewHash))throw new IOException("RESTORE_PREVIEW_CHANGED");requireTargetContext(record);JSONObject manifest=validateStage(work,record);String coverage=mediaCoverage(manifest);if(coverage.equals("LEGACY_NOT_PROVEN")&&!legacyMediaCoverageConfirmed)throw new IOException("RESTORE_MEDIA_COVERAGE_CONFIRMATION_REQUIRED");
            JSONObject request=new JSONObject().put("formatVersion",1).put("request_id",requestId).put("request_fingerprint",fingerprint).put("restore_id",restoreId).put("preview_hash",expectedPreviewHash).put("phase","REQUESTED").put("requested_at",Instant.now().toString()).put("media_coverage",coverage).put("legacy_media_coverage_confirmed",legacyMediaCoverageConfirmed);
            write(activationFile(),request);write(receipt,request);return activationView(request);
        }
    }
    JSONObject cancelActivation(String requestId,String restoreId,String expectedPreviewHash)throws Exception {
        request(requestId);hash(expectedPreviewHash);synchronized(LOCK){JSONObject state=read(activationFile());activationVersion(state);
            if(!state.getString("restore_id").equals(restoreId)||!state.getString("preview_hash").equals(expectedPreviewHash))throw new IOException("RESTORE_PREVIEW_CHANGED");
            if(state.optString("phase").equals("CANCELLED")){if(!state.optString("cancel_request_id").equals(requestId))throw new IOException("RESTORE_CANCELLATION_REPLAY_MISMATCH");return activationView(state);}
            if(!Arrays.asList("REQUESTED","FAILED_HOLD","ROLLED_BACK").contains(state.optString("phase"))||state.has("targets")&&BackupDatasetExchange.anyOldMoved(state)&&!state.optString("phase").equals("ROLLED_BACK"))throw new IOException("RESTORE_CANCELLATION_REQUIRES_RECOVERY");
            state.put("phase","CANCELLED").put("cancel_request_id",requestId);write(receipt(state.getString("request_id")),state);write(activationFile(),state);return activationView(state);
        }
    }
    JSONObject status(String restoreId)throws Exception {synchronized(LOCK){JSONObject activation=readOptional(activationFile());if(activation!=null&&activation.optString("restore_id").equals(restoreId))return activationView(activation);return previewView(read(new File(work(restoreId),"preview.json")));}}
    static JSONObject pendingStatus(Context context)throws Exception {JSONObject state=readOptional(new File(context.getNoBackupFilesDir(),"full-restore/activation.json"));return state==null?new JSONObject().put("phase","NONE"):activationView(state);}
    static boolean requiresExclusiveStartup(Context context){try{JSONObject state=readOptional(new File(context.getNoBackupFilesDir(),"full-restore/activation.json"));if(state==null)return false;activationVersion(state);return PENDING.contains(state.optString("phase"));}catch(Exception damaged){return true;}}
    static JSONObject activatePending(Context context,Protection protection)throws Exception {
        BackupRestoreService service=new BackupRestoreService(context);synchronized(LOCK){JSONObject state=readOptional(service.activationFile());if(state==null)return new JSONObject().put("phase","NONE");activationVersion(state);
            if(!PENDING.contains(state.optString("phase"))){if(state.has("request_id"))write(service.receipt(state.getString("request_id")),state);return activationView(state);}
            if(protection==null)throw new IOException("RESTORE_PROTECTION_REQUIRED");
            try(AutoCloseable exclusive=protection.enterExclusiveStartup()){
                File work=service.work(state.getString("restore_id"));JSONObject record=read(new File(work,"preview.json"));JSONObject manifest=read(new File(work,"manifest.json"));
                if(!state.getString("preview_hash").equals(record.getString("preview_hash"))||!EncryptedBackupArchive.sha256(new File(work,"manifest.json")).equals(record.getString("manifest_sha256")))throw new IOException("RESTORE_PREVIEW_CHANGED");String coverage=mediaCoverage(manifest);if(coverage.equals("LEGACY_NOT_PROVEN")&&!state.optBoolean("legacy_media_coverage_confirmed")||state.has("media_coverage")&&!coverage.equals(state.getString("media_coverage")))throw new IOException("RESTORE_MEDIA_COVERAGE_CONFIRMATION_REQUIRED");
                JSONObject hookManifest=new JSONObject(manifest.toString()).put("_activation_context",new JSONObject().put("installation_id",record.getString("installation_id")).put("expected_restore_epoch",record.getString("expected_restore_epoch")).put("restore_id",record.getString("restore_id"))).put("_restored_browser_checkpoint",service.activationBrowser(work,record,manifest));
                final JSONObject[] current={state};BackupDatasetExchange.Journal journal=new BackupDatasetExchange.Journal(){public JSONObject read(){return current[0];}public void save(JSONObject value)throws Exception{write(service.activationFile(),value);current[0]=value;}};
                try{
                    if(!state.has("targets")){
                        service.requireTargetContext(record);service.validateStage(work,record);String recovery=BackupRecoveryKeyStore.scoped(context,"restore_"+record.getString("restore_id")).read();
                        JSONObject proof=state.optJSONObject("current_backup_proof");if(proof==null){proof=protection.preserveCurrent(state.getString("request_id"),recovery);validateProof(proof);state.put("current_backup_proof",proof);journal.save(state);}else validateProof(proof);
                        ActivePaths active=protection.activePaths();Map<String,File> prepared=service.prepareTargets(work,record,manifest);state.put("targets",BackupDatasetExchange.targets(active.paths,prepared,new File(work,"rollback"))).put("phase","ACTIVATING");journal.save(state);
                    }
                    protection.blockBeforeActivation(state.getString("request_id"),hookManifest,state.getJSONObject("current_backup_proof"));
                    if(state.getString("phase").equals("ROLLING_BACK")||state.getString("phase").equals("FAILED_HOLD")){BackupDatasetExchange.rollback(journal,BackupDatasetExchange.ATOMIC);state=current[0];write(service.receipt(state.getString("request_id")),state);return activationView(state);}
                    if(!state.getString("phase").equals("SWAPPED"))BackupDatasetExchange.install(journal,BackupDatasetExchange.ATOMIC);
                    protection.afterActivation(state.getString("request_id"),hookManifest,protection.activePaths().database());state=current[0];state.put("phase","ACTIVE").put("activated_at",Instant.now().toString());write(service.receipt(state.getString("request_id")),state);journal.save(state);return activationView(state);
                }catch(Exception failure){
                    state=current[0];state.put("last_error_code",safeCode(failure));
                    if(state.has("targets"))try{BackupDatasetExchange.rollback(journal,BackupDatasetExchange.ATOMIC);state=current[0];state.put("last_error_code",safeCode(failure));write(service.receipt(state.getString("request_id")),state);journal.save(state);}catch(Exception rollbackFailure){state=current[0];state.put("phase","FAILED_HOLD").put("last_error_code","RESTORE_ROLLBACK_REQUIRES_REVIEW");journal.save(state);}
                    else{state.put("phase","FAILED_HOLD");journal.save(state);}
                    throw failure;
                }
            }
        }
    }
    /** This file stays in staging during component moves; recheck its immutable manifest binding on resume. */
    private JSONObject activationBrowser(File work,JSONObject record,JSONObject manifest)throws Exception {
        File data=new File(child(work,record.getString("directory")),"data"),browser=new File(data,"browser-storage.json");JSONObject expected=null;JSONArray files=manifest.getJSONArray("files");for(int i=0;i<files.length();i++)if(files.getJSONObject(i).getString("path").equals("browser-storage.json")){if(expected!=null)throw new IOException("RESTORE_STAGED_FILE_CHANGED");expected=files.getJSONObject(i);}
        if(expected==null||Files.isSymbolicLink(browser.toPath())||!browser.isFile()||browser.length()!=expected.getLong("size")||!EncryptedBackupArchive.sha256(browser).equals(expected.getString("sha256")))throw new IOException("RESTORE_STAGED_FILE_CHANGED");return RestoredBrowserReviewRules.validate(new JSONObject(readText(browser,16*1024*1024)));
    }
    private Map<String,File> prepareTargets(File work,JSONObject record,JSONObject manifest)throws Exception {
        File data=new File(child(work,record.getString("directory")),"data");LinkedHashMap<String,File> targets=new LinkedHashMap<>();targets.put("database",new File(data,"database.sqlite"));
        for(String[] item:new String[][]{{"files","files"},{"photo_capture","cache/photo-capture"},{"invoice_share","cache/invoice-share"},{"deletion_backups","no_backup/deletion-backups"},{"legacy_conflict_backups","no_backup/legacy-conflict-backups"}}){File directory=new File(data,item[1]);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("RESTORE_PREPARED_STORAGE_UNAVAILABLE");targets.put(item[0],directory);}
        JSONObject preferences=new JSONObject(readText(new File(data,"preferences.json"),Integer.MAX_VALUE));File prepared=new File(work,"prepared-preferences");if(!prepared.isDirectory()&&!prepared.mkdirs())throw new IOException("RESTORE_PREPARED_STORAGE_UNAVAILABLE");
        for(String group:BackupPreferences.GROUPS){JSONObject values=BackupPreferences.values(preferences,group),types=preferences.getJSONObject("groups").getJSONObject(group).getJSONObject("types");JSONObject safe=new JSONObject(values.toString()),safeTypes=new JSONObject(types.toString());sanitize(group,safe,safeTypes);File file=new File(prepared,group+".xml");writePreferences(file,safe,safeTypes);targets.put("preferences_"+group,file);}
        return targets;
    }
    private static void sanitize(String group,JSONObject values,JSONObject types)throws Exception {
        for(Iterator<String> it=keys(values).iterator();it.hasNext();){String key=it.next(),lower=key.toLowerCase(Locale.ROOT);if(lower.contains("secret")||lower.contains("token")||lower.contains("password")||lower.contains("credential")||lower.contains("api_key")||lower.equals("weather_iv")){values.remove(key);types.remove(key);}}
        if(group.equals("haus_garten")){for(String key:new String[]{"drive_account","drive_folder_id","drive_tree_uri","drive_transport"}){values.put(key,"");types.put(key,"string");}values.put("drive_automatic_enabled",false);types.put("drive_automatic_enabled","boolean");}
        if(group.equals("planning_private")){for(String key:new String[]{"account","calendar_id"}){values.put(key,"");types.put(key,"string");}for(String key:new String[]{"weather_enabled","read_busy","calendar_sync_enabled"}){values.put(key,false);types.put(key,"boolean");}}
        if(group.equals("haus_garten_mail")){values.put("account","");types.put("account","string");}
    }
    private static void writePreferences(File file,JSONObject values,JSONObject types)throws Exception {
        try(FileOutputStream output=new FileOutputStream(file)){XmlSerializer xml=Xml.newSerializer();xml.setOutput(output,"UTF-8");xml.startDocument("UTF-8",true);xml.startTag(null,"map");ArrayList<String> keys=keys(values);Collections.sort(keys);for(String key:keys){String type=types.getString(key);Object value=values.get(key);switch(type){case "string":xml.startTag(null,"string").attribute(null,"name",key).text((String)value).endTag(null,"string");break;case "string_set":xml.startTag(null,"set").attribute(null,"name",key);JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++)xml.startTag(null,"string").text(list.getString(i)).endTag(null,"string");xml.endTag(null,"set");break;case "boolean":case "int":case "long":case "float":String raw=type.equals("float")?Float.toString(Float.valueOf((String)value)):value.toString();xml.startTag(null,type).attribute(null,"name",key).attribute(null,"value",raw).endTag(null,type);break;default:throw new IOException("RESTORE_PREFERENCE_TYPE_UNSUPPORTED");}}xml.endTag(null,"map");xml.endDocument();xml.flush();output.getFD().sync();}
    }
    private JSONObject validateStage(File work,JSONObject record)throws Exception {
        File manifestFile=new File(work,"manifest.json");if(!manifestFile.isFile()||!EncryptedBackupArchive.sha256(manifestFile).equals(record.getString("manifest_sha256")))throw new IOException("RESTORE_PREVIEW_CHANGED");JSONObject manifest=read(manifestFile);File stage=child(work,record.getString("directory")),data=new File(stage,"data");Set<String> expected=new HashSet<>();JSONArray files=manifest.getJSONArray("files");for(int i=0;i<files.length();i++){JSONObject entry=files.getJSONObject(i);String path=entry.getString("path");File file=new EncryptedBackupArchive.Source(path,new File(data,path)).file;if(!file.getCanonicalPath().startsWith(data.getCanonicalPath()+File.separator)||Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()!=entry.getLong("size")||!EncryptedBackupArchive.sha256(file).equals(entry.getString("sha256")))throw new IOException("RESTORE_STAGED_FILE_CHANGED");expected.add(path);}for(EncryptedBackupArchive.Source source:BackupFilePins.sources(data))if(!expected.contains(source.path))throw new IOException("RESTORE_UNEXPECTED_STAGED_FILE");LocalBackupDataset.inspectStaged(data,manifest);return manifest;
    }
    private void requireTargetContext(JSONObject record)throws Exception {JSONObject identity=new BackupDeviceGuard(context).current();if(!record.getString("installation_id").equals(identity.getString("installationId"))||!record.getString("expected_restore_epoch").equals(identity.getString("restoreEpoch")))throw new IOException("RESTORE_TARGET_CONTEXT_CHANGED");}
    private static void validateProof(JSONObject proof)throws Exception {if(proof==null||proof.optInt("formatVersion")!=1||!proof.optBoolean("verified")||proof.optString("backup_id").isEmpty()||proof.optLong("byte_size")<=0)throw new IOException("RESTORE_CURRENT_BACKUP_PROOF_REQUIRED");hash(proof.getString("archive_sha256"));hash(proof.getString("database_sha256"));Instant.parse(proof.getString("captured_at"));Instant.parse(proof.getString("local_verified_at"));}
    private JSONObject previewView(JSONObject record)throws Exception {JSONObject out=new JSONObject().put("restore_id",record.getString("restore_id")).put("phase",record.getString("phase")).put("current_state_unchanged",true).put("invoice_release","BLOCKED_UNTIL_CURRENT_EXTERNAL_RECONCILIATION");if(record.has("preview_hash")){out.put("preview_hash",record.getString("preview_hash"));JSONObject manifest=read(new File(work(record.getString("restore_id")),"manifest.json"));out.put("summary",new JSONObject().put("captured_at",manifest.getString("capturedAt")).put("app_version",manifest.getString("appVersion")).put("schema_version",manifest.getInt("schemaVersion")).put("total_bytes",manifest.getLong("totalBytes")).put("counts",manifest.getJSONObject("counts")).put("last_issued_document_count",manifest.getJSONArray("lastIssuedDocumentIds").length()).put("media_coverage",mediaCoverage(manifest)));}if(record.has("last_error_code"))out.put("last_error_code",record.getString("last_error_code"));return out;}
    private static JSONObject activationView(JSONObject state)throws Exception {String phase=state.optString("phase");return new JSONObject().put("restore_id",state.getString("restore_id")).put("phase",phase.equals("REQUESTED")?"RESTART_REQUIRED":phase).put("preview_hash",state.optString("preview_hash")).put("restart_required",PENDING.contains(phase)).put("current_state_unchanged",Arrays.asList("REQUESTED","CANCELLED","ROLLED_BACK").contains(phase)).put("invoice_release","BLOCKED_UNTIL_CURRENT_EXTERNAL_RECONCILIATION").put("last_error_code",state.optString("last_error_code")).put("media_coverage",state.optString("media_coverage","LEGACY_NOT_PROVEN")).put("legacy_media_coverage_confirmed",state.optBoolean("legacy_media_coverage_confirmed"));}
    private File activationFile(){return new File(root,"activation.json");}
    private File receipt(String request)throws Exception{return new File(root,"activation-requests/"+PixelMcpWriteTools.hash(request)+".json");}
    private File work(String id)throws Exception {if(id==null||!id.matches("restore_[a-f0-9]{36}"))throw new IOException("RESTORE_ID_INVALID");File directory=child(root,id);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("RESTORE_STAGING_UNAVAILABLE");return directory;}
    private static File child(File root,String name)throws Exception {if(name==null||!name.matches("[A-Za-z0-9_.-]{1,160}")||name.equals(".")||name.equals(".."))throw new IOException("RESTORE_PATH_INVALID");File file=new File(root,name);if(Files.isSymbolicLink(file.toPath())||!file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("RESTORE_PATH_INVALID");return file;}
    private static JSONObject readOptional(File file)throws Exception {return file.exists()||new File(file+".bak").exists()?read(file):null;}
    private static JSONObject read(File file)throws Exception {AtomicFile atomic=new AtomicFile(file);try(InputStream input=atomic.openRead();ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=input.read(b))!=-1){if(n>EncryptedBackupArchive.MAX_MANIFEST_BYTES-output.size())throw new IOException("RESTORE_METADATA_TOO_LARGE");output.write(b,0,n);}return new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));}}
    private static String readText(File file,int maximum)throws Exception {try(InputStream input=new FileInputStream(file);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=input.read(b))!=-1){if(n>maximum-output.size())throw new IOException("RESTORE_METADATA_TOO_LARGE");output.write(b,0,n);}return new String(output.toByteArray(),StandardCharsets.UTF_8);}}
    private static void write(File file,JSONObject value)throws Exception {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("RESTORE_STAGING_UNAVAILABLE");AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;try{output=atomic.startWrite();output.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(output);output=null;}catch(Exception failure){if(output!=null)atomic.failWrite(output);throw failure;}}
    private static String mediaCoverage(JSONObject manifest){return manifest.has("browser_media_inventory")&&manifest.optInt("browser_media_format_version")==1?"VERIFIED_NATIVE_MIRROR":"LEGACY_NOT_PROVEN";}
    private static void request(String value)throws IOException{if(value==null||!value.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("RESTORE_REQUEST_ID_REQUIRED");}
    private static void hash(String value)throws IOException{if(value==null||!value.matches("[0-9a-f]{64}"))throw new IOException("RESTORE_HASH_REQUIRED");}
    private static String hex(byte[] value){StringBuilder result=new StringBuilder();for(byte b:value)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private static void activationVersion(JSONObject state)throws IOException {if(state.optInt("formatVersion")!=1||!Arrays.asList("REQUESTED","ACTIVATING","SWAPPED","ROLLING_BACK","FAILED_HOLD","ACTIVE","ROLLED_BACK","CANCELLED").contains(state.optString("phase")))throw new IOException("RESTORE_ACTIVATION_SCHEMA_UNSUPPORTED");}
    private static ArrayList<String> keys(JSONObject value){ArrayList<String> result=new ArrayList<>();for(Iterator<String> keys=value.keys();keys.hasNext();)result.add(keys.next());return result;}
    private static String safeCode(Exception failure){String value=failure.getMessage();return value!=null&&value.matches("(?:RESTORE|BACKUP|RECOVERY)_[A-Z0-9_]{1,100}")?value:"RESTORE_OPERATION_FAILED";}
}
