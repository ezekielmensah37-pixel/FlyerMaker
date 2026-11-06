package com.directormonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static class L implements Cloneable {
        int id, type, color = 0xff111111, align = 1, op = 255, br = 0, ct = 100, sat = 100;
        String txt = "Your text", font = "sans-serif", uri;
        float x, y, w, h, size = 120, rot;
        boolean bold, vis = true;
        Bitmap bmp;
        L copy() { try { return (L) clone(); } catch (CloneNotSupportedException e) { return null; } }
    }
    static class Snap { ArrayList<L> ls = new ArrayList<>(); int bg, W, H; }
    interface Cb { void on(int v); }

    int W = 1000, H = 1414, bg = 0xffffffff, tol = 35, tool = 0, brushSize = 30, brushColor = 0xff111111, nextId = 1;
    boolean busy;
    ArrayList<L> ls = new ArrayList<>();
    ArrayList<Snap> ul = new ArrayList<>(), rl = new ArrayList<>();
    L sel;
    LinkedHashMap<String, Typeface> fonts = new LinkedHashMap<>();
    FlyerView fv;
    LinearLayout layersBox, propsBox, optsBox, toolbox;
    TextView stat;
    final int[] PAL = {0xff000000, 0xffffffff, 0xffd90429, 0xffff6b6b, 0xffff9f1c, 0xffffd43b,
            0xff2ec4b6, 0xff11998e, 0xff1e90ff, 0xff0b3d91, 0xff6a11cb, 0xffff4fa3,
            0xff8d5524, 0xff6d6d6d, 0xffeeeeee, 0xff111827};

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    Button btn(String t, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(t); b.setAllCaps(false); b.setTextColor(0xffdddddd); b.setTextSize(13);
        b.setBackgroundColor(0xff3c3c3c); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(6), dp(8), dp(6)); b.setOnClickListener(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(p);
        return b;
    }
    Button fill(Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(p);
        return b;
    }
    TextView tv(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xffcccccc); t.setPadding(dp(8), dp(6), dp(8), dp(6));
        return t;
    }
    LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        for (View v : vs) r.addView(v);
        return r;
    }
    SeekBar sb(int max, int val, final boolean u, final Cb cb) {
        SeekBar s = new SeekBar(this);
        s.setMax(max); s.setProgress(val);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean f) { if (f) cb.on(p); }
            public void onStartTrackingTouch(SeekBar b) { if (u) push(); }
            public void onStopTrackingTouch(SeekBar b) {}
        });
        return s;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadFonts();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff1e1e1e);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(0xff323232);
        LinearLayout top = new LinearLayout(this);
        hs.addView(top);
        for (String m : new String[]{"File", "Edit", "Layer", "Image"}) {
            final String mm = m;
            top.addView(btn(m, v -> menu(mm)));
        }
        top.addView(btn("↶ Undo", v -> undo()));
        top.addView(btn("↷ Redo", v -> redo()));
        root.addView(hs);

        HorizontalScrollView hs3 = new HorizontalScrollView(this);
        hs3.setBackgroundColor(0xff383838);
        optsBox = new LinearLayout(this);
        optsBox.setGravity(Gravity.CENTER_VERTICAL);
        hs3.addView(optsBox);
        root.addView(hs3);

        LinearLayout mid = new LinearLayout(this);
        toolbox = new LinearLayout(this);
        toolbox.setOrientation(LinearLayout.VERTICAL);
        toolbox.setBackgroundColor(0xff2b2b2b);
        mid.addView(toolbox, new LinearLayout.LayoutParams(dp(64), -1));
        fv = new FlyerView(this);
        mid.addView(fv, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout rp = new LinearLayout(this);
        rp.setOrientation(LinearLayout.VERTICAL);
        rp.setBackgroundColor(0xff2b2b2b);
        rp.addView(tv("LAYERS"));
        ScrollView sv = new ScrollView(this);
        layersBox = new LinearLayout(this);
        layersBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(layersBox);
        rp.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        rp.addView(row(btn("▲", v -> move(1)), btn("▼", v -> move(-1)),
                btn("Copy", v -> dup()), btn("Delete", v -> del())));
        rp.addView(tv("PROPERTIES"));
        ScrollView sv2 = new ScrollView(this);
        propsBox = new LinearLayout(this);
        propsBox.setOrientation(LinearLayout.VERTICAL);
        sv2.addView(propsBox);
        rp.addView(sv2, new LinearLayout.LayoutParams(-1, 0, 1.4f));
        mid.addView(rp, new LinearLayout.LayoutParams(dp(240), -1));
        root.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setBackgroundColor(0xff323232);
        LinearLayout al = new LinearLayout(this);
        al.setGravity(Gravity.CENTER_VERTICAL);
        hs2.addView(al);
        String[] an = {"⇤ Left", "↔ Centre", "⇥ Right", "⤒ Top", "↕ Middle", "⤓ Bottom"};
        for (int i = 0; i < 6; i++) {
            final int k = i;
            al.addView(btn(an[i], v -> align(k)));
        }
        stat = tv("");
        al.addView(stat);
        root.addView(hs2);
        setContentView(root);
        buildTools();
        buildOpts();
        addText();
        ul.clear();
        status();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (e.isCtrlPressed() && k == KeyEvent.KEYCODE_Z) { undo(); return true; }
        if (e.isCtrlPressed() && k == KeyEvent.KEYCODE_Y) { redo(); return true; }
        if (sel == null) return super.onKeyDown(k, e);
        float st = e.isShiftPressed() ? 25 : 5;
        if (k == KeyEvent.KEYCODE_DEL || k == KeyEvent.KEYCODE_FORWARD_DEL) { del(); return true; }
        if (k == KeyEvent.KEYCODE_D && e.isCtrlPressed()) { dup(); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) sel.x -= st;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) sel.x += st;
        else if (k == KeyEvent.KEYCODE_DPAD_UP) sel.y -= st;
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) sel.y += st;
        else return super.onKeyDown(k, e);
        fv.invalidate();
        return true;
    }

    void status() { stat.setText("   " + Math.round(fv.sc * 100) + "%  ·  " + W + " × " + H); }

    /* ---------- fonts ---------- */
    void loadFonts() {
        fonts.clear();
        for (String n : new String[]{"sans-serif", "sans-serif-condensed", "sans-serif-light",
                "sans-serif-medium", "serif", "monospace", "cursive", "casual"})
            fonts.put(n, Typeface.create(n, Typeface.NORMAL));
        File[] fs = new File(getFilesDir(), "fonts").listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                try { fonts.put(f.getName().replaceAll("\\.[^.]+$", ""), Typeface.createFromFile(f)); }
                catch (Exception e) {}
            }
        }
    }

    /* ---------- undo / redo ---------- */
    Snap snap() {
        Snap s = new Snap();
        for (L l : ls) s.ls.add(l.copy());
        s.bg = bg; s.W = W; s.H = H;
        return s;
    }
    void push() {
        ul.add(snap()); rl.clear();
        if (ul.size() > 25) ul.remove(0);
        for (int i = 0; i < ul.size() - 3; i++)
            for (L c : ul.get(i).ls) if (c.type >= 2) c.bmp = null;
    }
    void restore(Snap s) {
        HashMap<Integer, Bitmap> cur = new HashMap<>();
        for (L l : ls) if (l.bmp != null) cur.put(l.id, l.bmp);
        int sid = sel == null ? -1 : sel.id;
        ls = new ArrayList<>(); sel = null;
        for (L c : s.ls) {
            L l = c.copy();
            if (l.type >= 2 && l.bmp == null) l.bmp = cur.get(l.id);
            if (l.type >= 2 && l.bmp == null) continue;
            ls.add(l);
            if (l.id == sid) sel = l;
        }
        bg = s.bg; W = s.W; H = s.H;
        refresh(); status();
    }
    void undo() { if (ul.isEmpty()) return; rl.add(snap()); restore(ul.remove(ul.size() - 1)); }
    void redo() { if (rl.isEmpty()) return; ul.add(snap()); restore(rl.remove(rl.size() - 1)); }
    void cow(L l) {
        l.bmp = l.bmp.copy(Bitmap.Config.ARGB_8888, true);
        l.bmp.setHasAlpha(true);
    }

    /* ---------- panels ---------- */
    void refresh() { buildLayers(); buildProps(); if (fv != null) fv.invalidate(); }

    void buildTools() {
        toolbox.removeAllViews();
        String[] n = {"Move", "Brush", "Erase", "Cut"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button b = fill(btn(n[i], v -> { tool = k; buildTools(); buildOpts(); }));
            b.setBackgroundColor(tool == k ? 0xff0d5fbf : 0xff3c3c3c);
            toolbox.addView(b);
        }
        toolbox.addView(fill(btn("Text", v -> addText())));
        toolbox.addView(fill(btn("Shape", v -> addShape())));
        toolbox.addView(fill(btn("Photo", v -> pickPhoto())));
    }

    void buildOpts() {
        optsBox.removeAllViews();
        if (tool == 1 || tool == 2) {
            optsBox.addView(tv(tool == 1 ? "Brush size" : "Eraser size"));
            optsBox.addView(sb(200, brushSize, false, v -> brushSize = Math.max(1, v)), new LinearLayout.LayoutParams(dp(160), -2));
            if (tool == 1) optsBox.addView(btn("Colour", v -> pickColor(c -> brushColor = c)));
        } else if (tool == 3) {
            optsBox.addView(btn("Auto remove background", v -> {
                if (sel != null && sel.type == 2) autoCut(sel); else toast("Select a photo layer first");
            }));
            optsBox.addView(tv("Tolerance"));
            optsBox.addView(sb(120, tol, false, v -> tol = v), new LinearLayout.LayoutParams(dp(160), -2));
            optsBox.addView(tv("Or tap the background of the photo"));
        } else optsBox.addView(tv("Drag = move   ·   square = resize   ·   dot = rotate   ·   pinch = zoom"));
    }

    void buildLayers() {
        layersBox.removeAllViews();
        for (int i = ls.size() - 1; i >= 0; i--) {
            final L l = ls.get(i);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackgroundColor(l == sel ? 0xff0d5fbf : 0xff333333);
            View th;
            if (l.type >= 2) { ImageView iv = new ImageView(this); iv.setImageBitmap(l.bmp); iv.setBackgroundColor(0xff777777); th = iv; }
            else if (l.type == 1) { th = new View(this); th.setBackgroundColor(l.color); }
            else { TextView t = tv("T"); t.setBackgroundColor(0xff555555); th = t; }
            r.addView(th, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView t = tv(l.type == 0 ? l.txt.replace("\n", " ") : l.type == 1 ? "Shape" : l.type == 2 ? "Photo" : "Paint");
            t.setSingleLine(true);
            t.setOnClickListener(v -> { sel = l; refresh(); });
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(btn(l.vis ? "●" : "○", v -> { l.vis = !l.vis; refresh(); }));
            layersBox.addView(r);
        }
    }

    void buildProps() {
        propsBox.removeAllViews();
        final L l = sel;
        if (l == null) { propsBox.addView(tv("Select a layer")); return; }
        propsBox.addView(tv("Opacity"));
        propsBox.addView(sb(255, l.op, true, v -> { l.op = v; fv.invalidate(); }));
        if (l.type == 0) {
            propsBox.addView(btn("Edit text…", v -> editText(l)));
            propsBox.addView(btn("Font: " + l.font, v -> pickFont(l)));
            propsBox.addView(tv("Size"));
            propsBox.addView(sb(400, (int) l.size, true, v -> { l.size = Math.max(10, v); fv.invalidate(); }));
            propsBox.addView(row(btn("Bold", v -> { push(); l.bold = !l.bold; fv.invalidate(); }),
                    btn("L", v -> { push(); l.align = 0; fv.invalidate(); }),
                    btn("C", v -> { push(); l.align = 1; fv.invalidate(); }),
                    btn("R", v -> { push(); l.align = 2; fv.invalidate(); })));
            propsBox.addView(btn("Colour", v -> pickColor(c -> { push(); l.color = c; fv.invalidate(); })));
        } else if (l.type == 1) {
            propsBox.addView(tv("Width"));
            propsBox.addView(sb(2 * W, (int) l.w, true, v -> { l.w = Math.max(20, v); fv.invalidate(); }));
            propsBox.addView(tv("Height"));
            propsBox.addView(sb(2 * H, (int) l.h, true, v -> { l.h = Math.max(4, v); fv.invalidate(); }));
            propsBox.addView(btn("Colour", v -> pickColor(c -> { push(); l.color = c; refresh(); })));
        } else if (l.type == 2) {
            propsBox.addView(tv("Width"));
            propsBox.addView(sb(2 * W, (int) l.w, true, v -> {
                l.w = Math.max(20, v); l.h = l.w * l.bmp.getHeight() / l.bmp.getWidth(); fv.invalidate();
            }));
            propsBox.addView(tv("Brightness"));
            propsBox.addView(sb(200, l.br + 100, true, v -> { l.br = v - 100; fv.invalidate(); }));
            propsBox.addView(tv("Contrast"));
            propsBox.addView(sb(200, l.ct, true, v -> { l.ct = v; fv.invalidate(); }));
            propsBox.addView(tv("Saturation"));
            propsBox.addView(sb(200, l.sat, true, v -> { l.sat = v; fv.invalidate(); }));
            propsBox.addView(btn("Restore original photo", v -> restoreOrig(l)));
        }
    }

    /* ---------- background remover ---------- */
    int dist(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)),
                Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)), Math.abs((a & 255) - (b & 255))));
    }

    void wand(Bitmap b, int[][] seeds, int tol) {
        int w = b.getWidth(), h = b.getHeight(), n = w * h;
        int[] px = new int[n];
        b.getPixels(px, 0, w, 0, 0, w, h);
        boolean[] cut = new boolean[n];
        int[] st = new int[n];
        for (int[] s : seeds) {
            int si = s[1] * w + s[0];
            if (cut[si] || (px[si] >>> 24) == 0) continue;
            int sc = px[si], sp = 0;
            st[sp++] = si; cut[si] = true;
            while (sp > 0) {
                int i = st[--sp], x = i % w, y = i / w;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? -1 : k == 1 ? 1 : 0), ny = y + (k == 2 ? -1 : k == 3 ? 1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (!cut[j] && ((px[j] >>> 24) == 0 || dist(px[j], sc) <= tol)) { cut[j] = true; st[sp++] = j; }
                }
            }
        }
        byte[] ra = new byte[n];
        for (int i = 0; i < n; i++) {
            if (cut[i] || (px[i] >>> 24) == 0) continue;
            int x = i % w, y = i / w;
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? -1 : k == 1 ? 1 : 0), ny = y + (k == 2 ? -1 : k == 3 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int j = ny * w + nx;
                if (!cut[j]) continue;
                int a = (px[j] >>> 24) == 0 ? 255 : Math.min(255, (int) (255f * dist(px[i], px[j]) / (tol * 0.9f + 1)));
                ra[i] = (byte) Math.max(1, a);
                break;
            }
        }
        for (int i = 0; i < n; i++) {
            if (cut[i]) px[i] = 0;
            else {
                int a = ra[i] & 255;
                if (a > 0) px[i] = (px[i] & 0x00ffffff) | (Math.min(px[i] >>> 24, a) << 24);
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    void runCut(final L l, final int[][] seeds) {
        toast("Removing…");
        final int t = tol;
        final Bitmap target = l.bmp;
        busy = true;
        new Thread(() -> {
            wand(target, seeds, t);
            runOnUiThread(() -> { busy = false; refresh(); });
        }).start();
    }
    void autoCut(L l) {
        if (busy) return;
        push(); cow(l);
        int w = l.bmp.getWidth(), h = l.bmp.getHeight();
        runCut(l, new int[][]{{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1},
                {w / 2, 0}, {w / 2, h - 1}, {0, h / 2}, {w - 1, h / 2}});
    }
    void restoreOrig(L l) {
        if (l == null || l.uri == null) return;
        try {
            Bitmap b = decode(Uri.parse(l.uri));
            if (b == null) return;
            push(); l.bmp = b; refresh();
        } catch (Exception e) { toast("Could not reload the photo"); }
    }

    /* ---------- dialogs ---------- */
    void editText(final L l) {
        final EditText e = new EditText(this);
        e.setText(l.txt); e.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("Text").setView(e)
                .setPositiveButton("OK", (d, i) -> { push(); l.txt = e.getText().toString(); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    void pickFont(final L l) {
        final String[] names = fonts.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Font")
                .setItems(names, (d, i) -> { push(); l.font = names[i]; refresh(); }).show();
    }

    void pickColor(final Cb cb) {
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 4; r++) {
            LinearLayout rw = new LinearLayout(this);
            for (int c = 0; c < 4; c++) {
                final int col = PAL[r * 4 + c];
                Button b = new Button(this);
                b.setBackgroundColor(col);
                b.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
                b.setOnClickListener(v -> { cb.on(col); dlg[0].dismiss(); });
                rw.addView(b);
            }
            g.addView(rw);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle("Colour").setView(g).show();
    }

    void menu(String t) {
        final String[] it = t.equals("File") ? new String[]{"Add photo", "Add fonts", "Export PNG"}
                : t.equals("Edit") ? new String[]{"Undo", "Redo", "Copy layer", "Delete layer", "Fit view"}
                : t.equals("Layer") ? new String[]{"New text", "New shape", "New paint layer", "Move up", "Move down"}
                : new String[]{"Canvas: A4", "Canvas: Post 4:5", "Canvas: Square", "Canvas: Story 9:16", "Background colour", "Restore original photo"};
        new AlertDialog.Builder(this).setTitle(t).setItems(it, (d, i) -> act(it[i])).show();
    }

    void act(String s) {
        switch (s) {
            case "Add photo": pickPhoto(); break;
            case "Add fonts": pickFonts(); break;
            case "Export PNG": {
                Intent t = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                t.addCategory(Intent.CATEGORY_OPENABLE);
                t.setType("image/png");
                t.putExtra(Intent.EXTRA_TITLE, "flyer.png");
                startActivityForResult(t, 3);
                break;
            }
            case "Undo": undo(); break;
            case "Redo": redo(); break;
            case "Copy layer": dup(); break;
            case "Delete layer": del(); break;
            case "Fit view": fv.zoom = 1; fv.panX = 0; fv.panY = 0; fv.invalidate(); status(); break;
            case "New text": addText(); break;
            case "New shape": addShape(); break;
            case "New paint layer": push(); newPaint(); break;
            case "Move up": move(1); break;
            case "Move down": move(-1); break;
            case "Canvas: A4": setSize(1000, 1414); break;
            case "Canvas: Post 4:5": setSize(1080, 1350); break;
            case "Canvas: Square": setSize(1080, 1080); break;
            case "Canvas: Story 9:16": setSize(1080, 1920); break;
            case "Background colour": pickColor(c -> { push(); bg = c; fv.invalidate(); }); break;
            case "Restore original photo": restoreOrig(sel); break;
        }
    }

    void setSize(int nw, int nh) {
        push();
        float kx = nw / (float) W, ky = nh / (float) H;
        for (L l : ls) { l.x *= kx; l.y *= ky; }
        W = nw; H = nh;
        fv.invalidate(); status();
    }

    /* ---------- layers ---------- */
    void add(L l) { l.id = nextId++; l.x = W / 2f; l.y = H / 2f; ls.add(l); sel = l; refresh(); }
    void addText() { push(); add(new L()); }
    void addShape() {
        push();
        L l = new L(); l.type = 1; l.w = W * .6f; l.h = W * .2f; l.color = 0xffffd43b;
        add(l);
    }
    void newPaint() {
        L l = new L(); l.type = 3; l.bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        l.w = W; l.h = H;
        add(l);
    }
    void move(int dir) {
        int i = ls.indexOf(sel), j = i + dir;
        if (sel == null || j < 0 || j >= ls.size()) return;
        push(); Collections.swap(ls, i, j); refresh();
    }
    void dup() {
        if (sel == null) return;
        push();
        L n = sel.copy();
        n.id = nextId++; n.x += 30; n.y += 30;
        if (sel.bmp != null) { n.bmp = sel.bmp.copy(Bitmap.Config.ARGB_8888, true); n.bmp.setHasAlpha(true); }
        ls.add(ls.indexOf(sel) + 1, n); sel = n; refresh();
    }
    void del() { if (sel == null) return; push(); ls.remove(sel); sel = null; refresh(); }

    void align(int k) {
        if (sel == null) return;
        push();
        fv.meas(sel);
        float hw = sel.w / 2, hh = sel.h / 2;
        if (k == 0) sel.x = hw;
        if (k == 1) sel.x = W / 2f;
        if (k == 2) sel.x = W - hw;
        if (k == 3) sel.y = hh;
        if (k == 4) sel.y = H / 2f;
        if (k == 5) sel.y = H - hh;
        fv.invalidate();
    }

    /* ---------- files ---------- */
    void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        startActivityForResult(i, 1);
    }
    void pickFonts() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 2);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        if (rs != RESULT_OK || d == null) return;
        try {
            if (rq == 1) addPhoto(d.getData());
            else if (rq == 2) {
                ArrayList<Uri> us = new ArrayList<>();
                if (d.getClipData() != null)
                    for (int i = 0; i < d.getClipData().getItemCount(); i++) us.add(d.getClipData().getItemAt(i).getUri());
                else us.add(d.getData());
                File dir = new File(getFilesDir(), "fonts");
                dir.mkdirs();
                int n = 0;
                for (Uri u : us) {
                    String name = nameOf(u);
                    if (!name.toLowerCase().matches(".*\\.(ttf|otf)")) continue;
                    copy(u, new File(dir, name));
                    n++;
                }
                loadFonts();
                toast(n + " font(s) added");
                refresh();
            } else if (rq == 3) {
                Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                fv.paint(new Canvas(bm), false);
                OutputStream os = getContentResolver().openOutputStream(d.getData());
                bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close(); bm.recycle();
                toast("Saved");
            }
        } catch (Exception e) { toast("Error: " + e.getMessage()); }
    }

    String nameOf(Uri u) {
        String n = "font" + System.currentTimeMillis() + ".ttf";
        Cursor c = getContentResolver().query(u, null, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = c.getString(i);
            }
            c.close();
        }
        return n;
    }
    void copy(Uri u, File f) throws IOException {
        InputStream in = getContentResolver().openInputStream(u);
        OutputStream o = new FileOutputStream(f);
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) > 0) o.write(b, 0, r);
        o.close(); in.close();
    }
    Bitmap decode(Uri u) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > 1600) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s; o.inMutable = true; o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        in = getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        in.close();
        if (b != null) b.setHasAlpha(true);
        return b;
    }
    void addPhoto(Uri u) throws IOException {
        Bitmap b = decode(u);
        if (b == null) return;
        push();
        L l = new L();
        l.type = 2; l.bmp = b; l.uri = u.toString();
        l.w = W * .7f; l.h = l.w * b.getHeight() / b.getWidth();
        add(l);
    }

    /* ---------- canvas ---------- */
    class FlyerView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ip = new Paint(Paint.FILTER_BITMAP_FLAG), bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sc = 1, ox, oy, dx, dy, zoom = 1, panX, panY, lx, ly, w0, h0, s0, d0, pbx, pby;
        int mode;
        boolean twoDown, pend;
        L drag;
        ScaleGestureDetector sgd;

        FlyerView(Activity a) {
            super(a);
            sgd = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    zoom = Math.max(.2f, Math.min(8f, zoom * d.getScaleFactor()));
                    status(); invalidate();
                    return true;
                }
            });
        }

        Typeface tf(L l) {
            Typeface t = fonts.get(l.font);
            if (t == null) t = Typeface.DEFAULT;
            return Typeface.create(t, l.bold ? Typeface.BOLD : Typeface.NORMAL);
        }
        void meas(L l) {
            if (l.type != 0) return;
            p.setTypeface(tf(l)); p.setTextSize(l.size);
            String[] a = l.txt.split("\n", -1);
            float w = 1;
            for (String s : a) w = Math.max(w, p.measureText(s));
            Paint.FontMetrics m = p.getFontMetrics();
            l.w = w; l.h = a.length * (m.descent - m.ascent);
        }
        ColorFilter filter(L l) {
            if (l.type != 2 || (l.br == 0 && l.ct == 100 && l.sat == 100)) return null;
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(l.sat / 100f);
            float c = l.ct / 100f, t = (-.5f * c + .5f) * 255 + l.br;
            cm.postConcat(new ColorMatrix(new float[]{c, 0, 0, 0, t, 0, c, 0, 0, t, 0, 0, c, 0, t, 0, 0, 0, 1, 0}));
            return new ColorMatrixColorFilter(cm);
        }
        void paint(Canvas c, boolean ui) {
            p.setStyle(Paint.Style.FILL); p.setColor(bg);
            c.drawRect(0, 0, W, H, p);
            for (L l : ls) {
                if (!l.vis) continue;
                meas(l);
                c.save(); c.translate(l.x, l.y); c.rotate(l.rot);
                RectF r = new RectF(-l.w / 2, -l.h / 2, l.w / 2, l.h / 2);
                if (l.type == 0) {
                    p.setTypeface(tf(l)); p.setTextSize(l.size); p.setColor(l.color);
                    p.setAlpha(Math.round(Color.alpha(l.color) * l.op / 255f));
                    Paint.FontMetrics m = p.getFontMetrics();
                    float lh = m.descent - m.ascent;
                    String[] a = l.txt.split("\n", -1);
                    p.setTextAlign(l.align == 0 ? Paint.Align.LEFT : l.align == 2 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                    float ax = l.align == 0 ? r.left : l.align == 2 ? r.right : 0;
                    for (int i = 0; i < a.length; i++) c.drawText(a[i], ax, r.top + i * lh - m.ascent, p);
                    p.setTextAlign(Paint.Align.LEFT);
                } else if (l.type == 1) {
                    p.setColor(l.color);
                    p.setAlpha(Math.round(Color.alpha(l.color) * l.op / 255f));
                    c.drawRect(r, p);
                } else if (l.bmp != null) {
                    ip.setAlpha(l.op); ip.setColorFilter(filter(l));
                    c.drawBitmap(l.bmp, null, r, ip);
                    ip.setColorFilter(null);
                }
                c.restore();
            }
            if (ui && sel != null && sel.vis) {
                c.save(); c.translate(sel.x, sel.y); c.rotate(sel.rot);
                float hw = sel.w / 2, hh = sel.h / 2, k = 1 / sc;
                p.setStyle(Paint.Style.STROKE); p.setColor(0xff2b8cff); p.setStrokeWidth(2 * k);
                c.drawRect(-hw, -hh, hw, hh, p);
                c.drawLine(0, -hh, 0, -hh - 40 * k, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(hw - 8 * k, hh - 8 * k, hw + 8 * k, hh + 8 * k, p);
                c.drawCircle(0, -hh - 40 * k, 8 * k, p);
                c.restore();
            }
        }
        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(0xff1e1e1e);
            sc = Math.min((getWidth() - 40f) / W, (getHeight() - 40f) / H) * zoom;
            ox = (getWidth() - W * sc) / 2 + panX; oy = (getHeight() - H * sc) / 2 + panY;
            c.save(); c.translate(ox, oy); c.scale(sc, sc); c.clipRect(0, 0, W, H);
            paint(c, true);
            c.restore();
        }
        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                zoom = Math.max(.2f, Math.min(8f, zoom * (v > 0 ? 1.1f : .9f)));
                status(); invalidate();
                return true;
            }
            return super.onGenericMotionEvent(e);
        }

        float[] loc(L l, float x, float y) {
            double a = Math.toRadians(-l.rot);
            float ddx = x - l.x, ddy = y - l.y;
            return new float[]{(float) (ddx * Math.cos(a) - ddy * Math.sin(a)), (float) (ddx * Math.sin(a) + ddy * Math.cos(a))};
        }
        float[] toBmp(L l, float x, float y) {
            float[] q = loc(l, x, y);
            return new float[]{(q[0] + l.w / 2) / l.w * l.bmp.getWidth(), (q[1] + l.h / 2) / l.h * l.bmp.getHeight()};
        }
        void stroke(L l, float x0, float y0, float x1, float y1) {
            Canvas cv = new Canvas(l.bmp);
            bp.setStyle(Paint.Style.STROKE); bp.setStrokeCap(Paint.Cap.ROUND);
            bp.setStrokeWidth(Math.max(1f, brushSize * l.bmp.getWidth() / l.w));
            if (tool == 2) { bp.setColor(0xff000000); bp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); }
            else { bp.setColor(brushColor); bp.setXfermode(null); }
            cv.drawLine(x0, y0, x1, y1, bp);
        }
        void paintDown(float x, float y) {
            boolean ok = sel != null && sel.type >= 2;
            if (!ok && tool == 2) { toast("Select a photo or paint layer first"); return; }
            push();
            if (!ok) newPaint(); else cow(sel);
            mode = 5;
            float[] b = toBmp(sel, x, y);
            pbx = b[0]; pby = b[1];
            stroke(sel, pbx, pby, pbx, pby);
            invalidate();
        }
        void cutTap(float x, float y) {
            if (busy || sel == null || sel.type != 2) return;
            float[] b = toBmp(sel, x, y);
            int bx = (int) b[0], by = (int) b[1];
            if (bx < 0 || by < 0 || bx >= sel.bmp.getWidth() || by >= sel.bmp.getHeight()) return;
            push(); cow(sel);
            runCut(sel, new int[][]{{bx, by}});
        }
        void onDn(float x, float y) {
            if (sel != null) {
                float[] q = loc(sel, x, y);
                float hw = sel.w / 2, hh = sel.h / 2, t = 30 / sc;
                if (Math.hypot(q[0] - hw, q[1] - hh) < t) {
                    mode = 2; pend = true; w0 = sel.w; h0 = sel.h; s0 = sel.size;
                    d0 = Math.max(1f, (float) Math.hypot(x - sel.x, y - sel.y));
                    return;
                }
                if (Math.hypot(q[0], q[1] + hh + 40 / sc) < t) { mode = 3; pend = true; return; }
            }
            L hit = null;
            for (int i = ls.size() - 1; i >= 0; i--) {
                L l = ls.get(i);
                if (!l.vis) continue;
                meas(l);
                float[] q = loc(l, x, y);
                if (Math.abs(q[0]) <= l.w / 2 + 12 / sc && Math.abs(q[1]) <= l.h / 2 + 12 / sc) { hit = l; break; }
            }
            if (hit != sel) { sel = hit; refresh(); }
            if (hit != null) { mode = 1; pend = true; drag = hit; dx = hit.x - x; dy = hit.y - y; }
            invalidate();
        }
        void onMv(MotionEvent e, float x, float y) {
            if (mode == 4) {
                panX += e.getX() - lx; panY += e.getY() - ly; lx = e.getX(); ly = e.getY();
            } else if (mode == 5) {
                float[] b = toBmp(sel, x, y);
                stroke(sel, pbx, pby, b[0], b[1]);
                pbx = b[0]; pby = b[1];
            } else if (mode >= 1 && mode <= 3 && sel != null) {
                if (pend) { push(); pend = false; }
                if (mode == 1 && drag != null) { drag.x = x + dx; drag.y = y + dy; }
                else if (mode == 2) {
                    float f = (float) Math.hypot(x - sel.x, y - sel.y) / d0;
                    if (sel.type == 0) sel.size = Math.max(10, s0 * f);
                    else { sel.w = Math.max(10, w0 * f); sel.h = Math.max(10, h0 * f); }
                } else if (mode == 3) sel.rot = (float) Math.toDegrees(Math.atan2(y - sel.y, x - sel.x)) + 90;
            }
            invalidate();
        }
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            sgd.onTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) twoDown = false;
            if (e.getPointerCount() > 1) {
                float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                if (twoDown) { panX += mx - lx; panY += my - ly; }
                twoDown = true; lx = mx; ly = my; mode = 0; drag = null;
                invalidate();
                return true;
            }
            if (twoDown) {
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) twoDown = false;
                return true;
            }
            float x = (e.getX() - ox) / sc, y = (e.getY() - oy) / sc;
            if (a == MotionEvent.ACTION_DOWN) {
                pend = false; mode = 0; drag = null;
                if ((e.getButtonState() & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_TERTIARY)) != 0) {
                    mode = 4; lx = e.getX(); ly = e.getY();
                } else if (tool == 1 || tool == 2) paintDown(x, y);
                else if (tool == 3) cutTap(x, y);
                else onDn(x, y);
            } else if (a == MotionEvent.ACTION_MOVE) onMv(e, x, y);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (mode == 5) buildLayers();
                mode = 0;
            }
            return true;
        }
    }
}
