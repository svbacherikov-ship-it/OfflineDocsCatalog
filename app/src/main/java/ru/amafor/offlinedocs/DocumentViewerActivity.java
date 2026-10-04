package ru.amafor.offlinedocs;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
        buildViewer(state == null ? 0 : state.getInt("page", 0));
        immersive();
    }

    private void buildViewer(int startPage) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        String n = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        if (m.equals("application/pdf") || n.endsWith(".pdf")) {
            PdfZoomView pdf = new PdfZoomView(this, file, startPage, (page, total) -> {
                if (pageLabel != null) pageLabel.setText((page + 1) + " / " + total);
            });
            contentView = pdf;
            root.addView(pdf, new FrameLayout.LayoutParams(-1, -1));
        } else if (m.startsWith("image/") || isImageName(n)) {
            ImageZoomView img = new ImageZoomView(this, file);
            contentView = img;
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
            sc.addView(tv, new ScrollView.LayoutParams(-1, -2));
            contentView = sc;
            root.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout top = new LinearLayout(this);
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
        t.setTextSize(16);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1));

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

        if (contentView instanceof PdfZoomView) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, dp(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            p.setMargins(0, 0, 0, dp(12));
            root.addView(pageLabel, p);
            ((PdfZoomView)contentView).notifyPage();
        }

        setContentView(root);
    }

    private TextView topButton(String s, int size) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void share() {
        Uri uri = Uri.parse("content://ru.amafor.offlinedocs.files/" + Uri.encode(stored));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Поделиться файлом"));
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
        if (contentView instanceof ImageZoomView) ((ImageZoomView)contentView).resetForSize();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (contentView instanceof PdfZoomView) out.putInt("page", ((PdfZoomView)contentView).getPageIndex());
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
