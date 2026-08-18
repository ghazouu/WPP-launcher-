package com.ghazou.wpp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;

import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import android.net.Uri;

import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {

    FrameLayout root;
    LinearLayout sidebar;
    ImageView wallpaper;
    ImageView sidebarBlur;

    PackageManager pm;
    SharedPreferences prefs;
    Handler handler = new Handler();
    AudioManager audio;

    TextView clock;
    TextView battery;

    View drawer;
    View systemPanel;
    View mediaPanel;
    View settingsPanel;

    ArrayList<ApplicationInfo> apps = new ArrayList<ApplicationInfo>();
    HashMap<String, String> labelCache = new HashMap<String, String>();
    HashMap<String, Drawable> iconCache = new HashMap<String, Drawable>();

    String currentCategory = "ALL";
    int slotPage = 0;
    final int MAX_SLOT_PAGES = 3;

    int TEXT = Color.rgb(192,202,245);
    int BLUE = Color.rgb(122,162,247);
    int PURPLE = Color.rgb(187,154,247);
    int CYAN = Color.rgb(125,207,255);
    int GREEN = Color.rgb(158,206,106);
    int RED = Color.rgb(247,118,142);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);

        getWindow().setBackgroundDrawable(null);

        hideSystemBars();

        pm = getPackageManager();
        prefs = getSharedPreferences("wpp", MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        loadTheme();
        slotPage = prefs.getInt("slot_page", 0);

        loadApps();
        build();
        startUpdates();

        checkDefaultLauncher();
    }

    void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (sidebar != null) {
            rebuildSidebar();
        }
    }

    void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                sidebarBlur = new ImageView(this);
                sidebarBlur.setScaleType(ImageView.ScaleType.CENTER_CROP);
                root.addView(sidebarBlur, new FrameLayout.LayoutParams(-1, -1));

                sidebarBlur.setClipBounds(
                        new android.graphics.Rect(
                                0, 0, dp(70),
                                getResources().getDisplayMetrics().heightPixels));

                applyBlur(sidebarBlur);
            } catch (Throwable e) {
                sidebarBlur = null;
            }
        }

        loadWallpaper();
        createSidebar();
        setContentView(root);
    }

    void applyBlur(View target) {
        try {
            Class<?> tileModeClass = Class.forName("android.graphics.Shader$TileMode");
            Object clamp = null;
            Object[] constants = tileModeClass.getEnumConstants();
            for (int i = 0; i < constants.length; i++) {
                if (constants[i].toString().equals("CLAMP")) {
                    clamp = constants[i];
                }
            }

            Class<?> effectClass = Class.forName("android.graphics.RenderEffect");
            java.lang.reflect.Method createBlur = effectClass.getMethod("createBlurEffect", float.class, float.class, tileModeClass);
            Object effect = createBlur.invoke(null, 35f, 35f, clamp);

            java.lang.reflect.Method setEffect = View.class.getMethod("setRenderEffect", effectClass);
            setEffect.invoke(target, effect);
        } catch (Throwable e) {}
    }

    /* GLASSMORPHISM DIALOG STYLER */
    void applyGlassStyle(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            GradientDrawable glassBg = new GradientDrawable();
            glassBg.setColor(Color.argb(210, 18, 20, 32));
            glassBg.setCornerRadius(dp(22));
            glassBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
            dialog.getWindow().setBackgroundDrawable(glassBg);
        }
    }

    void loadTheme() {
        String theme = prefs.getString("theme", "tokyo");

        if (theme.equals("custom")) {
            TEXT = prefs.getInt("theme_text", Color.rgb(192,202,245));
            BLUE = prefs.getInt("theme_blue", Color.rgb(122,162,247));
            PURPLE = prefs.getInt("theme_purple", Color.rgb(187,154,247));
            CYAN = prefs.getInt("theme_cyan", Color.rgb(125,207,255));
            GREEN = prefs.getInt("theme_green", Color.rgb(158,206,106));
            RED = prefs.getInt("theme_red", Color.rgb(247,118,142));
        } else if (theme.equals("nord")) {
            TEXT = Color.rgb(216,222,233);
            BLUE = Color.rgb(129,161,193);
            PURPLE = Color.rgb(180,142,173);
            CYAN = Color.rgb(136,192,208);
            GREEN = Color.rgb(163,190,140);
            RED = Color.rgb(191,97,106);
        } else if (theme.equals("gruvbox")) {
            TEXT = Color.rgb(235,219,178);
            BLUE = Color.rgb(131,165,152);
            PURPLE = Color.rgb(211,134,155);
            CYAN = Color.rgb(142,192,124);
            GREEN = Color.rgb(184,187,38);
            RED = Color.rgb(251,73,52);
        } else if (theme.equals("catppuccin")) {
            TEXT = Color.rgb(205,214,244);
            BLUE = Color.rgb(137,180,250);
            PURPLE = Color.rgb(203,166,247);
            CYAN = Color.rgb(148,226,213);
            GREEN = Color.rgb(166,227,161);
            RED = Color.rgb(243,139,168);
        } else {
            TEXT = Color.rgb(192,202,245);
            BLUE = Color.rgb(122,162,247);
            PURPLE = Color.rgb(187,154,247);
            CYAN = Color.rgb(125,207,255);
            GREEN = Color.rgb(158,206,106);
            RED = Color.rgb(247,118,142);
        }
    }

    void chooseTheme() {
        final String[] keys = {"tokyo", "nord", "gruvbox", "catppuccin", "custom"};
        String[] names = {"Tokyo Night", "Nord", "Gruvbox", "Catppuccin", "Custom (.css file)"};

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("THEME")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (keys[which].equals("custom")) {
                            pickThemeFile();
                        } else {
                            prefs.edit().putString("theme", keys[which]).apply();
                            recreate();
                        }
                    }
                })
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void pickThemeFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, 59);
    }

    void applyCssTheme(Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;

            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
            HashMap<String, String> vars = new HashMap<String, String>();
            String line;

            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("--")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;

                String key = line.substring(2, colon).trim().toLowerCase(Locale.getDefault());
                String value = line.substring(colon + 1).trim();
                if (value.endsWith(";")) {
                    value = value.substring(0, value.length() - 1).trim();
                }
                vars.put(key, value);
            }
            r.close();

            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("theme", "custom");
            putCssColor(ed, vars, "text", "theme_text");
            putCssColor(ed, vars, "blue", "theme_blue");
            putCssColor(ed, vars, "purple", "theme_purple");
            putCssColor(ed, vars, "cyan", "theme_cyan");
            putCssColor(ed, vars, "green", "theme_green");
            putCssColor(ed, vars, "red", "theme_red");
            ed.apply();

            recreate();
        } catch (Exception e) {
            AlertDialog d = new AlertDialog.Builder(this)
                    .setTitle("Theme error")
                    .setMessage("Couldn't read that CSS file.")
                    .setPositiveButton("OK", null)
                    .create();
            d.show();
            applyGlassStyle(d);
        }
    }

    void putCssColor(SharedPreferences.Editor ed, HashMap<String, String> vars, String cssKey, String prefKey) {
        String hex = vars.get(cssKey);
        if (hex == null) return;
        try {
            ed.putInt(prefKey, Color.parseColor(hex));
        } catch (Exception e) {}
    }

    void checkDefaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);

            android.content.pm.ResolveInfo info = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);

            if (info != null && info.activityInfo != null && !info.activityInfo.packageName.equals(getPackageName())) {
                AlertDialog d = new AlertDialog.Builder(this)
                        .setTitle("SET AS DEFAULT")
                        .setMessage("WPP isn't your default home screen yet.\n\nSet it now?")
                        .setPositiveButton("Set Default", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int which) {
                                try {
                                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                                } catch (Exception e) {}
                            }
                        })
                        .setNegativeButton("Later", null)
                        .create();
                d.show();
                applyGlassStyle(d);
            }
        } catch (Exception e) {}
    }

    /*
     * SIDEBAR
     */

    void createSidebar() {
        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.CENTER_HORIZONTAL);
        sidebar.setPadding(dp(2), dp(25), dp(2), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(120, 10, 12, 20));
        bg.setStroke(dp(1), Color.argb(30, 255, 255, 255));
        sidebar.setBackground(bg);

        root.addView(sidebar, new FrameLayout.LayoutParams(dp(70), -1, Gravity.LEFT));

        addText(sidebar, "⌁", 26, PURPLE, 50);

        clock = addText(sidebar, "00\n00", 14, TEXT, 55);

        addText(sidebar, new SimpleDateFormat("EEE\ndd", Locale.getDefault()).format(new Date()).toUpperCase(), 10, Color.LTGRAY, 45);

        spacer(7);

        final LinearLayout slotsBox = new LinearLayout(this);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        slotsBox.setGravity(Gravity.CENTER_HORIZONTAL);

        sidebar.addView(slotsBox, new LinearLayout.LayoutParams(-1, -2));

        addSlot(slotsBox, 0);
        addSlot(slotsBox, 1);
        addSlot(slotsBox, 2);

        addPageDots(slotsBox);

        slotsBox.setOnTouchListener(new View.OnTouchListener() {
            float startY = 0;

            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startY = e.getY();
                } else if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dy = e.getY() - startY;
                    if (dy < -dp(40)) {
                        slotPage = (slotPage + 1) % MAX_SLOT_PAGES;
                        prefs.edit().putInt("slot_page", slotPage).apply();
                        rebuildSidebar();
                    } else if (dy > dp(40)) {
                        slotPage = (slotPage - 1 + MAX_SLOT_PAGES) % MAX_SLOT_PAGES;
                        prefs.edit().putInt("slot_page", slotPage).apply();
                        rebuildSidebar();
                    }
                }
                return true;
            }
        });

        spacer(8);

        addButton(sidebar, "◌", CYAN, 48, new View.OnClickListener() {
            public void onClick(View v) {
                toggleSystem();
            }
        });

        addButton(sidebar, "♫", PURPLE, 48, new View.OnClickListener() {
            public void onClick(View v) {
                toggleMediaPanel();
            }
        });

        addButton(sidebar, "▦", TEXT, 48, new View.OnClickListener() {
            public void onClick(View v) {
                toggleDrawer();
            }
        });

        spacer(5);

        battery = addText(sidebar, "--%", 10, GREEN, 40);

        addButton(sidebar, "☼", PURPLE, 40, new View.OnClickListener() {
            public void onClick(View v) {
                toggleMediaPanel();
            }
        });

        addButton(sidebar, "⚙", TEXT, 44, new View.OnClickListener() {
            public void onClick(View v) {
                toggleSettingsPanel();
            }
        });

        addButton(sidebar, "⚙", TEXT, 44, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {}
            }
        });
    }

    String slotKey(int page, int slot) {
        if (page == 0) return "slot_" + slot;
        return "slot_p" + page + "_" + slot;
    }

    void addPageDots(LinearLayout parent) {
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);

        for (int i = 0; i < MAX_SLOT_PAGES; i++) {
            TextView dot = text("●", 8, (i == slotPage) ? CYAN : Color.DKGRAY);
            dots.addView(dot, new LinearLayout.LayoutParams(dp(14), dp(14)));
        }

        parent.addView(dots, new LinearLayout.LayoutParams(-1, dp(16)));
    }

    View wrapIconWithBadge(ImageView icon, String pkg, int width, int height) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        if (pkg.length() > 0 && NotificationService.has(pkg)) {
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(RED);
            dot.setBackground(dotBg);

            FrameLayout.LayoutParams dp2 = new FrameLayout.LayoutParams(dp(10), dp(10));
            dp2.gravity = Gravity.TOP | Gravity.RIGHT;
            wrap.addView(dot, dp2);
        }

        return wrap;
    }

    void addSlot(final LinearLayout parent, final int slot) {
        final int page = slotPage;
        final LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(3), dp(3), dp(3), dp(3));

        final ImageView icon = new ImageView(this);
        String pkg = prefs.getString(slotKey(page, slot), "");

        if (pkg.length() > 0) {
            try {
                icon.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (Exception e) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            icon.setImageResource(android.R.drawable.ic_input_add);
            icon.setColorFilter(BLUE);
        }

        holder.addView(wrapIconWithBadge(icon, pkg, 48, 48), new LinearLayout.LayoutParams(dp(48), dp(48)));
        parent.addView(holder, new LinearLayout.LayoutParams(dp(66), dp(58)));

        holder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String p = prefs.getString(slotKey(page, slot), "");
                if (p.length() == 0) {
                    chooseApp(page, slot);
                } else {
                    launch(p);
                }
            }
        });

        holder.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                chooseApp(page, slot);
                return true;
            }
        });
    }

    void chooseApp(final int page, final int slot) {
        final ArrayList<String> names = new ArrayList<String>();
        final ArrayList<String> packages = new ArrayList<String>();

        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo a = apps.get(i);
            names.add(labelCache.get(a.packageName));
            packages.add(a.packageName);
        }

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("SIDEBAR SLOT " + (slot + 1))
                .setItems(names.toArray(new String[names.size()]), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        prefs.edit().putString(slotKey(page, slot), packages.get(which)).apply();
                        rebuildSidebar();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void closePanelsExcept(View active) {
        if (drawer != null && drawer != active) {
            root.removeView(drawer);
            drawer = null;
        }
        if (systemPanel != null && systemPanel != active) {
            root.removeView(systemPanel);
            systemPanel = null;
        }
        if (mediaPanel != null && mediaPanel != active) {
            root.removeView(mediaPanel);
            mediaPanel = null;
        }
        if (settingsPanel != null && settingsPanel != active) {
            root.removeView(settingsPanel);
            settingsPanel = null;
        }
    }

    /*
     * DRAWER
     */

    void toggleDrawer() {
        if (drawer != null) {
            root.removeView(drawer);
            drawer = null;
            return;
        }
        closePanelsExcept(null);
        showDrawer();
    }

    void showDrawer() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(14), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 14, 16, 26));
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Color.argb(40, 255, 255, 255));
        box.setBackground(bg);

        TextView title = text("APPLICATIONS", 22, BLUE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        addFrequentRow(box);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        fillDrawer(list, "");

        final LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.HORIZONTAL);

        ScrollView catScroll = new ScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.addView(cats);

        box.addView(catScroll, new LinearLayout.LayoutParams(-1, dp(50)));

        addCategoryButtons(cats);

        final EditText search = new EditText(this);
        search.setHint("Search apps...");
        search.setTextColor(TEXT);
        search.setHintTextColor(Color.GRAY);
        search.setSingleLine(true);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.argb(60, 255, 255, 255));
        inputBg.setCornerRadius(dp(14));
        search.setBackground(inputBg);
        search.setPadding(dp(16), 0, dp(16), 0);

        box.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                fillDrawer(list, s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -1);
        p.leftMargin = dp(78);
        p.rightMargin = dp(8);
        p.topMargin = dp(8);
        p.bottomMargin = dp(8);

        root.addView(box, p);
        drawer = box;
        animateIn(box);
    }

    void addCategoryButtons(final LinearLayout cats) {
        cats.removeAllViews();
        addCatButton(cats, "ALL");

        String raw = prefs.getString("categories", "");
        if (raw.length() > 0) {
            String[] parts = raw.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].length() > 0) {
                    addCatButton(cats, parts[i]);
                }
            }
        }
        addCatButton(cats, "+");
    }

    void addFrequentRow(LinearLayout box) {
        ArrayList<ApplicationInfo> top = new ArrayList<ApplicationInfo>(apps);

        Collections.sort(top, new Comparator<ApplicationInfo>() {
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                int x = prefs.getInt("uses_" + a.packageName, 0);
                int y = prefs.getInt("uses_" + b.packageName, 0);
                return y - x;
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);

        int shown = 0;
        for (int i = 0; i < top.size() && shown < 8; i++) {
            final ApplicationInfo a = top.get(i);
            int uses = prefs.getInt("uses_" + a.packageName, 0);
            if (uses == 0) continue;

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(iconCache.get(a.packageName));
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));

            View wrap = wrapIconWithBadge(icon, a.packageName, 52, 52);
            row.addView(wrap, new LinearLayout.LayoutParams(dp(52), dp(52)));

            wrap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    launch(a.packageName);
                }
            });
            shown++;
        }

        if (shown > 0) {
            box.addView(scroll, new LinearLayout.LayoutParams(-1, dp(68)));
        }
    }

    void addCatButton(LinearLayout parent, final String name) {
        TextView b = text(name, 11, name.equals(currentCategory) ? CYAN : TEXT);
        b.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(80, 255, 255, 255));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setPadding(dp(12), 0, dp(12), 0);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(34));
        p.setMargins(dp(3), dp(4), dp(3), dp(4));

        parent.addView(b, p);

        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (name.equals("+")) {
                    createCategory();
                } else {
                    currentCategory = name;
                    toggleDrawer();
                    toggleDrawer();
                }
            }
        });
    }

    void fillDrawer(LinearLayout list, String query) {
        list.removeAllViews();
        String q = query.toLowerCase(Locale.getDefault());

        for (int i = 0; i < apps.size(); i++) {
            final ApplicationInfo app = apps.get(i);
            final String name = labelCache.get(app.packageName);

            if (!name.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (!belongsToCategory(app.packageName)) continue;

            addAppRow(list, app);
        }
    }

    void addAppRow(LinearLayout list, final ApplicationInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(3), dp(5), dp(3));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(iconCache.get(app.packageName));

        row.addView(wrapIconWithBadge(icon, app.packageName, 48, 55), new LinearLayout.LayoutParams(dp(48), dp(55)));

        TextView appText = text(labelCache.get(app.packageName), 15, TEXT);
        appText.setPadding(dp(15), 0, 0, 0);

        row.addView(appText, new LinearLayout.LayoutParams(-1, dp(55)));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                launch(app.packageName);
            }
        });

        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showAppContextMenu(app);
                return true;
            }
        });

        list.addView(row);
    }

    void showAppContextMenu(final ApplicationInfo app) {
        String[] items = {"Set Category", "App Info", "Uninstall"};

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(labelCache.get(app.packageName))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            chooseCategory(app.packageName);
                        } else if (which == 1) {
                            try {
                                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                i.setData(Uri.parse("package:" + app.packageName));
                                startActivity(i);
                            } catch (Exception e) {}
                        } else {
                            try {
                                Intent i = new Intent(Intent.ACTION_DELETE);
                                i.setData(Uri.parse("package:" + app.packageName));
                                startActivity(i);
                            } catch (Exception e) {}
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    boolean belongsToCategory(String pkg) {
        if (currentCategory.equals("ALL")) return true;
        String value = prefs.getString("cat_" + currentCategory, "");
        if (value.length() == 0) return false;

        String[] list = value.split("\\|");
        for (int i = 0; i < list.length; i++) {
            if (list[i].equals(pkg)) return true;
        }
        return false;
    }

    void createCategory() {
        final EditText input = new EditText(this);
        input.setHint("Category name");
        input.setTextColor(TEXT);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("NEW CATEGORY")
                .setView(input)
                .setPositiveButton("Create", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;

                        String old = prefs.getString("categories", "");
                        if (old.length() > 0) old += "|";
                        old += name;

                        prefs.edit().putString("categories", old).putString("cat_" + name, "").apply();
                        currentCategory = name;
                        toggleDrawer();
                        toggleDrawer();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void chooseCategory(final String pkg) {
        final ArrayList<String> cats = new ArrayList<String>();
        cats.add("NONE");

        String raw = prefs.getString("categories", "");
        if (raw.length() > 0) {
            String[] parts = raw.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].length() > 0) cats.add(parts[i]);
            }
        }

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("ADD TO CATEGORY")
                .setItems(cats.toArray(new String[cats.size()]), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            removeFromCategories(pkg);
                        } else {
                            String raw = prefs.getString("categories", "");
                            String[] parts = raw.split("\\|");
                            String cat = parts[which - 1];
                            addToCategory(cat, pkg);
                        }
                    }
                })
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void addToCategory(String cat, String pkg) {
        String old = prefs.getString("cat_" + cat, "");
        String[] parts = old.split("\\|");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(pkg)) return;
        }

        if (old.length() > 0) old += "|";
        old += pkg;

        prefs.edit().putString("cat_" + cat, old).apply();
    }

    void removeFromCategories(String pkg) {
        String raw = prefs.getString("categories", "");
        if (raw.length() == 0) return;

        String[] cats = raw.split("\\|");
        SharedPreferences.Editor e = prefs.edit();

        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            String old = prefs.getString("cat_" + cat, "");
            String[] appsIn = old.split("\\|");
            String result = "";

            for (int j = 0; j < appsIn.length; j++) {
                if (!appsIn[j].equals(pkg) && appsIn[j].length() > 0) {
                    if (result.length() > 0) result += "|";
                    result += appsIn[j];
                }
            }
            e.putString("cat_" + cat, result);
        }
        e.apply();
    }

    /*
     * FASTFETCH SYSTEM PANEL
     */

    void toggleSystem() {
        if (systemPanel != null) {
            root.removeView(systemPanel);
            systemPanel = null;
            return;
        }
        closePanelsExcept(null);
        showSystem();
    }

    void showSystem() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 15, 17, 26));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.argb(50, 122, 162, 247));
        box.setBackground(bg);

        TextView userHeader = new TextView(this);
        userHeader.setText("user@" + android.os.Build.MODEL.toLowerCase(Locale.getDefault()).replaceAll(" ", "-"));
        userHeader.setTextSize(14);
        userHeader.setTextColor(GREEN);
        userHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.addView(userHeader, new LinearLayout.LayoutParams(-1, -2));

        TextView sep = new TextView(this);
        sep.setText("-----------------------------------");
        sep.setTextSize(12);
        sep.setTextColor(PURPLE);
        sep.setTypeface(Typeface.MONOSPACE);
        box.addView(sep, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout statsList = new LinearLayout(this);
        statsList.setOrientation(LinearLayout.VERTICAL);

        DisplayMetrics dm = getResources().getDisplayMetrics();

        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long totalRam = mi.totalMem / 1048576L;
        long usedRam = (mi.totalMem - mi.availMem) / 1048576L;

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long totalStorage = fs.getTotalBytes() / 1073741824L;
        long usedStorage = (fs.getTotalBytes() - fs.getAvailableBytes()) / 1073741824L;

        addFastfetchRow(statsList, "OS", "Android " + android.os.Build.VERSION.RELEASE + " (" + android.os.Build.VERSION.SDK_INT + ")");
        addFastfetchRow(statsList, "Host", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        addFastfetchRow(statsList, "Uptime", getUptime());
        addFastfetchRow(statsList, "Resolution", dm.widthPixels + "x" + dm.heightPixels);
        addFastfetchRow(statsList, "CPU/HW", android.os.Build.HARDWARE);
        addFastfetchRow(statsList, "Memory", usedRam + "MiB / " + totalRam + "MiB");
        addFastfetchRow(statsList, "Storage", usedStorage + "GiB / " + totalStorage + "GiB");
        addFastfetchRow(statsList, "Locale", Locale.getDefault().getDisplayName());

        scroll.addView(statsList);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView colorBlocks = new TextView(this);
        colorBlocks.setText("\n● ● ● ● ● ● ● ●");
        colorBlocks.setTextSize(13);
        colorBlocks.setTextColor(CYAN);
        colorBlocks.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        colorBlocks.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(colorBlocks, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(320), dp(340));
        p.leftMargin = dp(78);
        p.topMargin = dp(60);

        root.addView(box, p);
        systemPanel = box;
        animateIn(box);
    }

    void addFastfetchRow(LinearLayout container, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView keyTv = new TextView(this);
        keyTv.setText(key + ": ");
        keyTv.setTextSize(12);
        keyTv.setTextColor(BLUE);
        keyTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        TextView valTv = new TextView(this);
        valTv.setText(value);
        valTv.setTextSize(12);
        valTv.setTextColor(TEXT);
        valTv.setTypeface(Typeface.MONOSPACE);

        row.addView(keyTv);
        row.addView(valTv);
        container.addView(row);
    }

    /*
     * NOCTALIA SHELL MEDIA & CONTROLS PANEL
     */

    void toggleMediaPanel() {
        if (mediaPanel != null) {
            root.removeView(mediaPanel);
            mediaPanel = null;
            return;
        }
        closePanelsExcept(null);
        showMediaPanel();
    }

    void showMediaPanel() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 18, 20, 32));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.argb(50, 187, 154, 247));
        box.setBackground(bg);

        TextView title = text("QUICK CONTROLS", 14, PURPLE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        spacer(10);

        LinearLayout trackCard = new LinearLayout(this);
        trackCard.setOrientation(LinearLayout.VERTICAL);
        trackCard.setGravity(Gravity.CENTER);
        trackCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(80, 255, 255, 255));
        cardBg.setCornerRadius(dp(16));
        trackCard.setBackground(cardBg);

        final TextView songTitle = text("No Media Playing", 15, TEXT);
        songTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        songTitle.setGravity(Gravity.CENTER);

        final TextView artistName = text("Unknown Artist", 12, Color.GRAY);
        artistName.setGravity(Gravity.CENTER);

        trackCard.addView(songTitle);
        trackCard.addView(artistName);

        box.addView(trackCard, new LinearLayout.LayoutParams(-1, -2));

        updateMediaMetadata(songTitle, artistName);

        spacer(12);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        TextView prevBtn = text("⏮", 22, CYAN);
        prevBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        prevBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                handler.postDelayed(new Runnable() {
                    public void run() { updateMediaMetadata(songTitle, artistName); }
                }, 300);
            }
        });

        TextView playBtn = text("⏯", 28, GREEN);
        playBtn.setPadding(dp(20), dp(8), dp(20), dp(8));
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                handler.postDelayed(new Runnable() {
                    public void run() { updateMediaMetadata(songTitle, artistName); }
                }, 300);
            }
        });

        TextView nextBtn = text("⏭", 22, CYAN);
        nextBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        nextBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                handler.postDelayed(new Runnable() {
                    public void run() { updateMediaMetadata(songTitle, artistName); }
                }, 300);
            }
        });

        controls.addView(prevBtn);
        controls.addView(playBtn);
        controls.addView(nextBtn);

        box.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        spacer(10);

        TextView volLabel = text("VOLUME", 10, PURPLE);
        volLabel.setGravity(Gravity.CENTER);
        box.addView(volLabel, new LinearLayout.LayoutParams(-1, -2));

        /* MODERN TRANSLUCENT VOLUME SEEKBAR */
        SeekBar volume = new SeekBar(this);
        int maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume.setMax(maxVol);
        volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        volume.setProgressTintList(ColorStateList.valueOf(PURPLE));
        volume.setThumbTintList(ColorStateList.valueOf(CYAN));
        volume.setSplitTrack(false);

        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                if (user) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        box.addView(volume, new LinearLayout.LayoutParams(-1, dp(34)));

        spacer(6);

        TextView brightLabel = text("BRIGHTNESS", 10, CYAN);
        brightLabel.setGravity(Gravity.CENTER);
        box.addView(brightLabel, new LinearLayout.LayoutParams(-1, -2));

        /* MODERN TRANSLUCENT BRIGHTNESS SEEKBAR */
        SeekBar brightness = new SeekBar(this);
        int curBrightness = 127;
        try {
            curBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {}

        brightness.setMax(255);
        brightness.setProgress(curBrightness);
        brightness.setProgressTintList(ColorStateList.valueOf(CYAN));
        brightness.setThumbTintList(ColorStateList.valueOf(PURPLE));
        brightness.setSplitTrack(false);

        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                if (user) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (!Settings.System.canWrite(MainActivity.this)) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                            return;
                        }
                    }
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
                    } catch (Exception e) {}

                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = value / 255.0f;
                    getWindow().setAttributes(lp);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        box.addView(brightness, new LinearLayout.LayoutParams(-1, dp(34)));

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(290), -2);
        p.leftMargin = dp(78);
        p.topMargin = dp(110);

        root.addView(box, p);
        mediaPanel = box;
        animateIn(box);
    }

    void updateMediaMetadata(TextView titleView, TextView artistView) {
        try {
            MediaSessionManager mm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, NotificationService.class);

            List<MediaController> controllers = mm.getActiveSessions(cn);

            for (MediaController mc : controllers) {
                MediaMetadata meta = mc.getMetadata();
                if (meta != null) {
                    String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);

                    if (title != null && !title.isEmpty()) {
                        titleView.setText(title);
                    }
                    if (artist != null && !artist.isEmpty()) {
                        artistView.setText(artist);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            titleView.setText("Media Session");
            artistView.setText("Grant Notification Access");
        }
    }

    void sendMediaKey(int keyCode) {
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } catch (Exception e) {}
    }

    /*
     * WPP SETTINGS DRAWER PANEL
     */

    void toggleSettingsPanel() {
        if (settingsPanel != null) {
            root.removeView(settingsPanel);
            settingsPanel = null;
            return;
        }
        closePanelsExcept(null);
        showWppSettings();
    }

    void showWppSettings() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 18, 20, 32));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.argb(50, 122, 162, 247));
        box.setBackground(bg);

        TextView title = text("WPP SETTINGS", 16, BLUE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        spacer(12);

        String[] items = {
                "Choose Wallpaper",
                "Customize Sidebar",
                "Manage Categories",
                "App Drawer",
                "Theme",
                "About WPP"
        };

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView row = text(items[i], 14, TEXT);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.argb(40, 255, 255, 255));
            rowBg.setCornerRadius(dp(12));
            row.setBackground(rowBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, dp(4));

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toggleSettingsPanel();
                    if (index == 0) {
                        chooseWallpaper();
                    } else if (index == 1) {
                        showSidebarHelp();
                    } else if (index == 2) {
                        manageCategories();
                    } else if (index == 3) {
                        toggleDrawer();
                    } else if (index == 4) {
                        chooseTheme();
                    } else {
                        showAbout();
                    }
                }
            });

            box.addView(row, lp);
        }

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(280), -2);
        p.leftMargin = dp(78);
        p.topMargin = dp(100);

        root.addView(box, p);
        settingsPanel = box;
        animateIn(box);
    }

    /*
     * WALLPAPER
     */

    void chooseWallpaper() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 56);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 56 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {}

            prefs.edit().putString("wallpaper", uri.toString()).apply();
            showWallpaper(uri);
        }

        if (requestCode == 59 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                applyCssTheme(uri);
            }
        }
    }

    void loadWallpaper() {
        String s = prefs.getString("wallpaper", "");
        if (s.length() > 0) {
            try {
                showWallpaper(Uri.parse(s));
            } catch (Exception e) {}
        }
    }

    void showWallpaper(Uri uri) {
        try {
            wallpaper.setImageURI(null);
            wallpaper.setImageURI(uri);
            if (sidebarBlur != null) {
                sidebarBlur.setImageURI(null);
                sidebarBlur.setImageURI(uri);
            }
        } catch (Exception e) {
            wallpaper.setImageDrawable(null);
        }
    }

    /*
     * CATEGORY MANAGEMENT
     */

    void manageCategories() {
        String raw = prefs.getString("categories", "");

        if (raw.length() == 0) {
            AlertDialog d = new AlertDialog.Builder(this)
                    .setTitle("CATEGORIES")
                    .setMessage("No categories yet.\n\nCreate one from the + button in the app drawer.")
                    .setPositiveButton("OK", null)
                    .create();
            d.show();
            applyGlassStyle(d);
            return;
        }

        final String[] cats = raw.split("\\|");

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("CATEGORIES")
                .setItems(cats, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        deleteCategory(cats[which]);
                    }
                })
                .setNegativeButton("Done", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void deleteCategory(final String name) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Delete " + name + "?")
                .setMessage("Apps won't be deleted.")
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        String raw = prefs.getString("categories", "");
                        String[] parts = raw.split("\\|");
                        String result = "";

                        for (int i = 0; i < parts.length; i++) {
                            if (!parts[i].equals(name)) {
                                if (result.length() > 0) result += "|";
                                result += parts[i];
                            }
                        }

                        prefs.edit().putString("categories", result).remove("cat_" + name).apply();
                        if (currentCategory.equals(name)) {
                            currentCategory = "ALL";
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    /*
     * SMALL SETTINGS SCREENS
     */

    void showSidebarHelp() {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("SIDEBAR")
                .setMessage("Tap an empty slot to choose an app.\n\nLong press an assigned slot to change it.")
                .setPositiveButton("OK", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    void showAbout() {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("WPP Launcher")
                .setMessage("WPP Launcher v0.3")
                .setPositiveButton("OK", null)
                .create();
        d.show();
        applyGlassStyle(d);
    }

    String getUptime() {
        long ms = android.os.SystemClock.elapsedRealtime();
        long totalMinutes = ms / 60000L;

        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        sb.append(hours).append("h ").append(minutes).append("m");
        return sb.toString();
    }

    /*
     * LOAD APPS
     */

    void loadApps() {
        apps.clear();
        labelCache.clear();
        iconCache.clear();

        List<ApplicationInfo> list = pm.getInstalledApplications(0);

        for (int i = 0; i < list.size(); i++) {
            ApplicationInfo app = list.get(i);
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                apps.add(app);
                labelCache.put(app.packageName, pm.getApplicationLabel(app).toString());
                iconCache.put(app.packageName, pm.getApplicationIcon(app));
            }
        }

        Collections.sort(apps, new Comparator<ApplicationInfo>() {
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                String x = labelCache.get(a.packageName);
                String y = labelCache.get(b.packageName);
                return x.compareToIgnoreCase(y);
            }
        });
    }

    /*
     * LAUNCH
     */

    void launch(String pkg) {
        try {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                startActivity(i);
                bumpUsage(pkg);
            }
        } catch (Exception e) {}
    }

    void bumpUsage(String pkg) {
        int n = prefs.getInt("uses_" + pkg, 0);
        prefs.edit().putInt("uses_" + pkg, n + 1).apply();
    }

    /*
     * CLOCK + BATTERY
     */

    void startUpdates() {
        handler.postDelayed(new Runnable() {
            public void run() {
                if (clock != null) {
                    clock.setText(new SimpleDateFormat("HH\nmm", Locale.getDefault()).format(new Date()));
                }

                if (battery != null) {
                    BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                    int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    battery.setText(level + "%");

                    if (level <= 20) {
                        battery.setTextColor(RED);
                    } else {
                        battery.setTextColor(GREEN);
                    }
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /*
     * ANIMATION
     */

    void animateIn(View v) {
        v.setAlpha(0.0f);
        v.setTranslationX(dp(50));
        v.animate()
                .alpha(1.0f)
                .translationX(0)
                .setDuration(200)
                .start();
    }

    /*
     * UI HELPERS
     */

    TextView addText(LinearLayout parent, String value, float size, int color, int height) {
        TextView v = text(value, size, color);
        v.setGravity(Gravity.CENTER);
        parent.addView(v, new LinearLayout.LayoutParams(dp(64), dp(height)));
        return v;
    }

    void addButton(LinearLayout parent, String value, int color, int height, View.OnClickListener listener) {
        TextView v = addText(parent, value, 21, color, height);
        v.setOnClickListener(listener);
    }

    TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    void spacer(int height) {
        sidebar.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    void rebuildSidebar() {
        root.removeView(sidebar);
        createSidebar();
    }

    int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
