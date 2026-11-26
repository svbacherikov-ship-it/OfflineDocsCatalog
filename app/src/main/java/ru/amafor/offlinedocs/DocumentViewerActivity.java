package ru.amafor.offlinedocs;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.LruCache;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DocumentViewerActivity extends Activity {
    private File file;
    private String stored;
    private String title;
    private String mime;
    private TextView pageLabel;
    private View contentView;
    private boolean pdfContinuous;
    private LinearLayout topPanel;
    private TextView zoomLabel;
    private boolean panelTransparent;
    private final Runnable restorePanel=()->setPanelTransparency(false);

    public static boolean supports(String title, String mime) {
        String n = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        return m.equals("application/pdf") || n.endsWith(".pdf")
            || m.startsWith("image/") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".bmp")
            || m.startsWith("text/") || n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".csv") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".md");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        stored = getIntent().getStringExtra("stored");
        title = getIntent().getStringExtra("title");
        mime = getIntent().getStringExtra("mime");
        file = new File(new File(getFilesDir(), "docs"), stored == null ? "" : stored);
        if (!file.isFile()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        pdfContinuous = getSharedPreferences("viewer", MODE_PRIVATE).getBoolean("pdf_continuous", true);
        buildViewer(state == null ? 0 : state.getInt("page", 0));
        immersive();
    }

    private void buildViewer(int startPage) {
        if(topPanel!=null)topPanel.removeCallbacks(restorePanel);
        topPanel=null;pageLabel=null;zoomLabel=null;panelTransparent=false;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        String n = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        boolean isPdf = m.equals("application/pdf") || n.endsWith(".pdf");

        if (isPdf) {
            if (pdfContinuous) {
                PdfContinuousList pdf = new PdfContinuousList(this, file, startPage, (page, total) -> {
                    if (pageLabel != null) pageLabel.setText((page + 1) + " / " + total);
                });
                contentView = pdf;
                pdf.onMovement=this::fadePanel;
                pdf.onZoom=()->{if(zoomLabel!=null)zoomLabel.setText(Math.round(pdf.zoom*100)+"%");};
                root.addView(pdf, new FrameLayout.LayoutParams(-1, -1));
            } else {
                PdfZoomView pdf = new PdfZoomView(this, file, startPage, (page, total) -> {
                    if (pageLabel != null) pageLabel.setText((page + 1) + " / " + total);
                });
                contentView = pdf;
                pdf.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_MOVE)fadePanel();return false;});
                root.addView(pdf, new FrameLayout.LayoutParams(-1, -1));
            }
        } else if (m.startsWith("image/") || isImageName(n)) {
            ImageZoomView img = new ImageZoomView(this, file);
            contentView = img;
            img.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_MOVE)fadePanel();return false;});
            root.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ScrollView sc = new ScrollView(this);
            sc.setBackgroundColor(Color.rgb(18, 18, 18));
            TextView tv = new TextView(this);
            tv.setTextColor(Color.rgb(238, 238, 238));
            tv.setTextSize(16);
            tv.setPadding(dp(18), dp(78), dp(18), dp(30));
            tv.setText(readText(file));
            tv.setTextIsSelectable(true);
            ScaleGestureDetector textScale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    float size=tv.getTextSize()/getResources().getDisplayMetrics().scaledDensity;
                    tv.setTextSize(Math.max(10f,Math.min(42f,size*d.getScaleFactor())));
                    fadePanel();return true;
                }
            });
            sc.setOnTouchListener((v,e)->{textScale.onTouchEvent(e);return e.getPointerCount()>1||textScale.isInProgress();});
            sc.setOnScrollChangeListener((v,x,y,ox,oy)->{if(y!=oy)fadePanel();});
            sc.addView(tv, new ScrollView.LayoutParams(-1, -2));
            contentView = sc;
            root.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout top = new LinearLayout(this);
        topPanel=top;
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.argb(185, 15, 18, 20));
        topBg.setCornerRadius(dp(14));
        top.setBackground(topBg);

        TextView back = topButton("‹", 32);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView t = new TextView(this);
        t.setText(title == null ? "Документ" : title);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(dp(2),0,dp(1),Color.BLACK);
        t.setTextSize(16);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1));

        if (isPdf) {
            TextView mode = topButton(pdfContinuous ? "↕" : "⇄", 24);
            mode.setContentDescription(pdfContinuous ? "Постраничный режим" : "Непрерывная прокрутка");
            mode.setOnClickListener(v -> {
                int page = currentPdfPage();
                pdfContinuous = !pdfContinuous;
                getSharedPreferences("viewer", MODE_PRIVATE).edit().putBoolean("pdf_continuous", pdfContinuous).apply();
                buildViewer(page);
                immersive();
            });
            top.addView(mode, new LinearLayout.LayoutParams(dp(52), dp(48)));
        }

        TextView share = topButton("↗", 24);
        share.setContentDescription("Поделиться");
        share.setOnClickListener(v -> share());
        top.addView(share, new LinearLayout.LayoutParams(dp(52), dp(48)));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP);
        topLp.setMargins(dp(8), dp(8), dp(8), 0);
        root.addView(top, topLp);

        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setTextSize(13);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable pageBg = new GradientDrawable();
        pageBg.setColor(Color.argb(170, 15, 18, 20));
        pageBg.setCornerRadius(dp(18));
        pageLabel.setBackground(pageBg);

        if (contentView instanceof PdfZoomView || contentView instanceof PdfContinuousList) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, dp(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            p.setMargins(0, 0, 0, dp(12));
            if (contentView instanceof PdfZoomView) {
                root.addView(pageLabel,p);
                ((PdfZoomView)contentView).notifyPage();
            } else {
                PdfContinuousList list=(PdfContinuousList)contentView;
                LinearLayout controls=new LinearLayout(this);
                controls.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(190,15,18,20));bg.setCornerRadius(dp(18));controls.setBackground(bg);
                TextView minus=topButton("−",23);minus.setContentDescription("Уменьшить масштаб");minus.setOnClickListener(v->list.changeZoom(list.zoom/1.25f,list.getWidth()/2f,list.getHeight()/2f));
                controls.addView(minus,new LinearLayout.LayoutParams(dp(42),dp(40)));
                zoomLabel=topButton(Math.round(list.zoom*100)+"%",13);zoomLabel.setContentDescription("Сбросить масштаб");zoomLabel.setOnClickListener(v->list.changeZoom(1f,list.getWidth()/2f,list.getHeight()/2f));controls.addView(zoomLabel,new LinearLayout.LayoutParams(dp(58),dp(40)));
                TextView plus=topButton("+",23);plus.setContentDescription("Увеличить масштаб");plus.setOnClickListener(v->list.changeZoom(list.zoom*1.25f,list.getWidth()/2f,list.getHeight()/2f));controls.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(40)));
                pageLabel.setBackgroundColor(Color.TRANSPARENT);controls.addView(pageLabel,new LinearLayout.LayoutParams(-2,dp(40)));
                p.height=dp(42);root.addView(controls,p);list.notifyPage();
            }
        }

        setContentView(root);
    }

    private TextView topButton(String s, int size) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setShadowLayer(dp(2),0,dp(1),Color.BLACK);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void share() {
        Uri uri = LocalFileProvider.documentUri(this,stored,title);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri(title,uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Поделиться файлом"));
    }

    private void setPanelTransparency(boolean transparent){
        if(topPanel==null||panelTransparent==transparent)return;
        panelTransparent=transparent;
        if(topPanel.getBackground() instanceof GradientDrawable)((GradientDrawable)topPanel.getBackground()).setColor(Color.argb(transparent?22:185,15,18,20));
    }
    private void fadePanel(){
        if(topPanel==null)return;
        setPanelTransparency(true);
        topPanel.removeCallbacks(restorePanel);
        topPanel.postDelayed(restorePanel,850);
    }
    @Override protected void onDestroy(){
        if(topPanel!=null)topPanel.removeCallbacks(restorePanel);
        super.onDestroy();
    }

    private void immersive() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        immersive();
        if (contentView instanceof PdfZoomView) ((PdfZoomView)contentView).rerender();
        if (contentView instanceof PdfContinuousList) ((PdfContinuousList)contentView).invalidatePages();
        if (contentView instanceof ImageZoomView) ((ImageZoomView)contentView).resetForSize();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (contentView instanceof PdfZoomView) out.putInt("page", ((PdfZoomView)contentView).getPageIndex());
        else if (contentView instanceof PdfContinuousList) out.putInt("page", ((PdfContinuousList)contentView).getPageIndex());
    }

    private int currentPdfPage() {
        if (contentView instanceof PdfZoomView) return ((PdfZoomView)contentView).getPageIndex();
        if (contentView instanceof PdfContinuousList) return ((PdfContinuousList)contentView).getPageIndex();
        return 0;
    }

    private String readText(File f) {
        long max = 4L * 1024L * 1024L;
        try (InputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(b)) > 0 && total < max) {
                int use = (int)Math.min(n, max - total);
                out.write(b, 0, use);
                total += use;
            }
            String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (f.length() > max) s += "\n\n… файл обрезан для просмотра; полный файл сохранён без изменений.";
            return s;
        } catch(Exception e) {
            return "Не удалось открыть текстовый файл: " + e.getMessage();
        }
    }

    private boolean isImageName(String n) {
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".bmp");
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    interface PageListener { void onPage(int page, int total); }

    static class PdfZoomView extends View {
        private final ParcelFileDescriptor pfd;
        private final PdfRenderer renderer;
        private Bitmap bitmap;
        private int pageIndex;
        private final PageListener listener;
        private final ScaleGestureDetector scaler;
        private final GestureDetector gestures;
        private float zoom = 1f;
        private float panX = 0f, panY = 0f;

        PdfZoomView(Context c, File file, int start, PageListener listener) {
            super(c);
            this.listener = listener;
            setBackgroundColor(Color.BLACK);
            ParcelFileDescriptor tempPfd = null;
            PdfRenderer tempRenderer = null;
            try {
                tempPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                tempRenderer = new PdfRenderer(tempPfd);
            } catch(Exception e) {
                if (tempPfd != null) try { tempPfd.close(); } catch(Exception ignored) {}
            }
            pfd = tempPfd;
            renderer = tempRenderer;
            int count = renderer == null ? 0 : renderer.getPageCount();
            pageIndex = count == 0 ? 0 : Math.max(0, Math.min(start, count - 1));

            scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    zoom *= d.getScaleFactor();
                    zoom = Math.max(1f, Math.min(5f, zoom));
                    if (zoom <= 1.02f) { zoom = 1f; panX = panY = 0f; }
                    invalidate();
                    return true;
                }
            });

            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }

                @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (zoom > 1.02f) {
                        panX -= dx;
                        panY -= dy;
                        invalidate();
                        return true;
                    }
                    return false;
                }

                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (zoom > 1.02f || Math.abs(vx) < Math.abs(vy) || Math.abs(vx) < 500) return false;
                    if (vx < 0) next(); else previous();
                    return true;
                }

                @Override public boolean onDoubleTap(MotionEvent e) {
                    zoom = zoom > 1.1f ? 1f : 2.5f;
                    if (zoom == 1f) panX = panY = 0f;
                    invalidate();
                    return true;
                }
            });
        }

        int getPageIndex() { return pageIndex; }

        void notifyPage() {
            if (listener != null && renderer != null) listener.onPage(pageIndex, renderer.getPageCount());
        }

        void rerender() {
            postDelayed(this::renderPage, 80);
        }

        private void next() {
            if (renderer != null && pageIndex < renderer.getPageCount() - 1) {
                pageIndex++;
                renderPage();
            }
        }

        private void previous() {
            if (renderer != null && pageIndex > 0) {
                pageIndex--;
                renderPage();
            }
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) renderPage();
        }

        private void renderPage() {
            if (renderer == null || renderer.getPageCount() == 0 || getWidth() <= 0 || getHeight() <= 0) return;
            PdfRenderer.Page page = null;
            try {
                page = renderer.openPage(pageIndex);
                int pw = Math.max(1, page.getWidth());
                int ph = Math.max(1, page.getHeight());
                float target = Math.min(3000f / pw, Math.max(2f, (getWidth() * 2f) / pw));
                int bw = Math.max(1, Math.round(pw * target));
                int bh = Math.max(1, Math.round(ph * target));
                if (bh > 4000) {
                    float k = 4000f / bh;
                    bw = Math.max(1, Math.round(bw * k));
                    bh = 4000;
                }
                Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                bitmap = b;
                zoom = 1f;
                panX = panY = 0f;
                notifyPage();
                invalidate();
            } catch(Exception ignored) {
            } finally {
                if (page != null) page.close();
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null || bitmap.isRecycled()) return;
            float fit = Math.min((float)getWidth() / bitmap.getWidth(), (float)getHeight() / bitmap.getHeight());
            float scale = fit * zoom;
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float left = (getWidth() - w) / 2f + panX;
            float top = (getHeight() - h) / 2f + panY;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            RectF dst = new RectF(left, top, left + w, top + h);
            c.drawBitmap(bitmap, null, dst, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            gestures.onTouchEvent(e);
            return true;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (renderer != null) renderer.close();
            if (pfd != null) try { pfd.close(); } catch(Exception ignored) {}
        }
    }

    static class PdfContinuousList extends ListView {
        private final PdfRenderer renderer;
        private final PageListener listener;
        private final PageAdapter adapter;
        private final ScaleGestureDetector scaler;
        private final int slop;
        private int currentPage,scrollState=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        private boolean pinching,horizontal,closed;
        private float startX,startY,lastX,pendingZoom,focusX,focusY;
        float zoom=1f, pan=0f;
        Runnable onMovement,onZoom;

        PdfContinuousList(Context c,File file,int start,PageListener listener){
            super(c);this.listener=listener;
            setBackgroundColor(Color.rgb(26,26,26));
            setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(26,26,26)));
            setDividerHeight(dpStatic(c,8));
            setPadding(dpStatic(c,6),dpStatic(c,72),dpStatic(c,6),dpStatic(c,56));
            setClipToPadding(false);setCacheColorHint(Color.TRANSPARENT);
            slop=ViewConfiguration.get(c).getScaledTouchSlop();
            ParcelFileDescriptor fd=null;PdfRenderer r=null;
            try{fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);r=new PdfRenderer(fd);}
            catch(Exception e){if(fd!=null)try{fd.close();}catch(Exception ignored){}}
            renderer=r;
            adapter=new PageAdapter(c);setAdapter(adapter);
            int count=adapter.getCount();currentPage=count==0?0:Math.max(0,Math.min(start,count-1));
            setOnScrollListener(new AbsListView.OnScrollListener(){
                @Override public void onScrollStateChanged(AbsListView v,int state){scrollState=state;if(state!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE)movement();}
                @Override public void onScroll(AbsListView v,int first,int visible,int total){
                    if(total<=0||closed)return;
                    currentPage=Math.max(0,Math.min(first,total-1));
                    notifyPage();if(scrollState!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE)movement();
                }
            });
            final int initialPage=currentPage;
            if(initialPage>0)post(()->{if(!closed)setSelection(initialPage);});
            if(count==0)post(()->Toast.makeText(c,"Не удалось открыть PDF",Toast.LENGTH_LONG).show());
            scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScaleBegin(ScaleGestureDetector d){pendingZoom=zoom;focusX=d.getFocusX();focusY=d.getFocusY();return true;}
                @Override public boolean onScale(ScaleGestureDetector d){
                    pendingZoom=clamp(pendingZoom*d.getScaleFactor(),1f,5f);
                    focusX=d.getFocusX();focusY=d.getFocusY();movement();return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector d){changeZoom(pendingZoom,focusX,focusY);}
            });
            scaler.setQuickScaleEnabled(false);
        }
        private void movement(){if(onMovement!=null)onMovement.run();}
        private static int dpStatic(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
        private float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
        private int contentWidth(){return Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());}
        private void clampPan(){pan=clamp(pan,0,Math.max(0,contentWidth()*(zoom-1f)));}
        int getPageIndex(){return currentPage;}
        void notifyPage(){if(listener!=null&&!closed&&renderer!=null&&renderer.getPageCount()>0)listener.onPage(currentPage,renderer.getPageCount());}
        void changeZoom(float requested,float x,float y){
            if(closed)return;
            float next=clamp(requested,1f,5f),old=zoom;
            if(Math.abs(next-old)<0.001f)return;
            int pos=pointToPosition((int)x,(int)y);
            if(pos==INVALID_POSITION)pos=getFirstVisiblePosition();
            View child=getChildAt(pos-getFirstVisiblePosition());
            float fraction=child==null?0f:clamp((y-child.getTop())/Math.max(1f,child.getHeight()),0f,1f);
            float localX=clamp(x-getPaddingLeft(),0,contentWidth());
            pan=(pan+localX)*next/old-localX;
            zoom=next;clampPan();
            int newHeight=child==null?0:Math.round(child.getHeight()*next/old);
            int offset=Math.round(y-fraction*newHeight)-getPaddingTop();
            adapter.notifyDataSetChanged();
            final int anchor=pos, top=offset;
            setSelectionFromTop(anchor,top);
            if(onZoom!=null)onZoom.run();movement();
        }
        void invalidatePages(){
            if(closed)return;
            final int page=currentPage;
            adapter.clearCache();clampPan();adapter.notifyDataSetChanged();
            post(()->{if(!closed)setSelection(page);});
        }
        private void cancelList(MotionEvent e){
            MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);cancel.recycle();
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                pinching=false;horizontal=false;startX=lastX=e.getX();startY=e.getY();
                scaler.onTouchEvent(e);return super.dispatchTouchEvent(e);
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN){
                if(!pinching){cancelList(e);pinching=true;}
            }
            scaler.onTouchEvent(e);
            if(pinching){
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)pinching=false;
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&zoom>1.001f){
                float x=e.getX();
                if(!horizontal&&Math.abs(x-startX)>slop&&Math.abs(x-startX)>Math.abs(e.getY()-startY)*1.3f){
                    cancelList(e);horizontal=true;lastX=startX;
                }
                if(horizontal){
                    pan-=x-lastX;lastX=x;clampPan();
                    for(int i=0;i<getChildCount();i++)getChildAt(i).invalidate();
                    movement();return true;
                }
            }
            if(horizontal){
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)horizontal=false;
                return true;
            }
            return super.dispatchTouchEvent(e);
        }
        class PageTile extends View{
            private Bitmap bitmap;
            private int index;
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            PageTile(Context c){super(c);}
            void bind(int i,Bitmap b){
                index=i;bitmap=b;
                setLayoutParams(new AbsListView.LayoutParams(-1,-2));requestLayout();invalidate();
            }
            @Override protected void onMeasure(int ws,int hs){
                int width=MeasureSpec.getSize(ws);
                float ratio=bitmap==null?1.414f:(float)bitmap.getHeight()/bitmap.getWidth();
                setMeasuredDimension(Math.max(1,width),Math.max(1,Math.round(width*zoom*ratio)));
            }
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);c.drawColor(Color.WHITE);
                if(bitmap!=null&&!bitmap.isRecycled()){
                    float width=getWidth()*zoom;
                    c.drawBitmap(bitmap,null,new RectF(-pan,0,width-pan,getHeight()),paint);
                }else{
                    paint.setColor(Color.DKGRAY);paint.setTextSize(dpStatic(getContext(),15));paint.setTextAlign(Paint.Align.CENTER);
                    c.drawText("Не удалось отобразить страницу "+(index+1),getWidth()/2f,dpStatic(getContext(),40),paint);
                }
            }
        }
        class PageAdapter extends BaseAdapter{
            private final Context context;
            private final LruCache<Integer,Bitmap> cache;
            PageAdapter(Context c){
                context=c;int kb=(int)Math.min(24*1024L,Runtime.getRuntime().maxMemory()/1024L/8L);
                cache=new LruCache<Integer,Bitmap>(Math.max(8*1024,kb)){
                    @Override protected int sizeOf(Integer k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
                };
            }
            @Override public int getCount(){return renderer==null||closed?0:renderer.getPageCount();}
            @Override public Object getItem(int i){return i;}
            @Override public long getItemId(int i){return i;}
            @Override public View getView(int i,View old,ViewGroup parent){
                PageTile tile=old instanceof PageTile?(PageTile)old:new PageTile(context);
                Bitmap b=cache.get(i);if(b==null){b=render(i);if(b!=null)cache.put(i,b);}
                tile.bind(i,b);return tile;
            }
            private Bitmap render(int i){
                if(i<0||i>=getCount())return null;
                try(PdfRenderer.Page page=renderer.openPage(i)){
                    int pw=Math.max(1,page.getWidth()),ph=Math.max(1,page.getHeight());
                    int w=Math.min(2400,Math.max(1000,Math.max(contentWidth(),getResources().getDisplayMetrics().widthPixels)*2));
                    int h=Math.max(1,Math.round(w*(float)ph/pw));
                    double factor=Math.min(1d,Math.min(3600d/h,Math.sqrt(4_000_000d/((double)w*h))));
                    w=Math.max(1,(int)(w*factor));h=Math.max(1,(int)(h*factor));
                    Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    Matrix m=new Matrix();m.setScale((float)w/pw,(float)h/ph);
                    page.render(b,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return b;
                }catch(Exception|OutOfMemoryError ex){android.util.Log.e("PdfViewer","Page "+(i+1),ex);return null;}
            }
            void clearCache(){cache.evictAll();}
        }
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            super.onSizeChanged(w,h,ow,oh);
            if(w>0&&w!=ow){clampPan();if(adapter!=null)post(()->{if(!closed)adapter.notifyDataSetChanged();});}
        }
        @Override protected void onDetachedFromWindow(){
            super.onDetachedFromWindow();
            if(closed)return;closed=true;adapter.clearCache();
            if(renderer!=null)renderer.close();
        }
    }

    static class ImageZoomView extends View {
        private Bitmap bitmap;
        private final ScaleGestureDetector scaler;
        private final GestureDetector gestures;
        private float zoom = 1f;
        private float panX = 0f, panY = 0f;

        ImageZoomView(Context c, File file) {
            super(c);
            setBackgroundColor(Color.BLACK);
            bitmap = decode(file);

            scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    zoom *= d.getScaleFactor();
                    zoom = Math.max(1f, Math.min(6f, zoom));
                    if (zoom <= 1.02f) { zoom = 1f; panX = panY = 0f; }
                    invalidate();
                    return true;
                }
            });

            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (zoom > 1f) {
                        panX -= dx; panY -= dy; invalidate(); return true;
                    }
                    return false;
                }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    zoom = zoom > 1.1f ? 1f : 2.5f;
                    if (zoom == 1f) panX = panY = 0f;
                    invalidate();
                    return true;
                }
            });
        }

        private Bitmap decode(File f) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int max = 4096;
            while (o.outWidth / sample > max || o.outHeight / sample > max) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        }

        void resetForSize() {
            zoom = 1f; panX = panY = 0f; invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null) return;
            float fit = Math.min((float)getWidth() / bitmap.getWidth(), (float)getHeight() / bitmap.getHeight());
            float scale = fit * zoom;
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            RectF dst = new RectF((getWidth()-w)/2f + panX, (getHeight()-h)/2f + panY,
                    (getWidth()+w)/2f + panX, (getHeight()+h)/2f + panY);
            c.drawBitmap(bitmap, null, dst, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            gestures.onTouchEvent(e);
            return true;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
}
