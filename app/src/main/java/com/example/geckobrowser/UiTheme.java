package com.example.geckobrowser;

import android.app.AlertDialog;
import android.content.Context;

/** Colour palettes for the light, dark and OLED (pure black) browser themes. */
public final class UiTheme {

    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String OLED = "oled";

    public final String name;
    public final boolean dark;

    public final int toolbar;
    public final int toolbarIcon;
    public final int toolbarDivider;
    public final int addressBackground;
    public final int addressBorder;
    public final int addressText;
    public final int addressHint;
    public final int addressIcon;

    public final int pageBackground;
    public final int menuBackground;
    public final int menuDivider;
    public final int menuText;
    public final int menuIcon;
    public final int accent;

    public final int cardBackground;
    public final int cardActiveBackground;
    public final int previewBackground;
    public final int sectionBackground;
    public final int separator;
    public final int primaryText;
    public final int secondaryText;

    private UiTheme(String name, boolean dark,
            int toolbar, int toolbarIcon, int toolbarDivider,
            int addressBackground, int addressBorder, int addressText, int addressHint,
            int addressIcon, int pageBackground, int menuBackground, int menuDivider,
            int menuText, int menuIcon, int accent, int cardBackground, int cardActiveBackground,
            int previewBackground, int sectionBackground, int separator, int primaryText,
            int secondaryText) {
        this.name = name;
        this.dark = dark;
        this.toolbar = toolbar;
        this.toolbarIcon = toolbarIcon;
        this.toolbarDivider = toolbarDivider;
        this.addressBackground = addressBackground;
        this.addressBorder = addressBorder;
        this.addressText = addressText;
        this.addressHint = addressHint;
        this.addressIcon = addressIcon;
        this.pageBackground = pageBackground;
        this.menuBackground = menuBackground;
        this.menuDivider = menuDivider;
        this.menuText = menuText;
        this.menuIcon = menuIcon;
        this.accent = accent;
        this.cardBackground = cardBackground;
        this.cardActiveBackground = cardActiveBackground;
        this.previewBackground = previewBackground;
        this.sectionBackground = sectionBackground;
        this.separator = separator;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
    }

    /** Slate-blue bar with a white address field, dark menu list, light dialogs. */
    private static final UiTheme LIGHT_THEME = new UiTheme(LIGHT, false,
            0xFF2F4054, 0xFFFFFFFF, 0xFF4A5C70,
            0xFFFFFFFF, 0xFFB9C3CC, 0xFF303030, 0xFF8E8E8E, 0xFF8A96A1,
            0xFFFFFFFF, 0xFF2B3238, 0xFF3E474F, 0xFFFFFFFF, 0xFFE6E9EC, 0xFF427BA6,
            0xFFFFFFFF, 0xFFD3E5F2, 0xFFE9EDF0, 0xFFE9EDF0, 0xFFCCD1D6, 0xFF303030,
            0xFF6E6E6E);

    private static final UiTheme DARK_THEME = new UiTheme(DARK, true,
            0xFF1F262D, 0xFFE6E9EC, 0xFF36414B,
            0xFF2E3740, 0xFF3D4852, 0xFFEEEEEE, 0xFF8A949D, 0xFF9AA4AD,
            0xFF15191D, 0xFF242B32, 0xFF353E47, 0xFFEEEEEE, 0xFFC9D0D6, 0xFF5D9BD0,
            0xFF2A3138, 0xFF23425C, 0xFF1B2126, 0xFF1E252B, 0xFF3A444D, 0xFFEEEEEE,
            0xFFA0A8AF);

    /** Pure black surfaces so OLED pixels stay off. */
    private static final UiTheme OLED_THEME = new UiTheme(OLED, true,
            0xFF000000, 0xFFDDDDDD, 0xFF262626,
            0xFF101010, 0xFF2A2A2A, 0xFFE6E6E6, 0xFF7A7A7A, 0xFF8A8A8A,
            0xFF000000, 0xFF000000, 0xFF1E1E1E, 0xFFE6E6E6, 0xFFBBBBBB, 0xFF4F94CC,
            0xFF0E0E0E, 0xFF10283B, 0xFF080808, 0xFF0A0A0A, 0xFF262626, 0xFFE6E6E6,
            0xFF8F8F8F);

    public static String currentName(Context context) {
        return context.getSharedPreferences("settingsPrefs", Context.MODE_PRIVATE)
                .getString("uiTheme", LIGHT);
    }

    public static UiTheme current(Context context) {
        String name = currentName(context);
        if (DARK.equals(name)) {
            return DARK_THEME;
        }
        if (OLED.equals(name)) {
            return OLED_THEME;
        }
        return LIGHT_THEME;
    }

    /** The phone's own dialog theme (TouchWiz on Samsung), light or dark. */
    public int dialogTheme() {
        return dark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
    }

    public int contextTheme() {
        return dark ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light;
    }
}
