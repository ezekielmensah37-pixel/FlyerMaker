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
    static class L {
        int type, color = 0xff111111, align = 1;
        String txt = "Your text", font = "sans-serif";
        float x, y, w, h, size = 120;
        boolean bold, vis = true;
        Bitmap bmp, orig;
    }
    interface Cb { void on(int v); }

    int W = 1000, H = 1414, bg = 0xffffffff, tol = 35;
    boolean tapMode;
    ArrayList<L> ls = new ArrayList<>();
    L sel;
    LinkedHashMap<String, Typeface> fonts = new LinkedHashMap<>();
    FlyerView fv;
    LinearLayout layersBox, propsBox;
    final int[] PAL = {0xff000000, 0xffffffff, 0xffd90429, 0xffff6b6b, 0xffff9f1c, 0xffffd43b,
            0xff2ec4b6, 0xff11998e, 0xff1e90ff, 0xff0b3d91, 0xff6a11cb, 0xffff4fa3,
            0xff8d5524, 0xff6d6d6d, 0xffeeeeee, 0xff111827};

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    Button btn(String t, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(t); b.setAllCaps(false); b.setTextColor(0xffdddddd);
        b.setBackgroundColor(0xff3c3c3c); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(6), dp(8), dp(6)); b.setOnClickListener(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(p);
        return b;
    }
    TextView tv(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xffcccccc); t.setPadding(dp(8), dp(8), dp(8), dp(8));
        return t;
    }
    LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        for (View v : vs) r.addView(v);
        return r;
    }
    SeekBar sb(int max, int val, final Cb cb) {
        SeekBar s = new SeekBar(this);
        s.setMax(max); s.setProgress(val);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) cb.on(p); }
            public void onStartTrackingTouch(SeekBar b) {}
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
        top.addView(btn("File", v -> fileMenu()));
        top.addView(btn("T  Text", v -> addText()));
        top.addView(btn("▭  Shape", v -> addShape()));
        top.addView(btn("▣  Photo", v -> pickPhoto()));
        top.addView(btn("Aa  Add fonts", v -> pickFonts()));
        root.addView(hs);

        LinearLayout mid = new LinearLayout(this);
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
        mid.addView(rp, new LinearLayout.LayoutParams(dp(230), -1));
        root.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setBackgroundColor(0xff323232);
        LinearLayout al = new LinearLayout(this);
        hs2.addView(al);
        String[] an = {"⇤ Left", "↔ Centre", "⇥ Right", "⤒ Top", "↕ Middle", "⤓ Bottom"};
        for (int i = 0; i < 6; i++) {
            final int k = i;
            al.addView(btn(an[i], v -> align(k)));
        }
        root.addView(hs2);
        setContentView(root);
        addText();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
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

    void refresh() { buildLayers(); buildProps(); if (fv != null) fv.invalidate(); }

    void buildLayers() {
        layersBox.removeAllViews();
        for (int i = ls.size() - 1; i >= 0; i--) {
            final L l = ls.get(i);
            LinearLayout r = new LinearLayout(this);
            r.setBackgroundColor(l == sel ? 0xff0d5fbf : 0xff333333);
            TextView t = tv(l.type == 0 ? "T  " + l.txt.replace("\n", " ") : l.type == 1 ? "▭  Shape" : "▣  Photo");
            t.setSingleLine(true);
            t.setOnClickListener(v -> { sel = l; tapMode = false; refresh(); });
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(btn(l.vis ? "●" : "○", v -> { l.vis = !l.vis; refresh(); }));
            layersBox.addView(r);
        }
    }

    void buildProps() {
        propsBox.removeAllViews();
        final L l = sel;
        if (l == null) { propsBox.addView(tv("Select a layer")); return; }
        if (l.type == 0) {
            propsBox.addView(btn("Edit text…", v -> editText(l)));
            propsBox.addView(btn("Font: " + l.font, v -> pickFont(l)));
            propsBox.addView(tv("Size"));
            propsBox.addView(sb(400, (int) l.size, v -> { l.size = Math.max(10, v); fv.invalidate(); }));
            propsBox.addView(row(btn("Bold", v -> { l.bold = !l.bold; fv.invalidate(); }),
                    btn("L", v -> { l.align = 0; fv.invalidate(); }),
                    btn("C", v -> { l.align = 1; fv.invalidate(); }),
                    btn("R", v -> { l.align = 2; fv.invalidate(); })));
        } else {
            propsBox.addView(tv("Width"));
            propsBox.addView(sb(2 * W, (int) l.w, v -> {
                l.w = Math.max(20, v);
                if (l.type == 2) l.h = l.w * l.bmp.getHeight() / l.bmp.getWidth();
                fv.invalidate();
            }));
            if (l.type == 1) {
                propsBox.addView(tv("Height"));
                propsBox.addView(sb(2 * H, (int) l.h, v -> { l.h = Math.max(4, v); fv.invalidate(); }));
            }
        }
        if (l.type != 2)
            propsBox.addView(btn("Colour", v -> pickColor(c -> { l.color = c; fv.invalidate(); })));
        if (l.type == 2) {
            propsBox.addView(tv("BACKGROUND REMOVER"));
            propsBox.addView(btn("✂  Auto remove background", v -> autoCut(l)));
            propsBox.addView(btn(tapMode ? "Tap to remove: ON" : "Tap to remove: OFF",
                    v -> { tapMode = !tapMode; buildProps(); }));
            propsBox.addView(tv("Tolerance (higher removes more)"));
            propsBox.addView(sb(120, tol, v -> tol = v));
            propsBox.addView(btn("Restore original", v -> restore(l)));
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
        new Thread(() -> {
            wand(l.bmp, seeds, t);
            runOnUiThread(() -> fv.invalidate());
        }).start();
    }
    void autoCut(L l) {
        int w = l.bmp.getWidth(), h = l.bmp.getHeight();
        runCut(l, new int[][]{{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1},
                {w / 2, 0}, {w / 2, h - 1}, {0, h / 2}, {w - 1, h / 2}});
    }
    void restore(L l) {
        l.bmp = l.orig.copy(Bitmap.Config.ARGB_8888, true);
        l.bmp.setHasAlpha(true);
        fv.invalidate();
    }

    /* ---------- dialogs and layers ---------- */
    void editText(final L l) {
        final EditText e = new EditText(this);
        e.setText(l.txt); e.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("Text").setView(e)
                .setPositiveButton("OK", (d, i) -> { l.txt = e.getText().toString(); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    void pickFont(final L l) {
        final String[] names = fonts.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Font")
                .setItems(names, (d, i) -> { l.font = names[i]; refresh(); }).show();
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

    void add(L l) { l.x = W / 2f; l.y = H / 2f; ls.add(l); sel = l; tapMode = false; refresh(); }
    void addText() { add(new L()); }
    void addShape() { L l = new L(); l.type = 1; l.w = W * .6f; l.h = W * .2f; l.color = 0xffffd43b; add(l); }

    void move(int dir) {
        int i = ls.indexOf(sel), j = i + dir;
        if (sel == null || j < 0 || j >= ls.size()) return;
        Collections.swap(ls, i, j); refresh();
    }
    void dup() {
        if (sel == null) return;
        L n = new L();
        n.type = sel.type; n.txt = sel.txt; n.font = sel.font; n.color = sel.color; n.align = sel.align;
        n.x = sel.x + 30; n.y = sel.y + 30; n.w = sel.w; n.h = sel.h; n.size = sel.size;
        n.bold = sel.bold; n.orig = sel.orig;
        if (sel.bmp != null) { n.bmp = sel.bmp.copy(Bitmap.Config.ARGB_8888, true); n.bmp.setHasAlpha(true); }
        ls.add(ls.indexOf(sel) + 1, n); sel = n; refresh();
    }
    void del() { if (sel == null) return; ls.remove(sel); sel = null; refresh(); }

    void align(int k) {
        if (sel == null) return;
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

    void fileMenu() {
        final String[] it = {"A4 portrait", "Post 4:5", "Square", "Story 9:16", "Background colour", "Fit view", "Export PNG"};
        new AlertDialog.Builder(this).setItems(it, (d, i) -> {
            if (i < 4) {
                int[][] s = {{1000, 1414}, {1080, 1350}, {1080, 1080}, {1080, 1920}};
                float kx = s[i][0] / (float) W, ky = s[i][1] / (float) H;
                for (L l : ls) { l.x *= kx; l.y *= ky; }
                W = s[i][0]; H = s[i][1]; fv.invalidate();
            } else if (i == 4) {
                pickColor(c -> { bg = c; fv.invalidate(); });
            } else if (i == 5) {
                fv.zoom = 1; fv.panX = 0; fv.panY = 0; fv.invalidate();
            } else {
                Intent t = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                t.addCategory(Intent.CATEGORY_OPENABLE);
                t.setType("image/png");
                t.putExtra(Intent.EXTRA_TITLE, "flyer.png");
                startActivityForResult(t, 3);
            }
        }).show();
    }

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
    void addPhoto(Uri u) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > 2048) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        in = getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        in.close();
        if (b == null) return;
        L l = new L();
        l.type = 2; l.orig = b;
        l.bmp = b.copy(Bitmap.Config.ARGB_8888, true);
        l.bmp.setHasAlpha(true);
        l.w = W * .7f; l.h = l.w * b.getHeight() / b.getWidth();
        add(l);
    }

    class FlyerView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ip = new Paint(Paint.FILTER_BITMAP_FLAG);
        float sc = 1, ox, oy, dx, dy, zoom = 1, panX, panY, lx, ly;
        boolean panning;
        L drag;
        FlyerView(Activity a) { super(a); }

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
        void paint(Canvas c, boolean ui) {
            p.setStyle(Paint.Style.FILL); p.setColor(bg);
            c.drawRect(0, 0, W, H, p);
            for (L l : ls) {
                if (!l.vis) continue;
                meas(l);
                RectF r = new RectF(l.x - l.w / 2, l.y - l.h / 2, l.x + l.w / 2, l.y + l.h / 2);
                if (l.type == 0) {
                    p.setTypeface(tf(l)); p.setTextSize(l.size); p.setColor(l.color);
                    Paint.FontMetrics m = p.getFontMetrics();
                    float lh = m.descent - m.ascent;
                    String[] a = l.txt.split("\n", -1);
                    p.setTextAlign(l.align == 0 ? Paint.Align.LEFT : l.align == 2 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                    float ax = l.align == 0 ? r.left : l.align == 2 ? r.right : l.x;
                    for (int i = 0; i < a.length; i++) c.drawText(a[i], ax, r.top + i * lh - m.ascent, p);
                    p.setTextAlign(Paint.Align.LEFT);
                } else if (l.type == 1) {
                    p.setColor(l.color); c.drawRect(r, p);
                } else c.drawBitmap(l.bmp, null, r, ip);
            }
            if (ui && sel != null && sel.vis) {
                p.setStyle(Paint.Style.STROKE); p.setColor(0xff2b8cff); p.setStrokeWidth(3 / sc);
                c.drawRect(sel.x - sel.w / 2, sel.y - sel.h / 2, sel.x + sel.w / 2, sel.y + sel.h / 2, p);
                p.setStyle(Paint.Style.FILL);
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
                invalidate();
                return true;
            }
            return super.onGenericMotionEvent(e);
        }
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = (e.getX() - ox) / sc, y = (e.getY() - oy) / sc;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                if ((e.getButtonState() & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_TERTIARY)) != 0) {
                    panning = true; lx = e.getX(); ly = e.getY();
                    return true;
                }
                if (tapMode && sel != null && sel.type == 2) {
                    int bw = sel.bmp.getWidth(), bh = sel.bmp.getHeight();
                    int bx = (int) ((x - (sel.x - sel.w / 2)) / sel.w * bw);
                    int by = (int) ((y - (sel.y - sel.h / 2)) / sel.h * bh);
                    if (bx >= 0 && by >= 0 && bx < bw && by < bh) runCut(sel, new int[][]{{bx, by}});
                    return true;
                }
                drag = null;
                for (int i = ls.size() - 1; i >= 0; i--) {
                    L l = ls.get(i);
                    if (!l.vis) continue;
                    meas(l);
                    if (Math.abs(x - l.x) <= l.w / 2 + 15 && Math.abs(y - l.y) <= l.h / 2 + 15) { drag = l; break; }
                }
                if (drag != sel) { sel = drag; buildLayers(); buildProps(); }
                if (drag != null) { dx = drag.x - x; dy = drag.y - y; }
                invalidate();
            } else if (a == MotionEvent.ACTION_MOVE) {
                if (panning) {
                    panX += e.getX() - lx; panY += e.getY() - ly;
                    lx = e.getX(); ly = e.getY();
                    invalidate();
                } else if (drag != null) {
                    drag.x = x + dx; drag.y = y + dy; invalidate();
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                panning = false;
            }
            return true;
        }
    }
}
