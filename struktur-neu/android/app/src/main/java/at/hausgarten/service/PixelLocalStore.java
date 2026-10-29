package at.hausgarten.service;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Original SQLite store separated from Activity lifecycle; schema and files unchanged. */
final class PixelLocalStore extends SQLiteOpenHelper implements PixelMcpWriteTools.Sink {
    // Contract 7 fences old snapshot-only writers after a conscious table transition.
    static final int SCHEMA_VERSION=7;
    private final Context context;
    private final SharedPreferences preferences;
    private final PhotoAttachmentStore photoAttachments;
    private final WorkSessionStore.Clock clock;
    private static final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger> OPEN_STORES=new java.util.concurrent.ConcurrentHashMap<>();
    private boolean registeredOpen;
    PixelLocalStore(Context context){this(context,new WorkSessionStore.DeviceClock(context));}
    PixelLocalStore(Context context,WorkSessionStore.Clock clock) {
        super(context, "haus-garten-mobile.db", null, SCHEMA_VERSION);
        LocalAuthorityGuard.assertStoreMayOpen(context);
        // Configure the helper before legacy attachment handling can open the database.
        setWriteAheadLoggingEnabled(true);
        this.clock=clock;
        this.context = context;
        preferences = context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE);
        photoAttachments = new PhotoAttachmentStore(context);
        migrateLegacySnapshotPhotoAttachments();
    }
    @Override public synchronized SQLiteDatabase getWritableDatabase(){LocalAuthorityGuard.assertStoreMayOpen(context);return super.getWritableDatabase();}
    @Override public synchronized SQLiteDatabase getReadableDatabase(){LocalAuthorityGuard.assertStoreMayOpen(context);return super.getReadableDatabase();}
    @Override public void onOpen(SQLiteDatabase database){super.onOpen(database);try{LocalAuthorityGuard.verifyDatabaseContract(context,database);if(!registeredOpen){OPEN_STORES.computeIfAbsent(context.getDatabasePath("haus-garten-mobile.db").getCanonicalPath(),key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();registeredOpen=true;}}catch(Exception invalid){throw new IllegalStateException("LOCAL_DATABASE_CONTRACT_REVIEW_REQUIRED",invalid);}}
    @Override public synchronized void close(){super.close();if(registeredOpen){try{java.util.concurrent.atomic.AtomicInteger count=OPEN_STORES.get(context.getDatabasePath("haus-garten-mobile.db").getCanonicalPath());if(count!=null)count.decrementAndGet();}catch(IOException invalid){throw new IllegalStateException("LOCAL_STORE_PATH_INVALID",invalid);}finally{registeredOpen=false;}}}
    static void assertNoOpenStores(Context context)throws Exception {java.util.concurrent.atomic.AtomicInteger count=OPEN_STORES.get(context.getDatabasePath("haus-garten-mobile.db").getCanonicalPath());if(count!=null&&count.get()!=0)throw new IOException("RESTORE_DATABASE_STILL_OPEN");}
    Context appContext(){return context;}
    /** Explicit storage-transfer boundary; opening the app never invokes a transfer. */
    BusinessTableStore businessTables(){return businessTables(getWritableDatabase());}
    static BusinessTableStore businessTables(SQLiteDatabase database){return businessTables(database,false);}
    static BusinessTableStore businessTables(SQLiteDatabase database,boolean committing){
        return new BusinessTableStore(new BusinessTableStore.Sql(){
            public BusinessTableStore.Rows query(String statement,String...args){
                Cursor rows=database.rawQuery(statement,args);
                return new BusinessTableStore.Rows(){
                    public boolean next(){return rows.moveToNext();}
                    public String text(int column){return rows.getString(column);}
                    public long number(int column){return rows.getLong(column);}
                    public void close(){rows.close();}
                };
            }
            public void execute(String statement,Object...args){database.execSQL(statement,args);}
            public <T>T transaction(BusinessTableStore.Action<T> action)throws Exception{
                // Captured backup databases are immutable and opened read-only.
                if(database.isReadOnly()||committing)return action.run();
                database.beginTransaction();
                try{T result=action.run();database.setTransactionSuccessful();return result;}
                finally{database.endTransaction();}
            }
        });
    }
    /** Maintenance entry point: callers must deliberately select each phase and supply archive bytes.
     * There is no startup, upgrade, restore or remote-tool call to this method.
     */
    JSONObject transferBusinessTables(String operation,String request,String expectedHash,int batch,File archive,String recovery,File staging)throws Exception {
        BusinessTableStore tables=businessTables();
        if(operation.equals("status"))return tables.status();
        if(operation.equals("cancel"))return tables.cancel(request);
        if(archive==null||staging==null)throw new IOException("TABLE_BACKUP_EVIDENCE_REQUIRED");
        BusinessTableStore.Evidence evidence=LocalBackupDataset.tableEvidence(this,archive,recovery,staging);
        switch(operation){
            case "prepare":{
                SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
                try{
                    if(DatabaseBackupSnapshot.ownerValue(db,"snapshot",null)==null){
                        String legacy=preferences.getString("drive_snapshot","");if(legacy.isEmpty())throw new IOException("TABLE_PERSISTED_SNAPSHOT_REQUIRED");
                        if(!PixelMcpWriteTools.hash(new JSONObject(legacy)).equals(expectedHash))throw new IOException("TABLE_SOURCE_CHANGED");
                        evidence.verify(new JSONObject(legacy));putOwnerValue("snapshot",legacy);
                    }
                    JSONObject prepared=tables.prepare(request,expectedHash,evidence);db.setTransactionSuccessful();return prepared;
                }finally{db.endTransaction();}
            }
            case "copy":return tables.copyBatch(request,batch,evidence);
            case "activate":{SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{JSONObject activated=tables.activate(request,expectedHash,evidence);db.setTransactionSuccessful();return activated;}finally{db.endTransaction();}}
            case "rollback":return tables.rollback(request,expectedHash,evidence);
            default:throw new IOException("TABLE_TRANSFER_OPERATION_UNSUPPORTED");
        }
    }
    /** Only the outer write transaction projects journals, after temporary action IDs are replaced.
     * Android calls onCommit before COMMIT and rolls back if it throws. No nested BEGIN here.
     */
    void beginBusinessTransaction(SQLiteDatabase db){
        beginJournalTransaction(db,()->{
            BusinessTableStore tables=businessTables(db,true);
            if(tables.active()&&!tables.projectionCurrent())tables.commitJournal(LocalPendingActions.read(db,false,id->BackupSnapshotState.photo(db,id),0),master->{
                removeSnapshotPhotoBinaries(master);documentReferences(master);PixelDriveState.prepare(master);seedServiceCatalog(master);LocalTestCleanup.overlay((key,fallback)->DatabaseBackupSnapshot.ownerValue(db,key,fallback),master);
                if(LocalAuthorityGuard.local(context))master.put("processing_engine",LocalAuthorityRules.ENGINE).put("local_contract_version",LocalAuthorityRules.CONTRACT);
                OfferDecisionProjection.apply(db,master);LocalBillingProjection.apply(db,master);
            });return null;
        });
    }
    static void beginJournalTransaction(SQLiteDatabase db,BusinessTableStore.Action<Void> commit){
        if(db.inTransaction()){db.beginTransaction();return;}
        db.beginTransactionWithListener(new android.database.sqlite.SQLiteTransactionListener(){
            public void onBegin(){}
            public void onRollback(){}
            public void onCommit(){try{commit.run();}catch(Exception failure){throw new IllegalStateException("LOCAL_JOURNAL_TABLE_COMMIT_FAILED",failure);}}
        });
    }
    WorkSessionStore workSessions(){return new WorkSessionStore(getWritableDatabase(),clock);}
    int compactPurgedTestActions()throws Exception{return LocalTestCleanup.compact(this);}
    private String readAsset(String name) throws Exception {
        try (InputStream input = context.getAssets().open(name); BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line); return out.toString();
        }
    }
    private JSONObject emptyBootstrap()throws IOException {
        try{
            JSONObject value=new JSONObject(readAsset("initial-data.json"));Set<String> keys=new HashSet<>(java.util.Arrays.asList("format","schema_version","accepted_ids","sync_errors","capabilities","customers","orders","worklogs","invoices","photos","offers","appointments","service_catalog"));
            if(value.length()!=keys.size()||value.getInt("format")!=1||value.getInt("schema_version")!=2)throw new IllegalArgumentException();for(Iterator<String> names=value.keys();names.hasNext();)if(!keys.contains(names.next()))throw new IllegalArgumentException();
            for(String key:new String[]{"accepted_ids","sync_errors","customers","orders","worklogs","invoices","photos","offers","appointments","service_catalog"})if(value.getJSONArray(key).length()!=0)throw new IllegalArgumentException();
            JSONObject capabilities=value.getJSONObject("capabilities");String[] supported={"worklog_compare_and_save","entity_compare_and_save","customer_custom_fields_compare_and_save","billing_relations"};if(capabilities.length()!=supported.length)throw new IllegalArgumentException();for(String key:supported)if(!Boolean.TRUE.equals(capabilities.get(key)))throw new IllegalArgumentException();return value;
        }catch(Exception invalid){throw new IOException("LOCAL_BOOTSTRAP_INVALID");}
    }
    private String isoNow() { return java.time.Instant.now().toString(); }
    private void migrateLegacySnapshotPhotoAttachments(){
        try{
            String saved=preferences.getString("drive_snapshot","");if(saved.isEmpty())return;JSONObject snapshot=new JSONObject(saved);JSONArray photos=snapshot.optJSONArray("photos");boolean inline=false;
            for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null&&(photo.has("data")||photo.has("full_data"))){inline=true;break;}}
            if(!inline)return;recordSnapshotPhotoAttachments(snapshot);preferences.edit().putString("drive_snapshot",snapshot.toString()).commit();
        }catch(Exception ignored){/* Preserve the original snapshot; getState still withholds inline binary. */}
    }
    void documentReferences(JSONObject snapshot)throws Exception{
        JSONArray invoices=snapshot.optJSONArray("invoices");for(int i=0;invoices!=null&&i<invoices.length();i++){JSONObject invoice=invoices.getJSONObject(i);LegacyPdfAttachment.Prepared prepared=LegacyPdfAttachment.describe(invoice);if(prepared!=null){new BusinessDocumentStore(context).store(prepared.bytes,invoice);invoice.remove("pdf_base64");}}
    }
    static void removeSnapshotPhotoBinaries(JSONObject snapshot){
        JSONArray photos=snapshot.optJSONArray("photos");for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null){photo.remove("data");photo.remove("full_data");}}
    }
    @Override public void onCreate(SQLiteDatabase database) { database.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)"); WorkSessionStore.createSchema(database); createPhotoAttachmentSchema(database); createOwnerSchema(database); }
    @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) { if(oldVersion<2) WorkSessionStore.createSchema(database);if(oldVersion<3){createPhotoAttachmentSchema(database);migrateLegacyPendingPhotoAttachments(database);}if(oldVersion<4)createOwnerSchema(database); /* 5 -> 7 fences old writers; no snapshot copy or activation occurs here. */ }
    private void createOwnerSchema(SQLiteDatabase database){
        database.execSQL("CREATE TABLE IF NOT EXISTS owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        database.execSQL("CREATE TABLE IF NOT EXISTS mcp_uploads(id TEXT PRIMARY KEY,metadata TEXT NOT NULL,data BLOB NOT NULL)");
    }
    JSONObject rawSnapshot(String fallback)throws Exception {return businessTables().snapshot(preferences.getString("drive_snapshot",fallback));}
    String snapshotText(String fallback){
        String value=ownerValue("snapshot",null);return value==null?preferences.getString("drive_snapshot",fallback):value;
    }
    void putOwnerValue(String key,String value){
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);
        try{
            BusinessTableStore tables=businessTables(db);
            if(key.equals("snapshot")&&tables.active())tables.replaceSnapshot(new JSONObject(value));
            else{db.execSQL("INSERT OR REPLACE INTO owner_state(key,value) VALUES(?,?)",new Object[]{key,value});if(key.equals("purged_local_test_records_v1"))tables.invalidateJournal();}
            db.setTransactionSuccessful();
        }catch(Exception invalid){throw new IllegalStateException("LOCAL_STATE_WRITE_FAILED",invalid);}
        finally{db.endTransaction();}
    }
    String ownerValue(String key,String fallback){
        if(key.equals("snapshot"))try{BusinessTableStore tables=businessTables();if(tables.active())return tables.exportSnapshot().toString();}catch(Exception invalid){throw new IllegalStateException("LOCAL_TABLE_READ_FAILED",invalid);}
        int count;try(Cursor row=getReadableDatabase().rawQuery("SELECT length(value) FROM owner_state WHERE key=?",new String[]{key})){if(!row.moveToFirst())return fallback;count=row.getInt(0);}
        StringBuilder out=new StringBuilder();for(int offset=0;offset<count;offset+=32768)try(Cursor row=getReadableDatabase().rawQuery("SELECT substr(value,?,32768) FROM owner_state WHERE key=?",new String[]{Integer.toString(offset+1),key})){if(!row.moveToFirst())return fallback;out.append(row.getString(0));}return out.toString();
    }
    void seedServiceCatalog(JSONObject snapshot)throws Exception{ServiceCatalogBootstrap.seed(snapshot,()->readAsset("service-catalog.json"));}
    void commitSnapshot(JSONObject snapshot,Set<String> confirmed)throws Exception{
        if(!LocalAuthorityGuard.legacySyncAllowed(context))throw new IOException("LEGACY_SYNC_WRITER_DISABLED");
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{
            JSONArray remaining=new JSONArray(),actions=pendingActions();for(int i=0;i<actions.length();i++)if(!confirmed.contains(actions.getJSONObject(i).getString("client_id")))remaining.put(actions.getJSONObject(i));
            JSONObject future=SnapshotProjection.effective(new JSONObject().put("master",new JSONObject(snapshot.toString())).put("pending",remaining));new RevierApplicationService(new RevierSnapshotRepository(db,future)).validateRelationships();
            putOwnerValue("snapshot",snapshot.toString());acknowledge(confirmed);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void commitLocalSnapshot(JSONObject snapshot)throws Exception {
        if(!LocalAuthorityGuard.local(context))throw new IOException("LOCAL_AUTHORITY_REQUIRED");
        snapshot.put("processing_engine",LocalAuthorityRules.ENGINE).put("local_contract_version",LocalAuthorityRules.CONTRACT);
        putOwnerValue("snapshot",snapshot.toString());
    }
    private void createPhotoAttachmentSchema(SQLiteDatabase database){
        database.execSQL("CREATE TABLE IF NOT EXISTS photo_attachments(photo_id TEXT PRIMARY KEY,content_sha256 TEXT NOT NULL,mime_type TEXT NOT NULL,byte_size INTEGER NOT NULL,storage_name TEXT NOT NULL,role TEXT NOT NULL DEFAULT 'documentation',transfer_state TEXT NOT NULL DEFAULT 'local',created_at TEXT NOT NULL)");
        database.execSQL("CREATE INDEX IF NOT EXISTS ix_photo_attachments_hash ON photo_attachments(content_sha256)");
    }
    private void migrateLegacyPendingPhotoAttachments(SQLiteDatabase database){
        try(Cursor rows=database.rawQuery("SELECT payload FROM pending WHERE kind='photo' ORDER BY rowid",null)){
            while(rows.moveToNext())try{
                JSONObject payload=new JSONObject(rows.getString(0));if(payload.optString("data").isEmpty()||payload.optString("id").isEmpty())continue;
                PhotoAttachmentStore.Prepared prepared=photoAttachments.prepareLegacy(payload);saveAttachmentMetadata(database,payload.getString("id"),prepared.metadata,"legacy_inline");
            }catch(Exception ignored){/* Keep the immutable legacy action unchanged; the laptop can still validate it. */}
        }
    }
    JSONObject getState() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject result=getStateLocked();db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
    }
    PixelMcpReadTools.Source readSource(){
        return new PixelMcpReadTools.Source(){
            public JSONObject state(){return getState();}
            public JSONObject stateFor(String tool,JSONObject args)throws Exception{return queryState(tool,args);}
            public JSONObject metadata()throws Exception{return new JSONObject().put("last_drive_sync",preferences.getString("last_drive_sync",""));}
        };
    }
    /** Committed row projections serve point and list reads without replaying the journal.
     * A write in progress still reads its complete uncommitted context for domain validation.
     */
    private JSONObject queryState(String tool,JSONObject args)throws Exception {
        String collection;boolean point;
        switch(tool){
            case "get_customer":collection="customers";point=true;break;
            case "get_order":collection="orders";point=true;break;
            case "get_photo":collection="photos";point=true;break;
            case "search_customers":collection="customers";point=false;break;
            case "search_orders":case "get_open_orders":collection="orders";point=false;break;
            case "list_work_records":case "get_customer_work_records":collection="worklogs";point=false;break;
            case "list_invoices":collection="invoices";point=false;break;
            case "list_photos":collection="photos";point=false;break;
            case "search_business_data":collection="";point=false;break;
            default:return getState();
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            BusinessTableStore tables=businessTables(db);JSONObject result;
            if(!tables.projectionCurrent())result=getStateLocked();
            else{
                JSONObject master=tables.metadataSnapshot();for(String kind:BusinessTableStore.COLLECTIONS)master.put(kind,new JSONArray());
                if(point){
                    String key=tool.equals("get_customer")?"customer_id":tool.equals("get_order")?"order_id":"photo_id";
                    JSONObject row=tables.get(collection,args.getString(key));
                    if(row!=null){master.getJSONArray(collection).put(row);if(tool.equals("get_order")){addQueryCustomer(tables,master,row.optString("customer_id"));addQueryCustomer(tables,master,row.optString("billing_customer_id"));}if(tool.equals("get_customer"))addQueryCustomer(tables,master,row.optString("default_billing_customer_id"));}
                }else{
                    // Reference context retains the existing matching and missing-ID behavior.
                    JSONObject references=tables.referenceContext();master.put("customers",references.getJSONArray("customers")).put("orders",references.getJSONArray("orders"));
                    if(tool.equals("search_business_data")){
                        JSONObject filters=new JSONObject().put("query",args.getString("query")).put("include_inactive",true).put("limit",args.optInt("limit_per_type",10));
                        for(String kind:new String[]{"customer","order","worklog"}){String group=kind.equals("customer")?"customers":kind.equals("order")?"orders":"worklogs";master.put(group,tables.selected(group,kind,filters,references));}
                    }else{
                        JSONObject filters=new JSONObject(args.toString());if(tool.equals("get_open_orders"))filters.put("open_only",true).put("limit",args.optInt("limit",50));
                        String kind=collection.equals("customers")?"customer":collection.equals("orders")?"order":collection.equals("worklogs")?"worklog":collection.equals("invoices")?"invoice":"photo";
                        master.put(collection,tables.selected(collection,kind,filters,master));
                    }
                }
                removeSnapshotPhotoBinaries(master);
                result=readSource().metadata().put("master",master).put("pending",new JSONArray()).put("storage_point_read",true).put("storage_list_selected",!point);
            }
            db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    private void addQueryCustomer(BusinessTableStore tables,JSONObject master,String id)throws Exception {
        if(id.isEmpty()||findById(master.getJSONArray("customers"),id)!=null)return;
        JSONObject row=tables.get("customers",id);if(row==null)return;master.getJSONArray("customers").put(row);
        // The selected customer's default billing recipient can supply the displayed name.
        String billing=row.optString("default_billing_customer_id");if(!billing.isEmpty()&&!billing.equals(id)&&findById(master.getJSONArray("customers"),billing)==null){JSONObject recipient=tables.get("customers",billing);if(recipient!=null)master.getJSONArray("customers").put(recipient);}
    }
    private JSONObject getStateLocked() {
        JSONObject output = new JSONObject();
        try { JSONObject master=rawSnapshot(readAsset("initial-data.json"));removeSnapshotPhotoBinaries(master);documentReferences(master);PixelDriveState.prepare(master);seedServiceCatalog(master);LocalTestCleanup.overlay(this,master);if(LocalAuthorityGuard.local(context))master.put("processing_engine",LocalAuthorityRules.ENGINE).put("local_contract_version",LocalAuthorityRules.CONTRACT);output.put("master",master); output.put("pending", DriveJournalRules.union(pendingActions(),output.getJSONObject("master").optJSONArray("drive_review_actions"))); output.put("last_drive_sync", preferences.getString("last_drive_sync", ""));output.put("work_session",workSessions().current());output.put("sync_queue",DriveJournalRules.queueStatus(output.getJSONObject("master"),output.getJSONArray("pending"))); }
        catch (Exception error) { throw new IllegalStateException("LOCAL_STATE_BUILD_FAILED"); }
        try{output.put("drive_sync_failed",preferences.getBoolean("drive_sync_failed",false)).put("last_sync_attempt",preferences.getString("last_sync_attempt","")).put("cloud_confirmed",preferences.getBoolean("drive_cloud_confirmed",false)&&output.getJSONObject("sync_queue").getInt("total")==0).put("confirmation_scope",preferences.getString("drive_confirmation_scope","local_only")).put("last_full_sync",preferences.getString("last_full_sync",""));}catch(Exception ignored){}
        try{JSONObject old=output.getJSONObject("sync_queue");output.put("legacy_queue_inventory",old).put("sync_queue",new JSONObject().put("contract_version",DriveRolePolicy.contractVersion()).put("mode",DriveRolePolicy.role()).put("total",0).put("pending",0).put("review",old.optInt("review")).put("locally_committed",output.getJSONArray("pending").length())).put("storage_contract_version",DriveRolePolicy.contractVersion()).put("drive_role",DriveRolePolicy.role()).put("processing_engine",LocalAuthorityRules.ENGINE).put("inventory_processing_engine",output.getJSONObject("master").optString("processing_engine")).put("business_sync_allowed",false).put("cloud_confirmed",false).put("drive_sync_failed",false).put("confirmation_scope","local_only").put("local_authority",new LocalAuthorityGuard(context).current());}catch(Exception invalid){throw new IllegalStateException("LOCAL_STATE_CONTRACT_INVALID",invalid);}
        try{OfferDecisionProjection.apply(this,output.getJSONObject("master"));LocalBillingProjection.apply(this,output.getJSONObject("master"));output.put("sync_status",DriveSyncCoordinator.status(context,output.getJSONObject("sync_queue")));}catch(Exception invalid){throw new IllegalStateException("LOCAL_STATE_METADATA_UNREADABLE",invalid);}
        return output;
    }
    String saveWorklog(JSONObject payload) throws Exception {
        SQLiteDatabase transaction=getWritableDatabase();beginBusinessTransaction(transaction);
        try {String result=saveWorklogLocked(payload);transaction.setTransactionSuccessful();return result;}
        finally{transaction.endTransaction();}
    }
    private String saveWorklogLocked(JSONObject payload) throws Exception {
        JSONObject current=PixelMcpReadTools.effective(getState());
        payload=ManualChangeRules.editor(payload,findById(current.getJSONArray("worklogs"),payload.optString("id")),"worklog");
        String customerId = payload.optString("customer_id").trim();
        if (customerId.isEmpty() || payload.optString("activity").trim().isEmpty()) throw new IllegalArgumentException("Kunde und Tätigkeit sind erforderlich.");
        boolean isNew = payload.optString("id").isEmpty();
        boolean requireOrder = payload.optBoolean("_require_order", false) || payload.optBoolean("require_order", false);
        String orderId = payload.isNull("order_id") ? "" : payload.optString("order_id").trim();
        JSONObject master = current;
        JSONArray customers = master.getJSONArray("customers"), orders = master.getJSONArray("orders");
        JSONObject previous=findById(master.getJSONArray("worklogs"),payload.optString("id"));
        isNew=previous==null;
        if (!containsId(customers, customerId)) throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
        if (isNew && requireOrder && orderId.isEmpty()) throw new IllegalArgumentException("Bitte zuerst einen Auftrag auswählen oder anlegen.");
        if (!orderId.isEmpty()) {
            JSONObject order = findById(orders, orderId);
            if (order == null) throw new IllegalArgumentException("Der ausgewählte Auftrag existiert nicht.");
            if (!customerId.equals(order.optString("customer_id"))) throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
            if (payload.optString("location").trim().isEmpty()) payload.put("location", order.optString("location"));
        }
        BillingRules.work(payload,previous,new JSONObject().put("customers",customers).put("orders",orders));
        AppointmentRules.worklog(payload,previous,current);
        validateRevierContext(payload,false);
        WorklogRules.validate(payload, isNew);
        WorklogRelationRules.validate(payload,previous,master.getJSONArray("photos"),master.getJSONArray("invoices"));
        String entityId = payload.optString("id");
        if (entityId.isEmpty()) entityId = "wrk_mobile_" + UUID.randomUUID().toString().replace("-", "");
        payload.put("id", entityId);
        if(payload.optString("source").trim().isEmpty())payload.put("source",previous==null?"Pixel offline":previous.optString("source","Pixel offline"));
        prepareManualChange("worklog",payload,previous);
        String actionId = "worklog_change_" + UUID.randomUUID().toString().replace("-", "");
        SQLiteDatabase database = getWritableDatabase(); beginBusinessTransaction(database);
        try {
            // Retain successive offline edits as a guarded action chain, not a lossy replacement.
            ContentValues values = new ContentValues(); values.put("id", actionId); values.put("kind", "worklog"); values.put("payload", payload.toString()); values.put("created_at", isoNow());
            database.insertOrThrow("pending", null, values); database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        return entityId;
    }
    JSONObject startWorkSession(JSONObject payload)throws Exception{JSONObject request=new JSONObject(payload.toString());request.remove("_request_input_hash");String inputHash=PixelMcpWriteTools.hash(request);request.put("_request_input_hash",inputHash);SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{JSONObject result=startWorkSessionLocked(request);db.setTransactionSuccessful();return result;}finally{db.endTransaction();}}
    private JSONObject startWorkSessionLocked(JSONObject payload)throws Exception{
        JSONObject active=workSessions().current();if(!"IDLE".equals(active.optString("state")))return workSessions().start(payload);
        JSONObject master=PixelMcpReadTools.effective(getState());
        String customer=payload.optString("customer_id").trim(),order=payload.optString("order_id").trim();
        if(!customer.isEmpty()&&!containsId(master.getJSONArray("customers"),customer))throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
        if(!customer.isEmpty())BillingRules.work(payload,null,new JSONObject().put("customers",master.getJSONArray("customers")).put("orders",master.getJSONArray("orders")));
        if(!order.isEmpty()){
            JSONObject row=findById(master.getJSONArray("orders"),order);
            if(row==null||!customer.equals(row.optString("customer_id")))throw new IllegalArgumentException("Kunde und Auftrag passen nicht zusammen.");
        }
        if(!payload.optString("appointment_id").isEmpty()){JSONObject appointment=PixelMcpWriteTools.find(PixelMcpReadTools.effective(getState()).getJSONArray("appointments"),payload.getString("appointment_id"));if(!AppointmentRules.active(appointment)||appointment.optString("status").equals("draft")||!customer.equals(appointment.optString("customer_id"))||!order.equals(appointment.optString("order_id")))throw new IllegalArgumentException("Termin, Kunde und Auftrag passen nicht zu einer aktiven Arbeit.");}
        validateRevierContext(payload,true);
        return workSessions().start(payload);
    }
    String completeWorkSession(String sessionId,JSONObject payload,String requestId)throws Exception{
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try{
        WorkSessionStore sessions=workSessions();JSONObject session=sessions.current(sessionId);
        if(!WorkSessionStore.STOPPED.equals(session.optString("state"))&&!WorkSessionStore.FINALIZED.equals(session.optString("state")))throw new IllegalStateException("Bitte die laufende Arbeit zuerst stoppen.");
        String stableId="wrk_session_"+sessionId.replace("ses_pixel_","");
        if(WorkSessionStore.FINALIZED.equals(session.optString("state"))){if(stableId.equals(session.optString("finalized_worklog_id")))return stableId;throw new IllegalStateException("Diese Arbeitssitzung ist bereits abgeschlossen.");}
        payload.remove("_require_order");payload.remove("require_order");payload.put("id",stableId).put("work_date",session.getString("work_date")).put("hours",session.getDouble("hours"));
        if(session.optBoolean("review_required")||session.optBoolean("pause_unverified"))throw new IllegalArgumentException("Pausenzeiten wegen widersprüchlicher Geräteuhr bitte prüfen.");
        payload.put("break_minutes",session.optInt("break_minutes",0));payload.remove("start_time");payload.remove("end_time");
        java.time.format.DateTimeFormatter timerStamp=java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Vienna"));String timing="Timer-Nachweis (Europe/Vienna): "+timerStamp.format(java.time.Instant.ofEpochMilli(session.getLong("started_at_ms")))+" bis "+timerStamp.format(java.time.Instant.parse(session.getString("updated_at")))+"; "+session.getDouble("hours")+" h netto, "+session.optInt("break_minutes",0)+" min Pause.";if("manual_review".equals(session.optString("time_origin")))timing="Geprüfte Timerzeit: "+session.getDouble("hours")+" h netto, "+session.optInt("break_minutes",0)+" min Pause. Keine Endzeit rekonstruiert. Grund: "+session.optString("correction_reason");if(!payload.optString("notes").contains(timing))payload.put("notes",(payload.optString("notes").isEmpty()?"":payload.getString("notes")+"\n")+timing);
        if(payload.optString("customer_id").trim().isEmpty())payload.put("customer_id",session.optString("customer_id"));
        if(!payload.has("order_id"))payload.put("order_id",session.optString("order_id"));
        if(!session.optString("order_id").isEmpty()&&!session.optString("order_id").equals(payload.optString("order_id")))throw new IllegalArgumentException("Der Auftrag der gestarteten Sitzung bleibt erhalten.");
        if(!session.optString("customer_id").isEmpty()&&!session.optString("customer_id").equals(payload.optString("customer_id")))throw new IllegalArgumentException("Die Sitzung gehört zu einem anderen Einsatzkunden.");
        if(!payload.has("billing_customer_id")&&!session.optString("billing_customer_id").isEmpty())payload.put("billing_customer_id",session.getString("billing_customer_id"));
        if(payload.optString("location").trim().isEmpty()&&payload.optString("customer_id").equals(session.optString("customer_id")))payload.put("location",session.optString("location"));
        if(!session.optString("appointment_id").isEmpty())payload.put("appointment_id",session.getString("appointment_id"));
        retainSessionContext(payload,session);
        String saved=saveWorklog(payload);
        JSONObject photos=PixelMcpReadTools.effective(getState());JSONArray sessionPhotos=photos.getJSONArray("photos");
        for(int i=0;i<sessionPhotos.length();i++){JSONObject old=sessionPhotos.getJSONObject(i);if(!sessionId.equals(old.optString("session_id"))||!old.optString("worklog_id").isEmpty())continue;
            JSONObject linked=DriveJournalRules.clean(old);linked.put("worklog_id",saved);prepareManualChange("photo",linked,old);saveAction("photo","pho_mobile_",linked);}
        sessions.finalizeSession(sessionId,saved,requestId);database.setTransactionSuccessful();return saved;
        }finally{database.endTransaction();}
    }
    private void validateRevierContext(JSONObject payload,boolean starting)throws Exception {
        String property=payload.optString("property_id"),visit=payload.optString("visit_id");
        if(!visit.isEmpty()){
            String raw=ownerValue("revier:v1:entity:visit:"+visit,"");if(raw.isEmpty())throw new IllegalArgumentException("Der ausgewählte Besuch existiert nicht.");JSONObject row=new JSONObject(raw);RevierRules.version(row);
            if(!property.equals(row.getString("property_id"))||!payload.optString("customer_id").equals(row.getString("customer_id"))||!payload.optString("order_id").equals(row.optString("order_id")))throw new IllegalArgumentException("Besuch, Objekt, Kunde und Auftrag passen nicht zusammen.");
            if(starting&&!RevierRules.activeVisit(row))throw new IllegalArgumentException("Dieser Besuch ist abgesagt oder abgeschlossen. Zuerst den Besuch prüfen.");
        }
        if(!property.isEmpty()){
            String raw=ownerValue("revier:v1:entity:property:"+property,"");if(raw.isEmpty())throw new IllegalArgumentException("Das ausgewählte Objekt existiert nicht.");JSONObject row=new JSONObject(raw);RevierRules.version(row);
            if(!payload.optString("customer_id").equals(row.getString("customer_id"))||starting&&!row.optBoolean("active",true))throw new IllegalArgumentException("Objekt und Einsatzkunde passen nicht zu einer neuen Arbeit.");
        }
    }
    private static void retainSessionContext(JSONObject payload,JSONObject session)throws Exception {
        for(String field:new String[]{"property_id","visit_id"}){
            String value=session.optString(field);if(payload.has(field)&&!payload.optString(field).equals(value))throw new IllegalArgumentException("Der Objekt-/Besuchsbezug der gestarteten Sitzung bleibt erhalten.");
            if(!value.isEmpty())payload.put(field,value);
        }
    }
    // Business action and replay receipt commit together in the existing database.
    // Receipts are internal metadata, never exported as Drive actions.
    @Override public JSONObject execute(String tool, JSONObject args) throws Exception {
        if(AppointmentTools.write(tool)||OfferTools.write(tool)||PixelMcpOwnerTools.write(tool))return new PixelOwnerAccess(context,this).execute(tool,args);
        if(PixelMcpSessionTools.handles(tool))return executeSession(tool,args);
        if(PixelMcpWriteTools.handles(tool))PixelMcpWriteTools.validate(args, PixelMcpWriteTools.schema(tool));
        else if(PixelMcpEntityWriteTools.handles(tool))PixelMcpEntityWriteTools.validate(tool,args);
        else throw new PixelMcpWriteTools.Rejected("UNKNOWN_TOOL","Diese Schreibfunktion ist nicht freigegeben.");
        String clientId = "mcp_pixel_" + PixelMcpWriteTools.hash(args.getString("request_id")).substring(0,40);
        String fingerprint = PixelMcpWriteTools.hash(new JSONObject().put("tool",tool).put("args",args));
        SQLiteDatabase database = getWritableDatabase(); beginBusinessTransaction(database);
        JSONObject result;
        try {
            JSONObject receipt = receipt(clientId);
            if (receipt != null) {
                if (!fingerprint.equals(receipt.optString("fingerprint"))) throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Diese Anfragekennung wurde bereits für andere Angaben verwendet. Bitte die ursprüngliche Anfrage prüfen.");
                result = new JSONObject().put("entity_type",receipt.optString("entity_type","worklog")).put("entity_id",receipt.optString("entity_id",receipt.optString("work_record_id"))).put("replayed",true).put("sync",status(clientId,getState()));
            } else {
                // Only an absent snapshot may use the existing, strictly empty
                // packaged bootstrap. Corrupt nonempty business state never falls back.
                if(snapshotText("").isEmpty())putOwnerValue("snapshot",emptyBootstrap().toString());
                JSONObject prepared=PixelMcpWriteTools.handles(tool)?new JSONObject().put("entity_type","worklog").put("payload",PixelMcpWriteTools.prepare(tool,args,getState(),clientId)):PixelMcpEntityWriteTools.prepare(tool,args,getState(),clientId);
                String entityType=prepared.getString("entity_type");JSONObject payload=prepared.getJSONObject("payload");
                String source = payload.optString("source","ChatGPT Sprache");
                String changedAt=isoNow();payload.put("updated_at",changedAt);if(!payload.has("created_at"))payload.put("created_at",changedAt);
                Set<String> existingActions=new HashSet<>();JSONArray priorActions=pendingActions();for(int i=0;i<priorActions.length();i++)existingActions.add(priorActions.getJSONObject(i).getString("client_id"));
                String entityId;
                switch(entityType){
                    case "customer":entityId=saveCustomer(payload);break;
                    case "order":entityId=saveOrder(payload);break;
                    case "invoice":entityId=saveInvoiceDraft(payload);break;
                    default:entityId=saveWorklog(payload);
                }
                payload.put("source",source);if(testLocalOnly(entityType,payload))payload.put("_local_only",true);
                // Replace only the queue action just allocated by the shared saver, inside this transaction.
                try (Cursor rows = database.rawQuery("SELECT id,payload FROM pending WHERE kind=?",new String[]{entityType})) {
                    while(rows.moveToNext()) if(!existingActions.contains(rows.getString(0))&&entityId.equals(new JSONObject(rows.getString(1)).optString("id"))) database.delete("pending","id=?",new String[]{rows.getString(0)});
                }
                ContentValues action = new ContentValues(); action.put("id",clientId); action.put("kind",entityType); action.put("payload",payload.toString()); action.put("created_at",isoNow());database.insertOrThrow("pending",null,action);
                JSONObject metadata = new JSONObject().put("fingerprint",fingerprint).put("tool",tool).put("entity_type",entityType).put("entity_id",entityId).put("work_record_id",entityType.equals("worklog")?entityId:"").put("uploaded",false).put("test_local_only",payload.optBoolean("_local_only"));
                ContentValues saved = new ContentValues();saved.put("id","receipt_"+clientId);saved.put("kind","mcp_receipt");saved.put("payload",metadata.toString());saved.put("created_at",isoNow());database.insertOrThrow("pending",null,saved);
                result = new JSONObject().put("entity_type",entityType).put("entity_id",entityId).put("replayed",false).put("sync",status(clientId,getState()));
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        if (context instanceof McpSyncHost) ((McpSyncHost)context).requestMcpSync();
        if("worklog".equals(result.optString("entity_type")))result.put("work_record_id",result.getString("entity_id"));
        result.put("sync",PixelMcpBusinessTools.sync(result.getJSONObject("sync"),getState())).put("local_state","local_saved").put("sync_state",result.getJSONObject("sync").optString("sync_state"));
        return new JSONObject().put("ok",true).put("data",result).put("message","Auf dem Pixel gespeichert. Drive dient ausschließlich der getrennt geprüften Vollsicherung.");
    }
    private JSONObject receipt(String clientId) throws Exception {
        try(Cursor row=getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE id=? AND kind='mcp_receipt'",new String[]{"receipt_"+clientId})) { return row.moveToFirst()?new JSONObject(row.getString(0)):null; }
    }
    private JSONObject executeSession(String tool,JSONObject args)throws Exception{
        PixelMcpWriteTools.validate(args,PixelMcpSessionTools.schema(tool));
        String clientId="mcp_pixel_"+PixelMcpWriteTools.hash(args.getString("request_id")).substring(0,40),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("tool",tool).put("args",args));
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);JSONObject result;
        try{
            JSONObject existing=receipt(clientId);if(existing!=null){if(!fingerprint.equals(existing.optString("fingerprint")))throw new PixelMcpWriteTools.Rejected("REPLAY_MISMATCH","Anfragekennung wurde für andere Angaben verwendet.");result=new JSONObject(existing.getJSONObject("result").toString()).put("replayed",true);}
            else{
                WorkSessionStore timer=workSessions();String id=args.optString("session_id"),request="mcp-session-"+clientId;
                if(tool.equals("finish_work")){JSONObject current=timer.current(id);if(java.util.Arrays.asList("RUNNING","PAUSED").contains(current.optString("state")))timer.stop(id,request+"-stop");}
                JSONObject payload=PixelMcpSessionTools.prepare(tool,args,getState()),session;
                switch(tool){case "review_work_session":payload.put("request_id",request);session=timer.reviewTime(id,payload);break;case "start_work":payload.put("request_id",request);session=startWorkSession(payload);break;case "start_break":session=timer.pause(id,request);break;case "end_break":session=timer.resume(id,request);break;case "stop_work":session=timer.stop(id,request);break;case "continue_work":session=timer.continueStopped(id,request);break;case "discard_work_session":session=timer.discard(id,request);break;case "restore_work_session":session=timer.restore(id,request);break;default:
                    payload.remove("request_id");payload.remove("session_id");String saved=completeWorkSession(id,payload,request);payload.put("id",saved).put("_mcp_create",true);if(testLocalOnly("worklog",payload))payload.put("_local_only",true);
                    try(Cursor rows=database.rawQuery("SELECT id,payload FROM pending WHERE kind='worklog'",null)){while(rows.moveToNext())if(saved.equals(new JSONObject(rows.getString(1)).optString("id")))database.delete("pending","id=?",new String[]{rows.getString(0)});}
                    ContentValues action=new ContentValues();action.put("id",clientId);action.put("kind","worklog");action.put("payload",payload.toString());action.put("created_at",isoNow());database.insertOrThrow("pending",null,action);session=timer.current(id);
                }
                result=new JSONObject().put("client_id",clientId).put("session",session).put("replayed",false).put("local_state","local_saved").put("sync_state",(tool.equals("complete_work_session")||tool.equals("finish_work"))?"sync_pending":"not_applicable").put("requires_work_record_completion",tool.equals("stop_work"));
                JSONObject meta=new JSONObject().put("fingerprint",fingerprint).put("tool",tool).put("entity_type",(tool.equals("complete_work_session")||tool.equals("finish_work"))?"worklog":"work_session").put("entity_id",(tool.equals("complete_work_session")||tool.equals("finish_work"))?session.optString("finalized_worklog_id"):session.getString("id")).put("work_record_id",session.optString("finalized_worklog_id")).put("uploaded",false).put("test_local_only",payload.optBoolean("_local_only")).put("result",result);
                ContentValues row=new ContentValues();row.put("id","receipt_"+clientId);row.put("kind","mcp_receipt");row.put("payload",meta.toString());row.put("created_at",isoNow());database.insertOrThrow("pending",null,row);
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
        if((tool.equals("complete_work_session")||tool.equals("finish_work"))){if(context instanceof McpSyncHost)((McpSyncHost)context).requestMcpSync();JSONObject sync=status(clientId,getState());result.put("sync",sync).put("sync_state",sync.optString("sync_state"));}
        return new JSONObject().put("ok",true).put("data",result).put("message",tool.equals("stop_work")?"Zeiterfassung lokal gestoppt. Tätigkeit prüfen und mit complete_work_session als Arbeitsaufzeichnung abschließen.":"Aktion dauerhaft auf dem Pixel gespeichert. Timer und Arbeitsaufzeichnungen bleiben lokal; Sicherungen werden als unverschlüsselte ZIP auf dem Pixel gespeichert.");
    }
    @Override public JSONObject status(String clientId,JSONObject state) throws Exception {
        JSONObject master=state.getJSONObject("master"),metadata=receipt(clientId);String ownerReceipt=ownerValue("receipt:"+clientId,"");boolean ownerSaved=!ownerReceipt.isEmpty(),providerReadback=DriveJournalRules.ids(master.optJSONArray("drive_delivered_ids")).contains(clientId)||(metadata!=null&&metadata.optBoolean("uploaded"));JSONObject ownerResult=ownerSaved?new JSONObject(ownerReceipt).optJSONObject("result"):null;
        if(metadata!=null&&"work_session".equals(metadata.optString("entity_type")))return new JSONObject().put("client_id",clientId).put("status","local_saved").put("sync_state","not_applicable").put("local_state","local_saved").put("saved_locally",true).put("confirmed_by_app",false).put("uploaded_to_drive",false).put("entity_id",metadata.optString("entity_id")).put("message","Timeraktion dauerhaft lokal gespeichert; keine Drive-Arbeitsaufzeichnung. Aktuellen Zustand mit get_active_work_session lesen.");
        String phase="unknown";JSONArray accepted=master.optJSONArray("accepted_ids");
        for(int i=0;accepted!=null&&i<accepted.length();i++)if(clientId.equals(accepted.optString(i)))phase="accepted";
        if(!phase.equals("accepted")) {
            JSONArray pending=state.optJSONArray("pending");boolean queued=false;
            for(int i=0;pending!=null&&i<pending.length();i++)if(clientId.equals(pending.getJSONObject(i).optString("client_id")))queued=true;
            if(queued)phase=metadata!=null&&metadata.optBoolean("uploaded")?"transferred":"queued";
            else if(metadata!=null)phase="superseded";
            if(DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId))phase=providerReadback?"transferred":"applied_on_pixel";
            JSONArray errors=master.optJSONArray("sync_errors");for(int i=0;errors!=null&&i<errors.length();i++){JSONObject error=errors.optJSONObject(i);if(error!=null&&clientId.equals(error.optString("client_id")))phase="failed";}
            JSONArray journalErrors=master.optJSONArray("drive_journal_errors");for(int i=0;journalErrors!=null&&i<journalErrors.length();i++){JSONObject error=journalErrors.optJSONObject(i);if(error!=null&&clientId.equals(error.optString("client_id")))phase="failed";}
        }
        if(master.optJSONObject("drive_resolved_actions")!=null&&master.getJSONObject("drive_resolved_actions").has(clientId))phase="resolved";
        return PixelMcpBusinessTools.sync(new JSONObject().put("client_id",clientId).put("status",phase).put("confirmed_by_app",phase.equals("accepted"))
            .put("test_local_only",metadata!=null&&metadata.optBoolean("test_local_only")||ownerResult!=null&&ownerResult.optBoolean("test_local_only"))
            .put("saved_locally",metadata!=null||ownerSaved).put("uploaded_to_drive",phase.equals("accepted")||providerReadback||(metadata!=null&&metadata.optBoolean("uploaded"))).put("provider_readback",providerReadback||(metadata!=null&&metadata.optBoolean("uploaded")))
            .put("entity_type",metadata==null?(ownerResult==null?"":ownerResult.optString("entity_type")):metadata.optString("entity_type","worklog"))
            .put("entity_id",metadata==null?(ownerResult==null?"":ownerResult.optString("entity_id")):metadata.optString("entity_id",metadata.optString("work_record_id")))
            .put("work_record_id",metadata==null?"":metadata.optString("work_record_id"))
            .put("applied_on_pixel",DriveJournalRules.ids(master.optJSONArray("drive_applied_ids")).contains(clientId))
            .put("confirmation_scope",phase.equals("accepted")?"web_import_receipt":(providerReadback||metadata!=null&&metadata.optBoolean("uploaded"))?"drive_provider_readback":"local_only")
            .put("cloud_confirmed",false)
            .put("message",phase.equals("resolved")?"Die Konfliktentscheidung wurde übernommen. Die ursprüngliche Aktion bleibt im Journal erhalten.":phase.equals("accepted")?"Im Gesamtstand übernommen und bestätigt. Cloud-Upload wird durch Drive durchgeführt.":phase.equals("failed")?"Die Änderung benötigt eine Prüfung. Lokale Daten bleiben erhalten.":phase.equals("superseded")?"Die vorgemerkte Änderung wurde in der App ersetzt. Bitte den Eintrag öffnen.":phase.equals("transferred")?"Änderung an die Drive-App übergeben und zurückgelesen; auf dem Pixel nutzbar. Der Google-Cloud-Upload ist nicht unabhängig bestätigt.":phase.equals("applied_on_pixel")?"Auf dem Pixel übernommen; Provider-Rücklesen noch nicht nachgewiesen.":phase.equals("queued")?"Auf dem Pixel gesichert. Drive-Abgleich steht noch aus.":"Für diese Anfrage liegt noch keine Bestätigung vor."),state);
    }
    void markUploaded(JSONArray actions) throws Exception {
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try {for(int i=0;i<actions.length();i++){String clientId=actions.getJSONObject(i).optString("client_id");JSONObject metadata=receipt(clientId);if(metadata!=null){metadata.put("uploaded",true);ContentValues value=new ContentValues();value.put("payload",metadata.toString());database.update("pending",value,"id=? AND kind='mcp_receipt'",new String[]{"receipt_"+clientId});}}database.setTransactionSuccessful();}finally{database.endTransaction();}
    }
    private JSONObject findById(JSONArray rows, String id) throws Exception {
        if (rows == null) return null;
        for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && id.equals(row.optString("id"))) return row; }
        return null;
    }
    private boolean containsId(JSONArray rows, String id) throws Exception { return findById(rows, id) != null; }

    String saveEditorRecord(String kind,JSONObject input)throws Exception {
        JSONObject payload=new JSONObject(input.toString());String request=payload.optString("_request_id");payload.remove("_request_id");if(kind.equals("worklog")&&payload.optString("_session_id").isEmpty())payload.remove("_session_id");
        if(!request.isEmpty()&&!request.matches("[A-Za-z0-9_-]{8,200}"))throw new IllegalArgumentException("Ungültige Aktionskennung.");
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{
            String key="editor_commit:"+PixelMcpWriteTools.hash(request),fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("kind",kind).put("payload",payload));
            if(!request.isEmpty()){String raw=ownerValue(key,"");if(!raw.isEmpty()){JSONObject receipt=new JSONObject(raw);if(!fingerprint.equals(receipt.getString("fingerprint")))throw new IllegalArgumentException("REQUEST_ID_REUSED: Ursprüngliche Speicherung unverändert prüfen.");db.setTransactionSuccessful();return receipt.getString("id");}}
            String id;switch(kind){case "customer":id=saveCustomer(payload);break;case "order":id=saveOrder(payload);break;case "worklog":{JSONObject work=new JSONObject(payload.toString());String session=work.optString("_session_id");work.remove("_session_id");if(!session.isEmpty()&&!request.isEmpty()&&WorkSessionStore.FINALIZED.equals(workSessions().current(session).optString("state")))throw new IllegalArgumentException("Diese Arbeitssitzung ist bereits abgeschlossen. Gespeicherte Arbeit öffnen; eine andere Bearbeitung wurde nicht angewendet.");id=session.isEmpty()?saveWorklog(work):completeWorkSession(session,work,"ui-finalize-"+session);break;}case "invoice":id=saveInvoiceDraft(payload);break;default:throw new IllegalArgumentException("Unbekannter Editor.");}
            if(!request.isEmpty())putOwnerValue(key,new JSONObject().put("formatVersion",1).put("fingerprint",fingerprint).put("id",id).toString());db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    String saveCustomer(JSONObject payload) throws Exception {
        if (payload.optString("name").trim().isEmpty()) throw new IllegalArgumentException("Der Kundenname fehlt.");
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try {
        JSONObject master=PixelMcpReadTools.effective(getState());
        JSONArray customers=master.getJSONArray("customers");
        payload=ManualChangeRules.editor(payload,findById(customers,payload.optString("id")),"customer");
        TestDataRules.customerChange(payload,findById(customers,payload.optString("id")));
        GardenRules.normalize(payload);
        BillingRules.customerConfig(payload,customers);
        if(findById(customers,payload.optString("id"))==null)EntityProtectionRules.validateNewCustomer(payload,customers);
        String entityId = payload.optString("id");
        if (entityId.isEmpty()) entityId = "cus_mobile_" + UUID.randomUUID().toString().replace("-", "");
        payload.put("id", entityId);
        prepareManualChange("customer",payload,findById(customers,entityId));
        if(TestDataRules.customer(payload))payload.put("_local_only",true);
        String actionId = "customer_change_" + UUID.randomUUID().toString().replace("-", "");
        ContentValues values = new ContentValues(); values.put("id", actionId); values.put("kind", "customer"); values.put("payload", payload.toString()); values.put("created_at", isoNow());
        database.insertOrThrow("pending", null, values);database.setTransactionSuccessful();
        return entityId;
        } finally { database.endTransaction(); }
    }
    String saveReviewedOrder(JSONObject request)throws Exception {
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try{JSONObject payload=OrderReviewRules.prepare(getState(),request);String id=saveAction("order","ord_mobile_",payload);database.setTransactionSuccessful();return id;}
        finally{database.endTransaction();}
    }
    String saveOrder(JSONObject payload) throws Exception {
        SQLiteDatabase tx=getWritableDatabase();beginBusinessTransaction(tx);try{String result=saveOrderLocked(payload);tx.setTransactionSuccessful();return result;}finally{tx.endTransaction();}
    }
    private String saveOrderLocked(JSONObject payload) throws Exception {
        JSONObject current=PixelMcpReadTools.effective(getState());payload=ManualChangeRules.editor(payload,findById(current.getJSONArray("orders"),payload.optString("id")),"order");
        BillingRules.order(payload,current);
        if (payload.optString("customer_id").isEmpty() || payload.optString("title").trim().isEmpty()) throw new IllegalArgumentException("Kunde und Auftragsbezeichnung sind erforderlich.");
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try {
        JSONObject master=PixelMcpReadTools.effective(getState());
        if (!containsId(master.getJSONArray("customers"), payload.optString("customer_id"))) throw new IllegalArgumentException("Der ausgewählte Kunde existiert nicht.");
        if (!payload.optString("scheduled_date").isEmpty() && !payload.isNull("scheduled_date")) {
            try { java.time.LocalDate.parse(payload.optString("scheduled_date")); } catch (Exception error) { throw new IllegalArgumentException("Bitte ein gültiges Auftragsdatum auswählen."); }
        }
        String entityId = payload.optString("id");
        EntityProtectionRules.validateOrderChange(payload,findById(master.getJSONArray("orders"),entityId),master.getJSONArray("worklogs"),master.getJSONArray("invoices"),master.getJSONArray("photos"));
        if (entityId.isEmpty()) entityId = "ord_mobile_" + UUID.randomUUID().toString().replace("-", "");
        payload.put("id", entityId); payload.put("source", "Pixel offline");
        prepareManualChange("order",payload,findById(master.getJSONArray("orders"),entityId));
        String saved=saveAction("order", "ord_mobile_", payload);database.setTransactionSuccessful();return saved;
        } finally { database.endTransaction(); }
    }
    String saveInvoiceDraft(JSONObject payload) throws Exception {
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);
        try{JSONObject all=PixelMcpReadTools.effective(getState());String id=payload.optString("id");if(id.isEmpty())id="inv_mobile_"+UUID.randomUUID().toString().replace("-","");payload.put("id",id);
            JSONObject old=findById(all.getJSONArray("invoices"),id);payload=ManualChangeRules.editor(payload,old,"invoice");
            for(int i=0;i<getState().getJSONArray("pending").length();i++){JSONObject a=getState().getJSONArray("pending").getJSONObject(i);if(java.util.Arrays.asList("invoice_issue","owner_invoice_issue","pixel_invoice_issue_v1").contains(a.optString("kind"))&&id.equals(a.getJSONObject("payload").optString("id")))throw new IllegalArgumentException("Rechnungsabschluss ist bereits vorgemerkt.");}
            if(LocalAuthorityGuard.local(context)){
                JSONArray positions=payload.optJSONArray("positions");for(int i=0;positions!=null&&i<positions.length();i++){
                    JSONObject position=positions.getJSONObject(i);Object quantity=position.opt("quantity");if(quantity instanceof String){
                        String text=(String)quantity;if(!text.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,8})?"))throw new IllegalArgumentException("Positionsmenge als positive Dezimalzahl mit höchstens acht Nachkommastellen angeben.");
                        java.math.BigDecimal decimal=new java.math.BigDecimal(text);if(decimal.signum()<=0||decimal.precision()>12)throw new IllegalArgumentException("Positionsmenge benötigt höchstens zwölf Ziffern und muss positiv sein.");position.put("quantity",decimal);
                    }
                }
            }
            InvoiceDraftRules.normalize(payload,old,all);if(payload.optString("invoice_number").isEmpty())payload.put("invoice_number","Entwurf-"+id);
            prepareManualChange("invoice",payload,old);String result=saveAction("invoice","inv_mobile_",payload);db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    static String photoMetadataHash(JSONObject original)throws Exception{JSONObject metadata=new JSONObject(original.toString());metadata.remove("data");metadata.remove("thumbnail_data");metadata.remove("_capture_context");return PixelMcpWriteTools.hash(metadata);}
    synchronized String savePhoto(JSONObject payload) throws Exception {
        JSONObject origin=payload.optJSONObject("_capture_context");payload=new JSONObject(payload.toString());payload.remove("_capture_context");
        if(payload.has("_photo_quality")&&!java.util.Arrays.asList("original","high","optimized").contains(payload.getString("_photo_quality")))throw new IllegalArgumentException("Ungültiges Foto-Qualitätsprofil. Die Auswahl bleibt als Entwurf erhalten.");
        if (payload.optString("customer_id").isEmpty() || payload.optString("data").isEmpty()) throw new IllegalArgumentException("Kunde und Foto sind erforderlich.");
        if(!payload.optString("capture_time").isEmpty())java.time.Instant.parse(payload.getString("capture_time"));
        JSONObject immutableRequest=new JSONObject(payload.toString());immutableRequest.remove("thumbnail_data");String request=payload.optString("_photo_request_id"),fingerprint=PixelMcpWriteTools.hash(immutableRequest);
        if(!request.isEmpty()&&!request.matches("[A-Za-z0-9_-]{8,128}"))throw new IllegalArgumentException("Ungültige Foto-Anfragekennung.");
        if(!request.isEmpty()){String saved=ownerValue("photo-ui:"+request,"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);if(!fingerprint.equals(receipt.getString("hash")))throw new IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.");return receipt.getString("id");}}
        if(origin!=null){JSONObject identity=new BackupDeviceGuard(context).current();if(origin.optInt("formatVersion")!=1||origin.optBoolean("unknown_restore")||!identity.getString("installationId").equals(origin.optString("installation_id"))||!identity.getString("restoreEpoch").equals(origin.optString("restore_epoch")))throw new IOException("PHOTO_RESTORE_ACTION_REVIEW_REQUIRED");}
        String entityId=payload.optString("id");if(entityId.isEmpty())entityId="pho_mobile_"+UUID.randomUUID().toString().replace("-","");payload.put("id",entityId);
        PhotoAttachmentStore.Prepared prepared=null;boolean committed=false;
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try {
            if(!request.isEmpty()){String saved=ownerValue("photo-ui:"+request,"");if(!saved.isEmpty()){JSONObject receipt=new JSONObject(saved);if(!fingerprint.equals(receipt.getString("hash")))throw new IllegalArgumentException("Die Foto-Anfragekennung enthält andere Angaben.");database.setTransactionSuccessful();committed=true;return receipt.getString("id");}}
            JSONObject master=PixelMcpReadTools.effective(getState());
            if(!payload.optString("session_id").isEmpty()){
                JSONObject session=workSessions().current(payload.getString("session_id"));
                if(!payload.getString("customer_id").equals(session.optString("customer_id"))||!payload.optString("order_id").equals(session.optString("order_id")))throw new IllegalArgumentException("Foto und gestartete Sitzung gehören nicht zum selben Kunden/Auftrag.");
                retainSessionContext(payload,session);
                if(!session.optString("finalized_worklog_id").isEmpty())payload.put("worklog_id",session.getString("finalized_worklog_id"));
            }
            validateRevierContext(payload,false);
            EntityProtectionRules.validatePhoto(payload,master.getJSONArray("customers"),master.getJSONArray("orders"),master.getJSONArray("worklogs"));
            if(!payload.has("_photo_quality"))payload.put("_photo_quality",new JSONObject(ownerValue("settings","{}")).optString("photo_quality","optimized"));
            prepared=photoAttachments.prepare(payload);
            prepareManualChange("photo",payload,findById(master.getJSONArray("photos"),payload.optString("id")));
            for(java.util.Iterator<String> keys=prepared.metadata.keys();keys.hasNext();){String key=keys.next();payload.put(key,prepared.metadata.get(key));}
            String original=payload.optString("original_name","foto.jpg").replace('\\','/');int slash=original.lastIndexOf('/');if(slash>=0)original=original.substring(slash+1);if(original.length()>240)original=original.substring(0,240);payload.put("original_name",original);
            payload.remove("data");payload.remove("_photo_quality");payload.put("sync_state","local_saved");payload.put("filename",prepared.metadata.getString("storage_name"));
            payload.remove("thumbnail_data");
            saveAttachmentMetadata(database,entityId,prepared.metadata,"local");
            String saved=saveAction("photo", "pho_mobile_", payload);if(!request.isEmpty())putOwnerValue("photo-ui:"+request,new JSONObject().put("hash",fingerprint).put("metadata_hash",photoMetadataHash(immutableRequest)).put("id",saved).toString());database.setTransactionSuccessful();committed=true;return saved;
        } finally {
            database.endTransaction();
            if(!committed&&prepared!=null&&prepared.created){
                try(Cursor referenced=getReadableDatabase().rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? LIMIT 1",new String[]{prepared.metadata.getString("content_sha256")})){
                    if(!referenced.moveToFirst())photoAttachments.discardCreated(prepared.metadata);
                }catch(Exception ignored){/* A verified orphan is safer than masking the original failed save. */}
            }
        }
    }
    JSONObject prepareEquipmentProfilePhoto(String upload,String equipment,String request)throws Exception {
        LocalOwnerRepository files=new LocalOwnerRepository(context,this);JSONObject meta=files.fileMeta(upload);byte[] bytes=files.fileBytes(upload);EquipmentProfilePhotoRules.upload(meta,bytes);
        JSONObject photo=storeEquipmentProfilePhoto(bytes,meta.getString("mime_type"),equipment,request);
        meta.put("attached_photo",photo.getString("id")).put("equipment_id",equipment);files.putFile(upload,meta,new byte[0]);return photo;
    }
    JSONObject prepareEquipmentProfilePhotoDraft(JSONObject source,String equipment,String request)throws Exception {
        try {
            String consumed="equipment_photo_draft_consumed:"+PixelMcpWriteTools.hash(source);
            if(!ownerValue(consumed,"").isEmpty())throw new PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE","Diese Fotoauswahl wurde bereits gespeichert. Aktuellen Gerätestand prüfen.");
            JSONObject editState=new BrowserDraftRepository(context,this).read(new JSONObject().put("formatVersion",1).put("action","get").put("key","hausgarten.form.sessions.v1").toString());
            EquipmentProfilePhotoRules.draftSession(editState,source,request);
            BrowserMediaRepository media=new BrowserMediaRepository(context,this);
            JSONObject current=media.readRecord("customer-draft:"+source.optString("media_key"));
            JSONObject ref=EquipmentProfilePhotoRules.draft(current,source);
            media.assertReadableReference(current.getString("record_id"),ref);
            File directory=new File(context.getFilesDir(),"browser-media");BrowserMediaFiles.verifyReference(directory,ref);
            byte[] bytes=java.nio.file.Files.readAllBytes(BrowserMediaFiles.referenceFile(directory,ref).toPath());
            if(bytes.length!=ref.getLong("byte_size")||!McpFileTransfer.sha(bytes).equals(ref.getString("sha256")))throw new IOException("BROWSER_MEDIA_ORIGINAL_MISSING_OR_CHANGED");
            JSONObject photo=storeEquipmentProfilePhoto(bytes,ref.getString("mime_type"),equipment,request);
            putOwnerValue(consumed,request);return photo;
        }catch(PixelMcpWriteTools.Rejected known){throw known;}
        catch(Exception unavailable){throw new PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_DRAFT_UNAVAILABLE","Fotoentwurf nicht eindeutig lesbar. Auswahl erneut prüfen; Geräteangaben bleiben erhalten.");}
    }
    private JSONObject storeEquipmentProfilePhoto(byte[] bytes,String mime,String equipment,String request)throws Exception {
        PhotoAttachmentStore.Prepared prepared;
        try{prepared=photoAttachments.prepare(new JSONObject().put("data","data:"+PhotoAttachmentStore.canonicalMime(mime)+";base64,"+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)).put("role","profile").put("_photo_quality","optimized"));}
        catch(IllegalArgumentException invalid){throw new PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_INVALID","Das ausgewählte Foto ist beschädigt oder nicht unterstützt. Bitte eine andere Datei auswählen.");}
        catch(IOException unavailable){throw new PixelMcpWriteTools.Rejected("EQUIPMENT_PHOTO_UNAVAILABLE","Das Foto konnte noch nicht im privaten Speicher gesichert werden. Geräteangaben und Fotoentwurf bleiben erhalten.");}
        JSONObject photo=new JSONObject(prepared.metadata.toString()).put("format_version",1).put("id","eqp_photo_"+PixelMcpWriteTools.hash(request).substring(0,32)).put("equipment_id",equipment);
        EquipmentProfilePhotoRules.validate(photo,equipment);saveAttachmentMetadata(getWritableDatabase(),photo.getString("id"),photo,"local");return photo;
    }
    private void saveAttachmentMetadata(SQLiteDatabase database,String photoId,JSONObject metadata,String state)throws Exception{
        String hash=metadata.optString("content_sha256"),mime=PhotoAttachmentStore.canonicalMime(metadata.optString("mime_type")),name=metadata.optString("storage_name"),role=metadata.optString("role","documentation").trim().toLowerCase(Locale.ROOT);
        String extension=mime.equals("image/jpeg")?".jpg":mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":mime.equals("image/gif")?".gif":mime.equals("image/heic")?".heic":"";
        if(photoId==null||photoId.trim().isEmpty()||photoId.length()>180||!PhotoAttachmentStore.canonicalName(name)||!hash.matches("[0-9a-f]{64}")||!name.equals(hash+extension)||metadata.optLong("byte_size")<1||metadata.optLong("byte_size")>PhotoAttachmentStore.MAX_BYTES||!new HashSet<>(java.util.Arrays.asList("profile","documentation","before","during","after","damage","other")).contains(role))throw new IllegalArgumentException("Ungültige lokale Foto-Metadaten.");
        ContentValues values=new ContentValues();values.put("photo_id",photoId);values.put("content_sha256",hash);values.put("mime_type",mime);values.put("byte_size",metadata.getLong("byte_size"));values.put("storage_name",name);values.put("role",role);values.put("transfer_state",state);values.put("created_at",isoNow());
        database.insertWithOnConflict("photo_attachments",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void restoreAttachmentMetadata(String photoId,JSONObject metadata)throws Exception{saveAttachmentMetadata(getWritableDatabase(),photoId,metadata,"local");}
    JSONObject photoAttachment(String photoId)throws Exception{
        try(Cursor row=getReadableDatabase().rawQuery("SELECT photo_id,content_sha256,mime_type,byte_size,storage_name,role,transfer_state,created_at FROM photo_attachments WHERE photo_id=?",new String[]{photoId})){
            if(!row.moveToFirst())return null;return new JSONObject().put("photo_id",row.getString(0)).put("content_sha256",row.getString(1)).put("mime_type",row.getString(2)).put("byte_size",row.getLong(3)).put("storage_name",row.getString(4)).put("role",row.getString(5)).put("transfer_state",row.getString(6)).put("created_at",row.getString(7));
        }
    }
    PhotoAttachmentStore photoAttachmentStore(){return photoAttachments;}
    private Set<String> persistedConfirmedActionIds(){
        Set<String> confirmed=new HashSet<>();
        try{
            JSONObject snapshot=new JSONObject(snapshotText("{}"));
            confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("accepted_ids")));
            confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("rejected_invoice_ids")));
            confirmed.addAll(DriveJournalRules.ids(snapshot.optJSONArray("drive_applied_ids")));
            JSONObject resolved=snapshot.optJSONObject("drive_resolved_actions");
            if(resolved!=null){Iterator<String> keys=resolved.keys();while(keys.hasNext())confirmed.add(keys.next());}
        }catch(Exception ignored){/* Missing/corrupt receipts must never make an unconfirmed attachment skippable. */}
        return confirmed;
    }
    JSONArray pendingPhotoAttachments(JSONArray actions)throws Exception{
        JSONArray result=new JSONArray();Set<String> confirmed=persistedConfirmedActionIds();for(int i=0;i<actions.length();i++){JSONObject action=actions.getJSONObject(i),payload=action.optJSONObject("payload");if(!"photo".equals(action.optString("kind"))||payload==null||!payload.optString("data").isEmpty())continue;JSONObject metadata=photoAttachment(payload.optString("id"));if(metadata==null){
                // The committed Drive snapshot is durable before confirmed-delete GC.
                // If the process died before acknowledge(), a stale confirmed create
                // may remain in pending without its now-deleted private attachment.
                if(confirmed.contains(action.optString("client_id")))continue;
                result.put(new JSONObject().put("photo_id",payload.optString("id")).put("missing",true));continue;
            }for(String key:new String[]{"thumbnail_storage_name","thumbnail_sha256"})if(payload.has(key))metadata.put(key,payload.get(key));result.put(metadata);}
        return result;
    }
    void markPhotoAttachmentTransferred(String photoId){ContentValues values=new ContentValues();values.put("transfer_state","provider_readback");getWritableDatabase().update("photo_attachments",values,"photo_id=?",new String[]{photoId});}
    void recordSnapshotPhotoAttachments(JSONObject snapshot)throws Exception{
        JSONArray photos=snapshot.optJSONArray("photos");if(photos==null)return;SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try{
            for(int i=0;i<photos.length();i++){
                JSONObject photo=photos.optJSONObject(i);if(photo==null||photo.optString("id").isEmpty())continue;
                JSONObject local=photoAttachment(photo.optString("id"));
                String inline=photo.optString("data");if(inline.isEmpty())inline=photo.optString("full_data");
                if(!inline.isEmpty()){
                    PhotoAttachmentStore.Prepared prepared=photoAttachments.prepareLegacy(new JSONObject(photo.toString()).put("data",inline));
                    if(local!=null&&!local.getString("content_sha256").equals(prepared.metadata.getString("content_sha256"))){
                        if(prepared.created)photoAttachments.discardCreated(prepared.metadata);
                        throw new IllegalArgumentException("Die Foto-ID verweist auf verschiedene Originale. Abgleich angehalten.");
                    }
                    if(local==null){saveAttachmentMetadata(database,photo.getString("id"),prepared.metadata,"legacy_inline_snapshot");local=new JSONObject(prepared.metadata.toString()).put("photo_id",photo.getString("id"));}
                }
                photo.remove("data");photo.remove("full_data");
                if(local!=null&&(!photo.optString("content_sha256").matches("[0-9a-f]{64}")||!PhotoAttachmentStore.canonicalName(photo.optString("storage_name")))){
                    for(String key:new String[]{"content_sha256","mime_type","byte_size","storage_name","role"})photo.put(key,local.get(key));
                }
                if(!photo.optString("content_sha256").matches("[0-9a-f]{64}")||!PhotoAttachmentStore.canonicalName(photo.optString("storage_name")))continue;
                boolean keepExisting=false;
                try(Cursor existing=database.rawQuery("SELECT content_sha256,transfer_state FROM photo_attachments WHERE photo_id=?",new String[]{photo.getString("id")})){
                    if(existing.moveToFirst()){
                        boolean sameHash=existing.getString(0).equals(photo.getString("content_sha256"));
                        boolean authoritativeLocal=!"remote_available".equals(existing.getString(1));
                        keepExisting=sameHash||authoritativeLocal;
                    }
                }
                if(keepExisting)continue;
                JSONObject metadata=new JSONObject().put("content_sha256",photo.getString("content_sha256")).put("mime_type",photo.getString("mime_type")).put("byte_size",photo.getLong("byte_size")).put("storage_name",photo.getString("storage_name")).put("role",photo.optString("role","documentation"));
                saveAttachmentMetadata(database,photo.getString("id"),metadata,"remote_available");
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }
    void cleanupConfirmedPhotoDeletes(JSONArray submitted,Set<String> confirmed,JSONObject snapshot)throws Exception{
        Set<String> active=new HashSet<>(),protectedPending=new HashSet<>(),candidates=new HashSet<>();
        JSONArray photos=snapshot.optJSONArray("photos");for(int i=0;photos!=null&&i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null&&!photo.optString("id").isEmpty())active.add(photo.optString("id"));}
        for(int i=0;i<submitted.length();i++){
            JSONObject action=submitted.optJSONObject(i),payload=action==null?null:action.optJSONObject("payload");if(payload==null)continue;
            String client=action.optString("client_id"),kind=action.optString("kind"),id=payload.optString("id");
            if("photo".equals(kind)&&!confirmed.contains(client)&&!id.isEmpty())protectedPending.add(id);
            if("delete".equals(kind)&&"photo".equals(payload.optString("entity"))&&confirmed.contains(client)&&!id.isEmpty()&&(snapshot.optJSONObject("drive_resolved_actions")==null||!snapshot.getJSONObject("drive_resolved_actions").has(client)))candidates.add(id);
        }
        candidates.removeAll(active);candidates.removeAll(protectedPending);
        if(candidates.isEmpty())return;
        SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);
        try{
            for(String photoId:candidates){
                JSONObject metadata=photoAttachment(photoId);if(metadata==null)continue;String hash=metadata.getString("content_sha256");boolean shared;
                try(Cursor references=database.rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? AND photo_id<>? LIMIT 1",new String[]{hash,photoId})){shared=references.moveToFirst();}
                // Physical originals are removed only after the delete receipt is confirmed,
                // the committed snapshot no longer contains the photo, and no other row shares it.
                if(!shared)photoAttachments.discardCreated(metadata);
                database.delete("photo_attachments","photo_id=?",new String[]{photoId});
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }
    boolean hasOtherPhotoAttachment(String id,String hash){try(Cursor rows=getReadableDatabase().rawQuery("SELECT 1 FROM photo_attachments WHERE content_sha256=? AND photo_id<>? LIMIT 1",new String[]{hash,id})){return rows.moveToFirst();}}
    void cleanupPurgedDocument(String key,JSONObject document,String marker)throws Exception{
        synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK){SQLiteDatabase db=getWritableDatabase();if(db.inTransaction())throw new IOException("MEDIA_CLEANUP_REQUIRES_COMMITTED_STATE");putOwnerValue(marker,"pending");beginBusinessTransaction(db);try{
            JSONObject current=PixelMcpReadTools.effective(getState()),saved=OwnerJournalRules.archives(current).optJSONObject(key);if(saved==null||!saved.optBoolean("purged")||!PixelMcpWriteTools.hash(saved.getJSONObject("document")).equals(PixelMcpWriteTools.hash(document)))throw new IllegalArgumentException("Dokument-Löschbeleg fehlt oder wurde geändert.");
            if(!TrashRules.referenced(current,pendingActions(),document.optString("pdf_storage_name")))new BusinessDocumentStore(context).deleteVerified(document);putOwnerValue(marker,"done");db.setTransactionSuccessful();
        }finally{db.endTransaction();}}
    }
    void cleanupPurgedPhoto(String id,JSONObject media,String marker)throws Exception{
        synchronized(BrowserMediaRepositoryRules.FILE_CAPTURE_LOCK){SQLiteDatabase db=getWritableDatabase();if(db.inTransaction())throw new IOException("MEDIA_CLEANUP_REQUIRES_COMMITTED_STATE");
            // The row removal commits before file work. A later rollback can only leave a retry marker.
            beginBusinessTransaction(db);try{JSONObject current=PixelMcpReadTools.effective(getState());if(!TrashRules.purged(current,"photo",id))throw new IllegalArgumentException("Löschbeleg fehlt.");db.delete("photo_attachments","photo_id=?",new String[]{id});putOwnerValue(marker,"pending");db.setTransactionSuccessful();}finally{db.endTransaction();}
            beginBusinessTransaction(db);try{JSONObject current=PixelMcpReadTools.effective(getState());JSONArray queued=pendingActions();if(!TrashRules.purged(current,"photo",id))throw new IllegalArgumentException("Löschbeleg wurde geändert.");if(!hasOtherPhotoAttachment(id,media.optString("content_sha256"))&&!TrashRules.referenced(current,queued,media.optString("storage_name")))photoAttachments.discardCreated(media);String thumb=media.optString("thumbnail_storage_name");if(!thumb.isEmpty()&&!TrashRules.referenced(current,queued,thumb))photoAttachments.deleteThumbnailVerified(thumb);putOwnerValue(marker,"done");db.setTransactionSuccessful();}finally{db.endTransaction();}
        }
    }
    String loadPhotoData(String photoId)throws Exception{
        JSONObject metadata=photoAttachment(photoId);if(metadata==null)throw new IllegalArgumentException("Für dieses Foto ist noch kein dauerhaftes Original hinterlegt.");
        try{return photoAttachments.dataUri(metadata);}catch(Exception missing){throw new IOException("LOCAL_ORIGINAL_MISSING",missing);}
    }
    private void prepareManualChange(String kind,JSONObject payload,JSONObject previous)throws Exception{
        ManualChangeRules.prepare(kind,payload,previous,isoNow());
        JSONArray queued=pendingActions(),dependencies=new JSONArray();String last="";
        for(int i=0;i<queued.length();i++){JSONObject a=queued.getJSONObject(i),p=a.getJSONObject("payload");String type=a.optString("kind");if(kind.equals(type)&&payload.optString("id").equals(p.optString("id")))last=a.getString("client_id");
            if(p.optBoolean("_mcp_create"))for(String[] ref:new String[][]{{"customer_id","customer"},{"billing_customer_id","customer"},{"order_id","order"},{"worklog_id","worklog"}})if(ref[1].equals(type)&&!payload.optString(ref[0]).isEmpty()&&payload.optString(ref[0]).equals(p.optString("id")))dependencies.put(a.getString("client_id"));}
        if(!last.isEmpty())dependencies.put(last);if(dependencies.length()>0)payload.put("_sync_predecessors",dependencies);
    }
    String removeEntity(String entity, String entityId) throws Exception {
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{
            ActiveWorkProtection.require(getState(),entity,entityId);
            JSONObject all=PixelMcpReadTools.effective(getState()),current=OwnerJournalRules.findOrNull(all.getJSONArray(OwnerJournalRules.group(entity)),entityId);
            if(current==null)throw new IllegalArgumentException("Der Eintrag fehlt.");
            JSONObject payload=DeletionRules.payload(all,entity,entityId,OwnerJournalRules.version(current),isoNow());
            OwnerJournalRules.apply(new JSONObject(all.toString()),new JSONObject().put("kind","archive").put("payload",payload).put("client_id","preview-delete").put("created_at",isoNow()),new java.util.HashMap<>(),new JSONObject());
            JSONArray dependencies=new JSONArray(),prior=pendingActions();for(int i=0;i<prior.length();i++){JSONObject a=prior.getJSONObject(i);if(entityId.equals(a.getJSONObject("payload").optString("id")))dependencies.put(a.getString("client_id"));JSONArray linked=payload.optJSONArray("photo_link_ids");for(int j=0;linked!=null&&j<linked.length();j++)if(linked.getJSONObject(j).getString("id").equals(a.getJSONObject("payload").optString("id")))dependencies.put(a.getString("client_id"));}if(dependencies.length()>0)payload.put("_sync_predecessors",dependencies);
            String saved=saveAction("archive","archive_mobile_",payload);db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
    String undoRemoveEntity(String entity, String entityId) throws Exception {
        JSONObject all=PixelMcpReadTools.effective(getState()),entry=OwnerJournalRules.archives(all).optJSONObject(entity+":"+entityId);
        if(entry==null)throw new IllegalArgumentException("Der Eintrag ist nicht im Papierkorb. Aktuellen Stand prüfen.");
        return new PixelOwnerAccess(context,this).execute("restore_entity",new JSONObject().put("request_id","ui-restore-"+UUID.randomUUID()).put("entity",entity).put("entity_id",entityId).put("expected_record_hash",OwnerJournalRules.version(entry))).toString();
    }
    long remainingDeleteUndoMs() {
        long remaining = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT payload FROM pending WHERE kind IN ('delete','archive')", null)) {
            while (cursor.moveToNext()) {
                JSONObject payload = new JSONObject(cursor.getString(0));
                remaining = Math.max(remaining, payload.optLong("_undo_until", 0) - System.currentTimeMillis());
            }
        } catch (Exception ignored) {}
        return Math.max(0, remaining);
    }
    boolean testLocalOnly(String kind,JSONObject payload)throws Exception {
        // Inspect provenance, not the entire journal projection on every save/batch entry.
        JSONObject event=new JSONObject().put("kind",kind).put("payload",payload),master=new JSONObject(snapshotText("{}"));
        if(TestDataRules.localOnly(event,master))return true;
        String entity=java.util.Arrays.asList("archive","restore","purge_v1").contains(kind)?payload.optString("entity"):kind.startsWith("offer_")?"offer":kind.startsWith("invoice_")?"invoice":kind;
        JSONArray pending=pendingActions();for(int i=0;i<pending.length();i++){JSONObject a=pending.getJSONObject(i),p=a.getJSONObject("payload");if(!p.optBoolean("_local_only")&&!p.optBoolean("test_record"))continue;if(a.optString("kind").equals("customer")&&p.optString("id").equals(payload.optString("customer_id"))||a.optString("kind").equals(entity)&&!p.optString("id").isEmpty()&&p.optString("id").equals(payload.optString("id")))return true;}
        return false;
    }
    String saveAction(String kind, String prefix, JSONObject payload) throws Exception {
        SQLiteDatabase db=getWritableDatabase();beginBusinessTransaction(db);try{
        if(testLocalOnly(kind,payload))payload.put("_local_only",true);
        String id = payload.optString("id"); if (id.isEmpty()) id = prefix + UUID.randomUUID().toString().replace("-", "");
        payload.put("id", id);
        // Entity IDs are stable. Each change must have a fresh receipt ID, including deletions.
        ContentValues values = new ContentValues(); values.put("id", kind + "_change_" + UUID.randomUUID()); values.put("kind", kind); values.put("payload", payload.toString()); values.put("created_at", isoNow());
        db.insertOrThrow("pending", null, values);db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    JSONArray pendingActions() throws Exception {
        return pendingActions(false);
    }
    JSONArray pendingActionsForDrive() throws Exception {
        if(!LocalAuthorityGuard.legacySyncAllowed(context))return new JSONArray();
        return pendingActions(true);
    }
    private JSONArray pendingActions(boolean forDrive) throws Exception {
        return LocalPendingActions.read(getReadableDatabase(),forDrive,this::photoAttachment,System.currentTimeMillis());
    }
    void acknowledge(Set<String> ids) { SQLiteDatabase database=getWritableDatabase();beginBusinessTransaction(database);try{for(String id:ids)database.delete("pending","id=?",new String[]{id});database.setTransactionSuccessful();}finally{database.endTransaction();} }
}

