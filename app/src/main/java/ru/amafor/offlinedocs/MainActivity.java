package ru.amafor.offlinedocs;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final String PREFS = "catalog";
    private static final String SEC_FAX = "fax";
    private static final String SEC_INS = "instructions";
    private static final String SEC_ORD = "orders";
    private static final String SEC_FORMS = "forms";

    private LinearLayout root;
    private String mode = "home";
    private String currentSection = null;
    private String currentFolderId = null;
    private String currentSearch = "";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        File d = new File(getFilesDir(), "docs");
        if (!d.exists()) d.mkdirs();
        migrateData();
        showHome();
    }

    private boolean dark() {
        int pref = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("theme", 0);
        if (pref == 2) return true;
        if (pref == 1) return false;
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private int bg() { return dark() ? Color.rgb(18,22,27) : Color.rgb(245,247,250); }
    private int surface() { return dark() ? Color.rgb(31,37,44) : Color.WHITE; }
    private int surface2() { return dark() ? Color.rgb(43,50,58) : Color.rgb(234,239,244); }
    private int text() { return dark() ? Color.rgb(239,243,247) : Color.rgb(28,36,44); }
    private int muted() { return dark() ? Color.rgb(174,184,194) : Color.rgb(94,108,121); }
    private int accent() { return dark() ? Color.rgb(83,180,205) : Color.rgb(0,115,150); }
    private int danger() { return Color.rgb(190,55,55); }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void base(String title, boolean back) {
        getWindow().setStatusBarColor(dark() ? Color.rgb(12,15,18) : Color.rgb(0,84,112));
        getWindow().setNavigationBarColor(bg());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(dark() ? Color.rgb(23,30,36) : Color.rgb(0,99,132));

        if (back) {
            TextView b = toolbarButton("‹");
            b.setTextSize(32);
            b.setOnClickListener(v -> goBack());
            bar.addView(b, new LinearLayout.LayoutParams(dp(52), dp(52)));
        }

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        bar.addView(t, new LinearLayout.LayoutParams(0, dp(52), 1));

        TextView search = toolbarButton("⌕");
        search.setTextSize(28);
        search.setContentDescription("Поиск");
        search.setOnClickListener(v -> showSearch(""));
        bar.addView(search, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView theme = toolbarButton("◐");
        theme.setTextSize(24);
        theme.setContentDescription("Тема");
        theme.setOnClickListener(v -> chooseTheme());
        bar.addView(theme, new LinearLayout.LayoutParams(dp(52), dp(52)));

        root.addView(bar);
        setContentView(root);
    }

    private TextView toolbarButton(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(Color.TRANSPARENT, 12));
        return v;
    }

    private void showHome() {
        mode = "home";
        currentSection = null;
        currentFolderId = null;
        base("Офлайн библиотека", false);

        ScrollView sc = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(16), dp(18), dp(16), dp(28));

        TextView heading = new TextView(this);
        heading.setText("Документы всегда под рукой");
        heading.setTextColor(text());
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(heading);

        TextView sub = new TextView(this);
        sub.setText("Локальное хранение · без интернета · быстрый поиск");
        sub.setTextColor(muted());
        sub.setTextSize(14);
        sub.setPadding(0, dp(4), 0, dp(14));
        body.addView(sub);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(12), dp(4), dp(6), dp(4));
        searchBox.setBackground(rounded(surface(), 18));
        EditText q = new EditText(this);
        q.setHint("Поиск по всем документам");
        q.setHintTextColor(muted());
        q.setTextColor(text());
        q.setSingleLine(true);
        q.setBackgroundColor(Color.TRANSPARENT);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.addView(q, new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView go = actionPill("Найти");
        go.setOnClickListener(v -> showSearch(q.getText().toString()));
        q.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showSearch(q.getText().toString());
                return true;
            }
            return false;
        });
        searchBox.addView(go, new LinearLayout.LayoutParams(dp(78), dp(44)));
        body.addView(searchBox, margin(-1, dp(62), 0, 0, 0, dp(18)));

        body.addView(sectionCard("ФАКС", "Факсограммы", "Оперативные документы и сообщения", SEC_FAX));
        body.addView(sectionCard("ИНС", "Инструкции", "Руководства, памятки и инструкции", SEC_INS));
        body.addView(sectionCard("ПР", "Приказы и распоряжения", "Приказы, распоряжения и организационные документы", SEC_ORD));
        body.addView(sectionCard("БЛ", "Бланки", "Формы, шаблоны и рабочие бланки", SEC_FORMS));

        TextView note = new TextView(this);
        note.setText("Импортированные файлы копируются во внутреннюю память приложения и остаются доступными офлайн.");
        note.setTextColor(muted());
        note.setTextSize(13);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        body.addView(note);

        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private View sectionCard(String mark, String title, String subtitle, String section) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface(), 20));
        card.setOnClickListener(v -> showSection(section));

        TextView icon = new TextView(this);
        icon.setText(mark);
        icon.setTextSize(14);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(accent(), 16));
        card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout texts = column();
        texts.setPadding(dp(14), 0, dp(8), 0);
        TextView a = label(title, 18, text(), true);
        TextView b = label(subtitle, 13, muted(), false);
        b.setPadding(0, dp(3), 0, 0);
        texts.addView(a);
        texts.addView(b);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = label("›", 30, accent(), false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(50)));

        LinearLayout.LayoutParams lp = margin(-1, -2, 0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private void showSection(String section) {
        mode = "section";
        currentSection = section;
        currentFolderId = null;
        base(sectionName(section), true);

        ScrollView sc = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView folder = actionPill("＋ Папка");
        folder.setOnClickListener(v -> editFolder(null, null, section));
        actions.addView(folder, new LinearLayout.LayoutParams(0, dp(48), 1));
        Space s = new Space(this);
        actions.addView(s, new LinearLayout.LayoutParams(dp(10), 1));
        TextView file = actionPill("＋ Документы");
        file.setOnClickListener(v -> pickFiles());
        actions.addView(file, new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(actions, margin(-1, dp(48), 0, 0, 0, dp(18)));

        JSONArray fs = folders();
        boolean anyFolder = false;
        for (int i=0; i<fs.length(); i++) {
            JSONObject f = fs.optJSONObject(i);
            if (section.equals(folderSection(f))) {
                anyFolder = true;
                body.addView(folderCard(f));
            }
        }
        if (!anyFolder) body.addView(emptyLine("Папок пока нет. Можно создать собственную структуру."));

        TextView h = label("Документы без папки", 15, muted(), true);
        h.setPadding(dp(4), dp(18), dp(4), dp(8));
        body.addView(h);

        int count = addDocs(body, section, null);
        if (count == 0) body.addView(emptyLine("Документов без папки нет."));

        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private View folderCard(JSONObject f) {
        String id = f.optString("id");
        String name = f.optString("name");
        int count = countDocs(currentSection, id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(rounded(surface(), 18));

        TextView badge = label("▣", 24, accent(), false);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(48)));

        LinearLayout tx = column();
        TextView title = label(name, 17, text(), true);
        TextView sub = label(count + " " + docWord(count), 13, muted(), false);
        tx.addView(title);
        tx.addView(sub);
        card.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));

        TextView menu = label("⋮", 28, muted(), false);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> folderMenu(id, name, currentSection));
        card.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(52)));

        card.setOnClickListener(v -> showFolder(currentSection, id, name));
        card.setOnLongClickListener(v -> { folderMenu(id, name, currentSection); return true; });
        card.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(9)));
        return card;
    }

    private void showFolder(String section, String folderId, String folderName) {
        mode = "folder";
        currentSection = section;
        currentFolderId = folderId;
        base(folderName, true);

        ScrollView sc = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        TextView add = actionPill("＋ Добавить документы в папку");
        add.setGravity(Gravity.CENTER);
        add.setOnClickListener(v -> pickFiles());
        body.addView(add, margin(-1, dp(50), 0, 0, 0, dp(16)));

        int count = addDocs(body, section, folderId);
        if (count == 0) body.addView(emptyLine("Папка пока пустая."));

        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private int addDocs(LinearLayout body, String section, String folderId) {
        JSONArray ds = docs();
        int count = 0;
        for (int i=0; i<ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            String f = d.isNull("folderId") ? null : d.optString("folderId");
            if (section.equals(d.optString("section")) && Objects.equals(folderId, f)) {
                body.addView(docCard(d, false));
                count++;
            }
        }
        return count;
    }

    private View docCard(JSONObject d, boolean showLocation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(11), dp(10), dp(11));
        card.setBackground(rounded(surface(), 16));

        String ext = extension(d.optString("title")).toUpperCase(Locale.ROOT);
        if (ext.length() == 0 || ext.length() > 5) ext = "FILE";
        TextView badge = label(ext, 11, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(accent(), 12));
        card.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(46)));

        LinearLayout tx = column();
        tx.setPadding(dp(12), 0, dp(6), 0);
        TextView title = label(d.optString("title"), 16, text(), true);
        title.setMaxLines(2);
        tx.addView(title);

        String noteText = d.optString("note", "").trim();
        if (noteText.length() > 0) {
            String preview = noteText.replace("\n", " ").replace("\r", " ");
            if (preview.length() > 80) preview = preview.substring(0, 80) + "…";
            TextView note = label("✎ " + preview, 12, accent(), false);
            note.setMaxLines(2);
            note.setPadding(0, dp(3), 0, 0);
            tx.addView(note);
        }

        if (showLocation) {
            String loc = sectionName(d.optString("section"));
            String fn = folderName(d.isNull("folderId") ? null : d.optString("folderId"));
            if (fn != null) loc += "  ›  " + fn;
            TextView sub = label(loc, 12, muted(), false);
            sub.setPadding(0, dp(3), 0, 0);
            tx.addView(sub);
        }
        card.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));

        TextView menu = label("⋮", 28, muted(), false);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> docMenu(d));
        card.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(50)));

        card.setOnClickListener(v -> openDoc(d));
        card.setOnLongClickListener(v -> { docMenu(d); return true; });
        card.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(8)));
        return card;
    }

    private void showSearch(String query) {
        mode = "search";
        currentSearch = query == null ? "" : query.trim();
        base("Поиск", true);

        LinearLayout container = column();
        container.setPadding(dp(14), dp(12), dp(14), 0);

        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), 0, dp(6), 0);
        box.setBackground(rounded(surface(), 16));
        EditText q = new EditText(this);
        q.setText(currentSearch);
        q.setHint("Название, раздел или папка");
        q.setHintTextColor(muted());
        q.setTextColor(text());
        q.setBackgroundColor(Color.TRANSPARENT);
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(q, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView go = actionPill("Найти");
        box.addView(go, new LinearLayout.LayoutParams(dp(78), dp(42)));
        go.setOnClickListener(v -> showSearch(q.getText().toString()));
        q.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showSearch(q.getText().toString());
                return true;
            }
            return false;
        });
        container.addView(box, margin(-1, dp(56), 0, 0, 0, dp(10)));
        root.addView(container);

        ScrollView sc = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(14), dp(4), dp(14), dp(26));

        String needle = currentSearch.toLowerCase(Locale.ROOT);
        JSONArray ds = docs();
        int found = 0;
        for (int i=0; i<ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            String fn = folderName(d.isNull("folderId") ? null : d.optString("folderId"));
            String hay = (d.optString("title") + " " + sectionName(d.optString("section")) + " " + (fn == null ? "" : fn) + " " + d.optString("note", "")).toLowerCase(Locale.ROOT);
            if (needle.length() == 0 || hay.contains(needle)) {
                body.addView(docCard(d, true));
                found++;
            }
        }
        if (found == 0) body.addView(emptyLine("Ничего не найдено."));
        else {
            TextView n = label("Найдено: " + found, 13, muted(), false);
            n.setPadding(dp(4), 0, dp(4), dp(8));
            body.addView(n, 0);
        }

        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void pickFiles() {
        if (currentSection == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
        int ok = 0;
        try {
            if (data.getClipData() != null) {
                for (int i=0; i<data.getClipData().getItemCount(); i++) {
                    importUri(data.getClipData().getItemAt(i).getUri());
                    ok++;
                }
            } else if (data.getData() != null) {
                importUri(data.getData());
                ok++;
            }
            Toast.makeText(this, "Добавлено файлов: " + ok, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка импорта: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refreshCurrent();
    }

    private void importUri(Uri uri) throws Exception {
        String display = displayName(uri);
        String ext = "";
        int dot = display.lastIndexOf('.');
        if (dot >= 0 && dot < display.length()-1) ext = display.substring(dot);
        String stored = UUID.randomUUID().toString() + ext;
        File out = new File(new File(getFilesDir(), "docs"), stored);
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Не удалось открыть файл");
        try (InputStream input = in; OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = input.read(buf)) > 0) os.write(buf, 0, n);
        }

        JSONObject d = new JSONObject();
        d.put("id", UUID.randomUUID().toString());
        d.put("title", display);
        d.put("stored", stored);
        String mime = getContentResolver().getType(uri);
        d.put("mime", mime == null ? mimeFromName(display) : mime);
        d.put("section", currentSection);
        if (currentFolderId == null) d.put("folderId", JSONObject.NULL);
        else d.put("folderId", currentFolderId);

        JSONArray arr = docs();
        arr.put(d);
        saveArray("docs", arr);
    }

    private String displayName(Uri uri) {
        Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (c != null) {
            try { if (c.moveToFirst()) return c.getString(0); }
            finally { c.close(); }
        }
        String s = uri.getLastPathSegment();
        return s == null ? "document" : s;
    }

    private void openDoc(JSONObject d) {
        String title = d.optString("title");
        String mime = d.optString("mime", mimeFromName(title));
        if (DocumentViewerActivity.supports(title, mime)) {
            Intent i = new Intent(this, DocumentViewerActivity.class);
            i.putExtra("stored", d.optString("stored"));
            i.putExtra("title", title);
            i.putExtra("mime", mime);
            startActivity(i);
            return;
        }

        try {
            Uri uri = Uri.parse("content://" + getPackageName() + ".files/" + Uri.encode(d.optString("stored")));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setTitle("Формат пока не поддерживается")
                .setMessage("Встроенный просмотр доступен для PDF, изображений и текстовых файлов. Для этого формата не найдено внешнее приложение.")
                .setPositiveButton("OK", null).show();
        }
    }

    private void shareDoc(JSONObject d) {
        Uri uri = Uri.parse("content://" + getPackageName() + ".files/" + Uri.encode(d.optString("stored")));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(d.optString("mime", mimeFromName(d.optString("title"))));
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Поделиться файлом"));
    }

    private void docMenu(JSONObject d) {
        String[] items = {"Открыть", "Поделиться", "Заметка", "Переместить", "Переименовать", "Удалить"};
        new AlertDialog.Builder(this).setTitle(d.optString("title")).setItems(items, (x, w) -> {
            if (w == 0) openDoc(d);
            else if (w == 1) shareDoc(d);
            else if (w == 2) editNote(d);
            else if (w == 3) moveDoc(d);
            else if (w == 4) renameDoc(d);
            else deleteDoc(d);
        }).show();
    }

    private void editNote(JSONObject d) {
        EditText e = new EditText(this);
        e.setText(d.optString("note", ""));
        e.setHint("Введите заметку к документу");
        e.setMinLines(6);
        e.setMaxLines(12);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = dp(18);
        e.setPadding(pad, dp(10), pad, dp(10));

        ScrollView wrap = new ScrollView(this);
        wrap.setFillViewport(true);
        wrap.addView(e, new ScrollView.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
            .setTitle("Заметка: " + d.optString("title"))
            .setView(wrap)
            .setPositiveButton("Сохранить", (dialog, which) -> {
                saveDocNote(d.optString("id"), e.getText().toString());
                refreshCurrent();
            })
            .setNeutralButton("Очистить", (dialog, which) -> {
                saveDocNote(d.optString("id"), "");
                refreshCurrent();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void saveDocNote(String docId, String note) {
        JSONArray a = docs();
        for (int i=0; i<a.length(); i++) {
            JSONObject item = a.optJSONObject(i);
            if (docId.equals(item.optString("id"))) {
                try { item.put("note", note == null ? "" : note.trim()); }
                catch(Exception ignored) {}
                break;
            }
        }
        saveArray("docs", a);
    }

    private void moveDoc(JSONObject d) {
        String[] sections = {"Факсограммы", "Инструкции", "Приказы и распоряжения", "Бланки"};
        String[] values = {SEC_FAX, SEC_INS, SEC_ORD, SEC_FORMS};

        new AlertDialog.Builder(this)
            .setTitle("Переместить в раздел")
            .setItems(sections, (dialog, which) -> chooseMoveFolder(d, values[which]))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void chooseMoveFolder(JSONObject d, String section) {
        JSONArray fs = folders();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();

        names.add("Без папки");
        ids.add(null);

        for (int i=0; i<fs.length(); i++) {
            JSONObject f = fs.optJSONObject(i);
            if (section.equals(folderSection(f))) {
                names.add(f.optString("name"));
                ids.add(f.optString("id"));
            }
        }

        new AlertDialog.Builder(this)
            .setTitle(sectionName(section) + ": выберите папку")
            .setItems(names.toArray(new String[0]), (dialog, which) -> {
                moveDocTo(d.optString("id"), section, ids.get(which));
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void moveDocTo(String docId, String section, String folderId) {
        JSONArray a = docs();
        String title = "Документ";

        for (int i=0; i<a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            if (docId.equals(d.optString("id"))) {
                title = d.optString("title", "Документ");
                try {
                    d.put("section", section);
                    if (folderId == null) d.put("folderId", JSONObject.NULL);
                    else d.put("folderId", folderId);
                } catch(Exception ignored) {}
                break;
            }
        }

        saveArray("docs", a);

        String place = sectionName(section);
        String fn = folderName(folderId);
        if (fn != null) place += " / " + fn;
        Toast.makeText(this, title + " → " + place, Toast.LENGTH_SHORT).show();
        refreshCurrent();
    }

    private void renameDoc(JSONObject d) {
        EditText e = new EditText(this);
        e.setText(d.optString("title"));
        e.selectAll();
        new AlertDialog.Builder(this).setTitle("Переименовать документ").setView(e)
            .setPositiveButton("Сохранить", (x, w) -> {
                String name = e.getText().toString().trim();
                if (name.length() == 0) return;
                updateDocTitle(d.optString("id"), name);
                refreshCurrent();
            }).setNegativeButton("Отмена", null).show();
    }

    private void updateDocTitle(String id, String name) {
        JSONArray a = docs();
        for (int i=0; i<a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            if (id.equals(d.optString("id"))) {
                try { d.put("title", name); } catch(Exception ignored) {}
                break;
            }
        }
        saveArray("docs", a);
    }

    private void deleteDoc(JSONObject target) {
        new AlertDialog.Builder(this).setTitle("Удалить документ?")
            .setMessage(target.optString("title"))
            .setPositiveButton("Удалить", (x, w) -> {
                JSONArray src = docs(), out = new JSONArray();
                for (int i=0; i<src.length(); i++) {
                    JSONObject d = src.optJSONObject(i);
                    if (!d.optString("id").equals(target.optString("id"))) out.put(d);
                }
                new File(new File(getFilesDir(), "docs"), target.optString("stored")).delete();
                saveArray("docs", out);
                refreshCurrent();
            }).setNegativeButton("Отмена", null).show();
    }

    private void editFolder(String id, String oldName, String section) {
        EditText e = new EditText(this);
        if (oldName != null) { e.setText(oldName); e.selectAll(); }
        new AlertDialog.Builder(this).setTitle(id == null ? "Новая папка" : "Переименовать папку").setView(e)
            .setPositiveButton("Сохранить", (x, w) -> {
                String name = e.getText().toString().trim();
                if (name.length() == 0) return;
                JSONArray a = folders();
                try {
                    if (id == null) {
                        JSONObject f = new JSONObject();
                        f.put("id", UUID.randomUUID().toString());
                        f.put("name", name);
                        f.put("section", section);
                        a.put(f);
                    } else {
                        for (int i=0; i<a.length(); i++) {
                            JSONObject f = a.optJSONObject(i);
                            if (id.equals(f.optString("id"))) f.put("name", name);
                        }
                    }
                } catch(Exception ignored) {}
                saveArray("folders", a);
                if ("folder".equals(mode)) showFolder(section, id, name);
                else showSection(section);
            }).setNegativeButton("Отмена", null).show();
    }

    private void folderMenu(String id, String name, String section) {
        new AlertDialog.Builder(this).setTitle(name)
            .setItems(new String[]{"Открыть", "Переименовать", "Удалить"}, (x, w) -> {
                if (w == 0) showFolder(section, id, name);
                else if (w == 1) editFolder(id, name, section);
                else deleteFolder(id, section);
            }).show();
    }

    private void deleteFolder(String id, String section) {
        JSONArray ds = docs();
        for (int i=0; i<ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            if (id.equals(d.optString("folderId"))) {
                Toast.makeText(this, "Сначала удалите документы из папки", Toast.LENGTH_LONG).show();
                return;
            }
        }
        new AlertDialog.Builder(this).setTitle("Удалить пустую папку?")
            .setPositiveButton("Удалить", (x, w) -> {
                JSONArray src = folders(), out = new JSONArray();
                for (int i=0; i<src.length(); i++) {
                    JSONObject f = src.optJSONObject(i);
                    if (!id.equals(f.optString("id"))) out.put(f);
                }
                saveArray("folders", out);
                showSection(section);
            }).setNegativeButton("Отмена", null).show();
    }

    private void chooseTheme() {
        int selected = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("theme", 0);
        String[] items = {"Системная", "Светлая", "Тёмная"};
        new AlertDialog.Builder(this).setTitle("Тема приложения")
            .setSingleChoiceItems(items, selected, (d, which) -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("theme", which).apply();
                d.dismiss();
                refreshCurrent();
            }).setNegativeButton("Отмена", null).show();
    }

    private void migrateData() {
        JSONArray fs = folders();
        boolean changed = false;
        for (int i=0; i<fs.length(); i++) {
            JSONObject f = fs.optJSONObject(i);
            if (!f.has("section") || f.optString("section").length() == 0) {
                try { f.put("section", SEC_INS); changed = true; } catch(Exception ignored) {}
            }
        }
        if (changed) saveArray("folders", fs);

        JSONArray ds = docs();
        changed = false;
        for (int i=0; i<ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            if (!d.has("section") || d.optString("section").length() == 0) {
                try { d.put("section", d.isNull("folderId") ? SEC_FAX : SEC_INS); changed = true; } catch(Exception ignored) {}
            }
        }
        if (changed) saveArray("docs", ds);
    }

    private String folderSection(JSONObject f) {
        String s = f.optString("section");
        return s.length() == 0 ? SEC_INS : s;
    }

    private int countDocs(String section, String folderId) {
        JSONArray a = docs();
        int n = 0;
        for (int i=0; i<a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            String f = d.isNull("folderId") ? null : d.optString("folderId");
            if (section.equals(d.optString("section")) && Objects.equals(folderId, f)) n++;
        }
        return n;
    }

    private String folderName(String id) {
        if (id == null || id.length() == 0) return null;
        JSONArray a = folders();
        for (int i=0; i<a.length(); i++) {
            JSONObject f = a.optJSONObject(i);
            if (id.equals(f.optString("id"))) return f.optString("name");
        }
        return null;
    }

    private String docWord(int n) {
        int n10 = n % 10, n100 = n % 100;
        if (n10 == 1 && n100 != 11) return "документ";
        if (n10 >= 2 && n10 <= 4 && !(n100 >= 12 && n100 <= 14)) return "документа";
        return "документов";
    }

    private String sectionName(String s) {
        if (SEC_FAX.equals(s)) return "Факсограммы";
        if (SEC_ORD.equals(s)) return "Приказы и распоряжения";
        if (SEC_FORMS.equals(s)) return "Бланки";
        return "Инструкции";
    }

    private String extension(String name) {
        int p = name.lastIndexOf('.');
        return p >= 0 && p < name.length()-1 ? name.substring(p+1) : "";
    }

    private String mimeFromName(String name) {
        String e = extension(name).toLowerCase(Locale.ROOT);
        if ("pdf".equals(e)) return "application/pdf";
        if ("png".equals(e)) return "image/png";
        if ("jpg".equals(e) || "jpeg".equals(e)) return "image/jpeg";
        if ("webp".equals(e)) return "image/webp";
        if ("txt".equals(e) || "log".equals(e) || "csv".equals(e) || "xml".equals(e) || "json".equals(e)) return "text/plain";
        return "application/octet-stream";
    }

    private JSONArray folders() { return readArray("folders"); }
    private JSONArray docs() { return readArray("docs"); }

    private JSONArray readArray(String key) {
        String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "[]");
        try { return new JSONArray(s); } catch(Exception e) { return new JSONArray(); }
    }

    private void saveArray(String key, JSONArray a) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, a.toString()).apply();
    }

    private void refreshCurrent() {
        if ("home".equals(mode)) showHome();
        else if ("section".equals(mode)) showSection(currentSection);
        else if ("folder".equals(mode)) {
            String n = folderName(currentFolderId);
            showFolder(currentSection, currentFolderId, n == null ? "Папка" : n);
        } else if ("search".equals(mode)) showSearch(currentSearch);
        else showHome();
    }

    private void goBack() {
        if ("home".equals(mode)) { finish(); return; }
        if ("folder".equals(mode)) { showSection(currentSection); return; }
        showHome();
    }

    @Override public void onBackPressed() { goBack(); }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView actionPill(String s) {
        TextView v = label(s, 15, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(accent(), 15));
        v.setPadding(dp(10), 0, dp(10), 0);
        return v;
    }

    private TextView emptyLine(String s) {
        TextView v = label(s, 14, muted(), false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(18), dp(12), dp(18));
        v.setBackground(rounded(surface2(), 14));
        v.setLayoutParams(margin(-1, -2, 0, 0, 0, dp(8)));
        return v;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams margin(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
