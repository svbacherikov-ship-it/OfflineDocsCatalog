package ru.amafor.offlinedocs;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

public class LocalFileProvider extends ContentProvider {
    public static Uri documentUri(Context context,String stored,String title) {
        String name=title==null||title.trim().isEmpty()?stored:title;
        name=name.replace('/','_').replace('\\','_').replace('\u0000','_');
        int dot=stored.lastIndexOf('.');
        if(name.lastIndexOf('.')<=0&&dot>0)name+=stored.substring(dot);
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".files")
                .appendPath("document").appendPath(stored).appendPath(name).build();
    }
    @Override public boolean onCreate(){return true;}
    private String storedName(Uri uri){
        List<String> s=uri.getPathSegments();
        if(s.size()==3&&"document".equals(s.get(0)))return s.get(1);
        return s.size()==1?s.get(0):null;
    }
    private File resolve(Uri uri)throws FileNotFoundException{
        String name=storedName(uri);
        if(name==null||name.isEmpty()||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))throw new FileNotFoundException();
        File base=new File(getContext().getFilesDir(),"docs");
        File f=new File(base,name);
        try{
            if(!f.getCanonicalFile().getParentFile().equals(base.getCanonicalFile())||!f.isFile())throw new FileNotFoundException();
            return f;
        }catch(Exception e){throw new FileNotFoundException();}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){
        String n=storedName(uri);if(n==null)return "application/octet-stream";
        int dot=n.lastIndexOf('.');if(dot<0)return "application/octet-stream";
        String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(dot+1).toLowerCase(Locale.ROOT));
        return m==null?"application/octet-stream":m;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){
        try{
            File f=resolve(uri);
            String name=uri.getPathSegments().size()==3?uri.getLastPathSegment():f.getName();
            String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor c=new MatrixCursor(columns);
            Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=name;
                else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=f.length();
            }
            c.addRow(row);return c;
        }catch(Exception e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
