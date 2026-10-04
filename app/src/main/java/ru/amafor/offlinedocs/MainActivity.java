package ru.amafor.offlinedocs;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final String PREFS = "catalog";
    private LinearLayout root;
    private String mode = "home";
    private String currentFolderId = null;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        File d = new File(getFilesDir(), "docs");
        if (!d.exists()) d.mkdirs();
        showHome();
    }

    private void base(String title, boolean back) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244,246,248));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.rgb(38,50,56));
        if (back) {
            Button b = new Button(this);
            b.setText("‹");
            b.setTextSize(28);
            b.setOnClickListener(v -> goBack());
            bar.addView(b, new LinearLayout.LayoutParams(dp(55), dp(52)));
        }
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(t, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);
        setContentView(root);
    }

    private void showHome() {
        mode = "home"; currentFolderId = null;
        base("Офлайн Документы", false);
        addBigButton("Факсограммы", v -> showFax());
        addBigButton("Инструкции", v -> showInstructionFolders());
        TextView h = hint("Все добавленные документы копируются во внутреннюю память приложения и доступны без интернета.");
        root.addView(h);
    }

    private void showFax() {
        mode = "fax"; currentFolderId = null;
        showDocuments("Факсограммы", "fax", null);
    }

    private void showInstructionFolders() {
        mode = "folders"; currentFolderId = null;
        base("Инструкции", true);
        Button add = smallButton("＋ Новая папка");
        add.setOnClickListener(v -> editFolder(null, null));
        root.addView(add);

        JSONArray arr = folders();
        ListView list = new ListView(this);
        ArrayList<String> names = new ArrayList<>();
        for (int i=0;i<arr.length();i++) names.add(arr.optJSONObject(i).optString("name"));
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener((a,v,p,id)->{
            JSONObject f = arr.optJSONObject(p);
            currentFolderId = f.optString("id");
            mode = "folder";
            showDocuments(f.optString("name"), "instructions", currentFolderId);
        });
        list.setOnItemLongClickListener((a,v,p,id)->{
            JSONObject f = arr.optJSONObject(p);
            folderMenu(f.optString("id"), f.optString("name"));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1,0,1));
    }

    private void showDocuments(String title, String section, String folderId) {
        mode = folderId == null ? "fax" : "folder";
        currentFolderId = folderId;
        base(title, true);

        Button add = smallButton("＋ Добавить документы");
        add.setOnClickListener(v -> pickFiles());
        root.addView(add);

        JSONArray docs = docs();
        ArrayList<JSONObject> visible = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (int i=0;i<docs.length();i++) {
            JSONObject d = docs.optJSONObject(i);
            boolean sec = section.equals(d.optString("section"));
            boolean fld = Objects.equals(folderId, d.isNull("folderId") ? null : d.optString("folderId"));
            if (sec && fld) {
                visible.add(d);
                labels.add(d.optString("title"));
            }
        }
        ListView list = new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        list.setOnItemClickListener((a,v,p,id)->openDoc(visible.get(p)));
        list.setOnItemLongClickListener((a,v,p,id)->{
            docMenu(visible.get(p));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1,0,1));
        if (labels.isEmpty()) root.addView(hint("Документов пока нет."));
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req,res,data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
        try {
            if (data.getClipData() != null) {
                for (int i=0;i<data.getClipData().getItemCount();i++) importUri(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData()!=null) importUri(data.getData());
        } catch(Exception e) {
            Toast.makeText(this, "Ошибка импорта: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refreshCurrent();
    }

    private void importUri(Uri uri) throws Exception {
        String display = displayName(uri);
        String ext = "";
        int dot = display.lastIndexOf('.');
        if (dot >= 0 && dot < display.length()-1) ext = display.substring(dot);
        String stored = UUID.randomUUID().toString() + ext;
        File out = new File(new File(getFilesDir(),"docs"), stored);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n=in.read(buf))>0) os.write(buf,0,n);
        }
        JSONObject d = new JSONObject();
        d.put("id", UUID.randomUUID().toString());
        d.put("title", display);
        d.put("stored", stored);
        String mime = getContentResolver().getType(uri);
        d.put("mime", mime == null ? "application/octet-stream" : mime);
        d.put("section", currentFolderId == null ? "fax" : "instructions");
        if (currentFolderId == null) d.put("folderId", JSONObject.NULL); else d.put("folderId", currentFolderId);
        JSONArray arr = docs();
        arr.put(d);
        saveArray("docs", arr);
    }

    private String displayName(Uri uri) {
        Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null,null,null);
        if (c != null) {
            try { if (c.moveToFirst()) return c.getString(0); }
            finally { c.close(); }
        }
        String s = uri.getLastPathSegment();
        return s == null ? "document" : s;
    }

    private void openDoc(JSONObject d) {
        try {
            Uri uri = Uri.parse("content://ru.amafor.offlinedocs.files/" + Uri.encode(d.optString("stored")));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, d.optString("mime","application/octet-stream"));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch(Exception e) {
            Toast.makeText(this, "Нет приложения для открытия этого файла", Toast.LENGTH_LONG).show();
        }
    }

    private void shareDoc(JSONObject d) {
        Uri uri = Uri.parse("content://ru.amafor.offlinedocs.files/" + Uri.encode(d.optString("stored")));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(d.optString("mime","application/octet-stream"));
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Поделиться файлом"));
    }

    private void docMenu(JSONObject d) {
        String[] items = {"Открыть","Поделиться","Переименовать","Удалить"};
        new AlertDialog.Builder(this).setTitle(d.optString("title")).setItems(items,(x,w)->{
            if (w==0) openDoc(d);
            else if (w==1) shareDoc(d);
            else if (w==2) renameDoc(d);
            else deleteDoc(d);
        }).show();
    }

    private void renameDoc(JSONObject d) {
        EditText e = new EditText(this); e.setText(d.optString("title")); e.selectAll();
        new AlertDialog.Builder(this).setTitle("Новое название").setView(e)
            .setPositiveButton("Сохранить",(x,w)->{
                try { d.put("title", e.getText().toString().trim()); saveArray("docs", docs()); }
                catch(Exception ex) {}
                refreshCurrent();
            }).setNegativeButton("Отмена",null).show();
    }

    private void deleteDoc(JSONObject target) {
        new AlertDialog.Builder(this).setTitle("Удалить документ?")
            .setPositiveButton("Удалить",(x,w)->{
                JSONArray src=docs(), out=new JSONArray();
                for(int i=0;i<src.length();i++) {
                    JSONObject d=src.optJSONObject(i);
                    if (!d.optString("id").equals(target.optString("id"))) out.put(d);
                }
                new File(new File(getFilesDir(),"docs"), target.optString("stored")).delete();
                saveArray("docs",out);
                refreshCurrent();
            }).setNegativeButton("Отмена",null).show();
    }

    private void editFolder(String id, String oldName) {
        EditText e = new EditText(this); if(oldName!=null){e.setText(oldName); e.selectAll();}
        new AlertDialog.Builder(this).setTitle(id==null?"Новая папка":"Переименовать папку").setView(e)
            .setPositiveButton("Сохранить",(x,w)->{
                String name=e.getText().toString().trim(); if(name.isEmpty()) return;
                JSONArray a=folders();
                try {
                    if(id==null){ JSONObject f=new JSONObject(); f.put("id",UUID.randomUUID().toString()); f.put("name",name); a.put(f);}
                    else for(int i=0;i<a.length();i++){JSONObject f=a.optJSONObject(i);if(id.equals(f.optString("id")))f.put("name",name);}
                    saveArray("folders",a);
                } catch(Exception ex){}
                showInstructionFolders();
            }).setNegativeButton("Отмена",null).show();
    }

    private void folderMenu(String id, String name) {
        new AlertDialog.Builder(this).setTitle(name)
            .setItems(new String[]{"Переименовать","Удалить"},(x,w)->{
                if(w==0) editFolder(id,name); else deleteFolder(id);
            }).show();
    }

    private void deleteFolder(String id) {
        JSONArray ds=docs();
        for(int i=0;i<ds.length();i++){
            JSONObject d=ds.optJSONObject(i);
            if(id.equals(d.optString("folderId"))){
                Toast.makeText(this,"Сначала удалите документы из папки",Toast.LENGTH_LONG).show(); return;
            }
        }
        JSONArray src=folders(),out=new JSONArray();
        for(int i=0;i<src.length();i++){JSONObject f=src.optJSONObject(i);if(!id.equals(f.optString("id")))out.put(f);}
        saveArray("folders",out); showInstructionFolders();
    }

    private JSONArray folders(){ return readArray("folders"); }
    private JSONArray docs(){ return readArray("docs"); }
    private JSONArray readArray(String key){
        String s=getSharedPreferences(PREFS,MODE_PRIVATE).getString(key,"[]");
        try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}
    }
    private void saveArray(String key, JSONArray a){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(key,a.toString()).apply();
    }

    private void refreshCurrent(){
        if("fax".equals(mode)) showFax();
        else if("folder".equals(mode)){
            String name="Инструкции";
            JSONArray a=folders();
            for(int i=0;i<a.length();i++){JSONObject f=a.optJSONObject(i);if(currentFolderId.equals(f.optString("id")))name=f.optString("name");}
            showDocuments(name,"instructions",currentFolderId);
        } else showInstructionFolders();
    }

    private void goBack(){
        if("home".equals(mode)){finish();return;}
        if("folder".equals(mode)) showInstructionFolders();
        else showHome();
    }

    @Override public void onBackPressed(){ goBack(); }

    private void addBigButton(String text, View.OnClickListener l){
        Button b=smallButton(text); b.setTextSize(18); b.setOnClickListener(l);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(70)); p.setMargins(dp(16),dp(14),dp(16),0);
        root.addView(b,p);
    }
    private Button smallButton(String text){
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(16); return b;
    }
    private TextView hint(String s){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(14); t.setTextColor(Color.DKGRAY); t.setPadding(dp(18),dp(16),dp(18),dp(16)); return t;
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
