package ru.amafor.offlinedocs;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public class OfficeViewerActivity extends Activity {
    private WebView web;
    private Button pdf;
    private String title, stored, mime;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean ready;
    private boolean startedLoad;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        title=getIntent().getStringExtra("title");
        stored=getIntent().getStringExtra("stored");
        mime=getIntent().getStringExtra("mime");
        if(title==null)title="Документ";
        if(stored==null||stored.contains("/")||stored.contains("\\")||stored.equals("..")){finish();return;}
        File file=new File(new File(getFilesDir(),"docs"),stored);
        if(!file.isFile()){Toast.makeText(this,"Файл не найден",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(Color.rgb(0,99,132));
        Button back=new Button(this);back.setText("‹");back.setContentDescription("Назад");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));
        TextView heading=new TextView(this);heading.setText(title);heading.setTextColor(Color.WHITE);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);bar.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));heading.setGravity(Gravity.CENTER_VERTICAL);
        pdf=new Button(this);pdf.setText("В PDF");pdf.setEnabled(false);pdf.setOnClickListener(v->printPdf());bar.addView(pdf,new LinearLayout.LayoutParams(dp(88),dp(52)));
        Button external=new Button(this);external.setText("⋮");external.setContentDescription("Открыть оригинал или поделиться");external.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"Открыть в другом приложении","Поделиться оригиналом"},(d,i)->external(i==1)).show());bar.addView(external,new LinearLayout.LayoutParams(dp(48),dp(52)));
        root.addView(bar);
        web=new WebView(this);WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setDomStorageEnabled(false);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setSupportZoom(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return true;}
            @Override public void onPageFinished(WebView v,String url){if(startedLoad){ready=true;pdf.setEnabled(true);}}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        web.loadDataWithBaseURL(null,"<html><meta charset='utf-8'><body>Подготовка документа…</body></html>","text/html","UTF-8",null);
        worker.submit(()->{
            try{
                String html=OfficePreview.read(file,title,mime);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;startedLoad=true;web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);});
            }catch(Exception|LinkageError|OutOfMemoryError ex){
                android.util.Log.e("OfficeViewer","Preview failed",ex);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Не удалось открыть документ").setMessage("Формат повреждён, защищён, слишком велик или не поддерживается встроенным просмотром. Можно открыть оригинал во внешнем редакторе.").setPositiveButton("Открыть оригинал",(d,i)->external(false)).setNegativeButton("Назад",(d,i)->finish()).show();});
            }
        });
        immersive();
    }
    private void external(boolean send){
        try{
            Uri uri=Uri.parse("content://"+getPackageName()+".files/"+Uri.encode(stored));
            Intent i=new Intent(send?Intent.ACTION_SEND:Intent.ACTION_VIEW);
            String type=mime==null?"application/octet-stream":mime;
            if(send){i.setType(type);i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("Документ",uri));}
            else i.setDataAndType(uri,type);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,send?"Поделиться":"Открыть оригинал"));
        }catch(Exception e){Toast.makeText(this,"Нет подходящего приложения",Toast.LENGTH_LONG).show();}
    }
    private void printPdf(){
        if(!ready)return;
        new AlertDialog.Builder(this).setTitle("Сохранение в PDF").setMessage("Будет сохранено упрощённое представление документа. В системном окне выберите «Сохранить как PDF», затем размер бумаги, ориентацию и папку. Для точных схем и бланков используйте оригинал.").setPositiveButton("Продолжить",(d,w)->{
            try{
                PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
                String name=title.replaceFirst("\\.[^.]+$","");
                PrintAttributes attrs=new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build();
                pm.print(name,web.createPrintDocumentAdapter(name),attrs);
            }catch(Exception e){Toast.makeText(this,"Не удалось открыть системный диалог печати",Toast.LENGTH_LONG).show();}
        }).setNegativeButton("Отмена",null).show();
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override protected void onDestroy(){worker.shutdownNow();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
