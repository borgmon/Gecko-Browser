package com.example.geckobrowser;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;



public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiTheme ui = UiTheme.current(this);
        setTheme(ui.dark ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(savedInstanceState);
        if (UiTheme.OLED.equals(ui.name)) {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.BLACK));
        }
        setContentView(R.layout.activity_settings);

        findViewById(R.id.setDefaultBrowserButton).setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.role.RoleManager roleManager =
                        getSystemService(android.app.role.RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(
                        android.app.role.RoleManager.ROLE_BROWSER)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(
                            android.app.role.RoleManager.ROLE_BROWSER), 1045);
                    return;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                    return;
                } catch (android.content.ActivityNotFoundException ignored) {
                }
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            startActivity(Intent.createChooser(browserIntent,
                    getString(R.string.choose_default_browser)));
        });

        Button b = findViewById(R.id.settingsSaveButton);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                SharedPreferences.Editor editor = getSharedPreferences("settingsPrefs", MODE_PRIVATE).edit();

                Switch s;

                s = findViewById(R.id.desktopToggle);
                if (s.isChecked()){
                    editor.putBoolean("desktopMode", true);
                } else {
                    editor.putBoolean("desktopMode", false);
                }

                s = findViewById(R.id.disableJavascriptToggle);
                if (s.isChecked()){
                    editor.putBoolean("javascript", false);
                } else {
                    editor.putBoolean("javascript", true);

                }

                s = findViewById(R.id.autoHideBarToggle);
                if (s.isChecked()){
                    editor.putBoolean("autoHideBar", true);
                } else {
                    editor.putBoolean("autoHideBar", false);
                }

                s = findViewById(R.id.fullscreenToggle);
                editor.putBoolean("fullscreen", s.isChecked());

                s = findViewById(R.id.disableTPToggle);
                if (s.isChecked()){
                    editor.putBoolean("trackingProtection", false);
                } else {
                    editor.putBoolean("trackingProtection", true);
                }

                s = findViewById(R.id.privateModeToggle);
                if (s.isChecked()){
                    editor.putBoolean("privateMode", true);
                } else {
                    editor.putBoolean("privateMode", false);
                }

                s = findViewById(R.id.hideWhiteSplashToggle);
                if (s.isChecked()){
                    editor.putBoolean("hideWhiteSplash", true);
                } else {
                    editor.putBoolean("hideWhiteSplash", false);
                }

                s = findViewById(R.id.searchSuggestionsToggle);
                if (s.isChecked()){
                    editor.putBoolean("searchSuggestions", true);
                } else {
                    editor.putBoolean("searchSuggestions", false);
                }

                s = findViewById(R.id.homepageForNewTabsToggle);
                editor.putBoolean("homepageForNewTabs", s.isChecked());

                int checkedTheme = ((android.widget.RadioGroup) findViewById(R.id.themeRadioGroup))
                        .getCheckedRadioButtonId();
                editor.putString("uiTheme", checkedTheme == R.id.themeDark ? UiTheme.DARK
                        : checkedTheme == R.id.themeOled ? UiTheme.OLED : UiTheme.LIGHT);

                boolean liveTabs = ((android.widget.RadioGroup) findViewById(R.id.tabModeRadioGroup))
                        .getCheckedRadioButtonId() == R.id.tabModeLive;
                boolean tabModeChanged = liveTabs != MainActivity.isLiveTabsMode(SettingsActivity.this);
                editor.putString("tabMode", liveTabs ? "live" : "classic");
                boolean privateChanged = ((Switch) findViewById(R.id.privateModeToggle)).isChecked()
                        != getSharedPreferences("settingsPrefs", MODE_PRIVATE)
                        .getBoolean("privateMode", false);
                editor.putBoolean("clearTabsOnExit",
                        ((Switch) findViewById(R.id.clearTabsOnExitToggle)).isChecked());
                boolean blockAds = ((Switch) findViewById(R.id.blockAdsToggle)).isChecked();
                boolean blockAdsChanged = blockAds != getSharedPreferences("settingsPrefs",
                        MODE_PRIVATE).getBoolean("blockAds", false);
                editor.putBoolean("blockAds", blockAds);

                s = findViewById(R.id.liteModeToggle);
                boolean liteModeChanged = s.isChecked() != MainActivity.isLiteMode(SettingsActivity.this);
                editor.putBoolean("liteMode", s.isChecked());
                EditText e;

                e = findViewById(R.id.userAgentEditText);
                editor.putString("userAgent", e.getText().toString());

                e = findViewById(R.id.searchEngineEditText);
                editor.putString("searchEngine", e.getText().toString());

                e = findViewById(R.id.homepageEditText);
                editor.putString("homepage", e.getText().toString().trim());

                editor.commit(); //needs to be commit otherwise its stopped by restart

                // Engine prefs and session settings only apply to a fresh process.
                restartApp(liteModeChanged || tabModeChanged || privateChanged || blockAdsChanged);
            }
        });

        SharedPreferences prefs = getSharedPreferences("settingsPrefs", MODE_PRIVATE);
        Switch s;

        s = findViewById(R.id.desktopToggle);
        s.setChecked(prefs.getBoolean("desktopMode", false));

        s = findViewById(R.id.disableJavascriptToggle);
        s.setChecked(!prefs.getBoolean("javascript", true)); //reversed with !

        s = findViewById(R.id.autoHideBarToggle);
        s.setChecked(prefs.getBoolean("autoHideBar", false));

        s = findViewById(R.id.fullscreenToggle);
        s.setChecked(prefs.getBoolean("fullscreen", false));

        s = findViewById(R.id.disableTPToggle);
        s.setChecked(!prefs.getBoolean("trackingProtection", true)); //reversed with !

        s = findViewById(R.id.privateModeToggle);
        s.setChecked(prefs.getBoolean("privateMode", false));

        s = findViewById(R.id.searchSuggestionsToggle);
        s.setChecked(prefs.getBoolean("searchSuggestions", true));

        s = findViewById(R.id.hideWhiteSplashToggle);
        s.setChecked(prefs.getBoolean("hideWhiteSplash", false));

        s = findViewById(R.id.homepageForNewTabsToggle);
        s.setChecked(prefs.getBoolean("homepageForNewTabs", true));

        s = findViewById(R.id.liteModeToggle);
        s.setChecked(MainActivity.isLiteMode(this));

        ((android.widget.RadioGroup) findViewById(R.id.tabModeRadioGroup)).check(
                MainActivity.isLiveTabsMode(this) ? R.id.tabModeLive : R.id.tabModeClassic);
        ((Switch) findViewById(R.id.clearTabsOnExitToggle)).setChecked(
                prefs.getBoolean("clearTabsOnExit", false));
        ((Switch) findViewById(R.id.blockAdsToggle)).setChecked(
                prefs.getBoolean("blockAds", false));

        String themeName = UiTheme.currentName(this);
        ((android.widget.RadioGroup) findViewById(R.id.themeRadioGroup)).check(
                UiTheme.DARK.equals(themeName) ? R.id.themeDark
                        : UiTheme.OLED.equals(themeName) ? R.id.themeOled : R.id.themeLight);

        EditText e;
        e = findViewById(R.id.userAgentEditText);
        e.setText(prefs.getString("userAgent", ""));

        e = findViewById(R.id.searchEngineEditText);
        e.setText(prefs.getString("searchEngine", ""));

        e = findViewById(R.id.homepageEditText);
        e.setText(prefs.getString("homepage", "https://start.duckduckgo.com"));
    }

    private void restartApp(boolean restartProcess) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (restartProcess) {
            // Gecko reads engine prefs only at startup, so restart the whole process.
            android.app.AlarmManager alarmManager =
                    (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            int flags = android.app.PendingIntent.FLAG_CANCEL_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }
            alarmManager.set(android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + 500,
                    android.app.PendingIntent.getActivity(this, 2, intent, flags));
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        this.startActivity(intent);
    }

    public void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);

        View view = this.getCurrentFocus();
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}