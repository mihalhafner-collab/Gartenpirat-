package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Real SQLite through JDBC, synthetic files only. This does not exercise Android's
 * transaction listener, CursorWindow, app writers or backup/restore coordinator. */
public final class BusinessTableStoreTests {
    private static int checks;
    private static void yes(boolean value) {if(!value)throw new AssertionError("table check "+checks);checks++;}
    private static String hash(Object value)throws Exception{return PixelMcpWriteTools.hash(value);}
    private static void denied(String code,BusinessTableStore.Action<Void> action)throws Exception {
        try{action.run();}catch(Exception e){if(String.valueOf(e.getMessage()).contains(code)){checks++;return;}throw e;}
        throw new AssertionError("Expected "+code);
    }
    private static final class Sql implements BusinessTableStore.Sql,AutoCloseable {
        final Connection connection;
        Sql(Path path)throws Exception{connection=DriverManager.getConnection("jdbc:sqlite:"+path);}
        public BusinessTableStore.Rows query(String sql,String...arguments)throws Exception {
            PreparedStatement statement=connection.prepareStatement(sql);
            try{
                for(int i=0;i<arguments.length;i++)statement.setString(i+1,arguments[i]);
                ResultSet result=statement.executeQuery();
                return new BusinessTableStore.Rows(){
                    public boolean next()throws Exception{return result.next();}
                    public String text(int column)throws Exception{return result.getString(column+1);}
                    public long number(int column)throws Exception{return result.getLong(column+1);}
                    public void close()throws Exception{try{result.close();}finally{statement.close();}}
                };
            }catch(Exception e){statement.close();throw e;}
        }
        public void execute(String sql,Object...arguments)throws Exception {
            try(PreparedStatement statement=connection.prepareStatement(sql)){
                for(int i=0;i<arguments.length;i++)statement.setObject(i+1,arguments[i]);statement.execute();
            }
        }
        public <T>T transaction(BusinessTableStore.Action<T> action)throws Exception {
            if(!connection.getAutoCommit())return action.run();
            connection.setAutoCommit(false);
            try{T result=action.run();connection.commit();return result;}
            catch(Exception|Error e){connection.rollback();throw e;}
            finally{connection.setAutoCommit(true);}
        }
        String owner(String key)throws Exception {
            try(BusinessTableStore.Rows rows=query("SELECT value FROM owner_state WHERE key=?",key)){return rows.next()?rows.text(0):null;}
        }
        JSONArray pending()throws Exception {
            JSONArray result=new JSONArray();
            try(BusinessTableStore.Rows rows=query("SELECT id,kind,payload,created_at FROM pending ORDER BY created_at,id")){
                while(rows.next())result.put(new JSONObject().put("client_id",rows.text(0)).put("kind",rows.text(1)).put("payload",new JSONObject(rows.text(2))).put("created_at",rows.text(3)));
            }return result;
        }
        void append(String id,JSONObject payload)throws Exception{execute("INSERT INTO pending VALUES(?,'customer',?,'2026-10-28T12:00:00Z')",id,payload.toString());}
        public void close()throws Exception{connection.close();}
    }
    private static JSONObject fixture()throws Exception {
        JSONObject source=new JSONObject().put("schema_version",2).put("unknown_optional",new JSONObject().put("decimal","12.3400").put("unicode","Grün 🌿"));
        int[] counts={300,40,5000,500,3000};
        for(int k=0;k<counts.length;k++){
            JSONArray rows=new JSONArray();
            for(int i=0;i<counts[k];i++){
                JSONObject row=new JSONObject().put("id",BusinessTableStore.COLLECTIONS[k]+"_qa_"+i).put("name","[TEST] Synthetisch "+i).put("unknown_field",JSONObject.NULL).put("test_record",true);
                if(k==0)row.put("active",i%5==0?0:1).put("phone","+43 (660) 123-"+i);
                if(k!=0)row.put("customer_id","customers_qa_"+(i%300));
                if(k==1)row.put("title","QA Auftrag "+i).put("status",i%2==0?"Offen":"Abgeschlossen");
                if(k==2)row.put("hours","1.25").put("order_id","orders_qa_"+(i%40)).put("billing_customer_id","customers_qa_"+(i%7)).put("activity","QA Arbeit").put("billed",i%2).put("work_date",i%2==0?"2026-10-27":"2026-10-28");
                if(k==3)row.put("amount","123.45").put("invoice_number","QA-R-"+i);
                if(k==4)row.put("capture_time","2026-10-28T12:00:00Z").put("caption","QA Foto").put("role",i%2==0?"profile":"work").put("worklog_id","worklogs_qa_"+(i%5000));
                rows.put(row);
            }source.put(BusinessTableStore.COLLECTIONS[k],rows);
        }
        source.getJSONArray("photos").getJSONObject(0).put("large_optional","🌿".repeat(600000));return source;
    }
    private static void parity(BusinessTableStore tables,JSONObject source)throws Exception {
        String[] kinds={"customer","order","worklog","invoice","photo"};
        JSONObject[] filters={new JSONObject(),new JSONObject().put("offset",3).put("limit",7),new JSONObject().put("offset",999999),new JSONObject().put("query","Synthetisch 2"),new JSONObject().put("query","+43 660 123"),new JSONObject().put("include_inactive",true),new JSONObject().put("customer_id","customers_qa_2"),new JSONObject().put("order_id","orders_qa_2"),new JSONObject().put("billing_customer_id","customers_qa_2"),new JSONObject().put("date_from","2026-10-28").put("date_to","2026-10-28"),new JSONObject().put("open_only",true),new JSONObject().put("status","Offen"),new JSONObject().put("role","profile"),new JSONObject().put("worklog_id","worklogs_qa_2")};
        JSONObject context=tables.referenceContext();
        for(int i=0;i<kinds.length;i++)for(JSONObject filter:filters){
            // The public contract accepts worklog_id only for photo listing.
            if(filter.has("worklog_id")&&!kinds[i].equals("photo"))continue;
            JSONArray actual=(JSONArray)SnapshotReadSupport.withoutBinary(tables.selected(BusinessTableStore.COLLECTIONS[i],kinds[i],filter,context));
            JSONArray expected=SnapshotReadSupport.select(source.getJSONArray(BusinessTableStore.COLLECTIONS[i]),filter,kinds[i],source);
            if(!hash(actual).equals(hash(expected)))throw new AssertionError("Selection mismatch "+kinds[i]+" "+filter);
            checks++;
        }
    }
    private static void run(Path file)throws Exception {
        JSONObject source=fixture();String original=source.toString(),sourceHash=hash(source),request="qa_table_transfer";
        BusinessTableStore.Evidence proof=value->{if(!hash(value).equals(sourceHash))throw new Exception("QA_SOURCE_MISMATCH");return hash("synthetic evidence only");};
        try(Sql sql=new Sql(file)){
            sql.execute("CREATE TABLE owner_state(key TEXT PRIMARY KEY,value TEXT NOT NULL)");sql.execute("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)");
            sql.execute("INSERT INTO owner_state VALUES('snapshot',?)",original);BusinessTableStore tables=new BusinessTableStore(sql);
            yes(tables.status().getString("phase").equals("SNAPSHOT"));
            denied("TABLE_SOURCE_CHANGED",()->{tables.prepare(request,hash("wrong"),proof);return null;});
            denied("TABLE_BACKUP_EVIDENCE_REQUIRED",()->{tables.prepare(request,sourceHash,null);return null;});
            tables.prepare(request,sourceHash,proof);yes(!tables.active());
            denied("TABLE_TRANSFER_IN_PROGRESS",()->{sql.append("qa_blocked",new JSONObject());return null;});
            denied("TABLE_TRANSFER_IN_PROGRESS",()->{sql.execute("UPDATE owner_state SET value='{}' WHERE key='snapshot'");return null;});
            String checkpoint=hash(tables.status());
            denied("QA_INTERRUPTED",()->sql.transaction(()->{tables.copyBatch(request,33,proof);throw new Exception("QA_INTERRUPTED");}));
            yes(checkpoint.equals(hash(tables.status())));tables.copyBatch(request,73,proof);yes(tables.status().getInt("offset")==73);
            tables.cancel(request);yes(tables.status().getString("phase").equals("ABORTED"));
            sql.append("qa_cancelled",new JSONObject());sql.execute("DELETE FROM pending WHERE id='qa_cancelled'");tables.prepare(request,sourceHash,proof);
        }
        try(Sql sql=new Sql(file)){
            BusinessTableStore tables=new BusinessTableStore(sql);yes(tables.status().getInt("offset")==73);
            while(tables.status().getString("phase").equals("COPYING"))tables.copyBatch(request,1000,proof);
            tables.activate(request,sourceHash,proof);tables.activate(request,sourceHash,proof);yes(tables.active());yes(sourceHash.equals(hash(tables.exportSnapshot())));yes(original.equals(sql.owner("snapshot")));
            parity(tables,source);
            JSONObject row=tables.get("customers","customers_qa_1"),edited=new JSONObject(row.toString()).put("name","[TEST] QA nach Übernahme");
            JSONObject receipt=tables.put("customers",edited,hash(row),"qa_table_write");yes(hash(receipt).equals(hash(tables.put("customers",edited,hash(row),"qa_table_write"))));
            denied("TABLE_REQUEST_REUSED",()->{tables.put("customers",row,hash(edited),"qa_table_write");return null;});
            denied("TABLE_WRITE_CONFLICT",()->{tables.put("customers",row,hash(row),"qa_table_conflict");return null;});
            JSONObject current=tables.exportSnapshot();JSONArray reversed=new JSONArray(),customers=current.getJSONArray("customers");for(int i=customers.length()-1;i>=0;i--)reversed.put(customers.get(i));current.put("customers",reversed);tables.replaceSnapshot(current);
            yes(hash(current).equals(hash(tables.exportSnapshot())));yes(original.equals(sql.owner("snapshot")));
            JSONObject payload=new JSONObject(edited.toString()).put("name","[TEST] QA Journal").put("_sync_base",LocalJournalRules.guard(edited,"customer"));
            sql.transaction(()->{
                sql.append("qa_temporary",payload);sql.execute("DELETE FROM pending WHERE id='qa_temporary'");sql.append("qa_final",payload);sql.execute("INSERT INTO owner_state VALUES('receipt:qa_final','{}')");yes(!tables.projectionCurrent());
                tables.commitJournal(sql.pending(),master->{LocalBusinessState.prepare(master);master.put("processing_engine",LocalBusinessState.ENGINE);});return null;
            });
            yes(tables.projectionCurrent());yes(tables.get("customers",edited.getString("id")).getString("name").equals("[TEST] QA Journal"));
            JSONObject committed=tables.exportSnapshot();yes(LocalJournalRules.ids(committed.getJSONArray("drive_applied_ids")).contains("qa_final"));yes(!LocalJournalRules.ids(committed.getJSONArray("drive_applied_ids")).contains("qa_temporary"));yes(committed.getString("processing_engine").equals(LocalBusinessState.ENGINE));yes(sql.owner("business_tables:original:"+hash(request))==null);
            sql.execute("CREATE TRIGGER qa_fail BEFORE INSERT ON business_customers WHEN NEW.id='customers_qa_1' BEGIN SELECT RAISE(ABORT,'QA_STORAGE_FAILURE'); END");
            denied("QA_STORAGE_FAILURE",()->sql.transaction(()->{
                JSONObject next=tables.get("customers","customers_qa_1");sql.append("qa_failure",new JSONObject(next.toString()).put("name","[TEST] QA verboten").put("_sync_base",LocalJournalRules.guard(next,"customer")));sql.execute("INSERT INTO owner_state VALUES('receipt:qa_failure','{}')");tables.commitJournal(sql.pending(),LocalBusinessState::prepare);return null;
            }));
            yes(sql.owner("receipt:qa_failure")==null);yes(hash(committed).equals(hash(tables.exportSnapshot())));yes(sql.pending().length()==1);yes(tables.projectionCurrent());sql.execute("DROP TRIGGER qa_fail");
        }
        try(Sql sql=new Sql(file)){
            BusinessTableStore tables=new BusinessTableStore(sql);yes(tables.projectionCurrent());String currentHash=hash(tables.exportSnapshot());
            BusinessTableStore.Evidence currentProof=value->{yes(currentHash.equals(hash(value)));return hash("synthetic current evidence");};
            tables.rollback("qa_table_rollback",currentHash,currentProof);tables.rollback("qa_table_rollback",currentHash,currentProof);yes(!tables.active());yes(currentHash.equals(hash(tables.snapshot("{}"))));yes(sql.owner("business_tables:original:"+hash(request))==null);
        }
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.sqlite.JDBC");Path directory=Files.createTempDirectory("gp-table-jdbc-");
        try{run(directory.resolve("synthetic.sqlite"));System.out.println("PASS "+checks+" SQLite/JDBC table checks; synthetic 300 customers/40 orders/5000 worklogs/500 invoices/3000 photos; Android transaction listener and full restore NOT tested");}
        finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}}
    }
}
