package com.example.geckobrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.media.AudioManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.app.AlertDialog;
import android.view.ContextThemeWrapper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.MediaSession;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
//TODO undo and redo

public class MainActivity extends AppCompatActivity {

    private static final int MENU_RELOAD = 1;
    private static final int MENU_DESKTOP_MODE = 2;
    private static final int MENU_CLEAR_SITE_DATA = 3;
    private static final int MENU_SETTINGS = 4;
    private static final int MENU_EXIT = 5;
    private static final int MENU_ADD_FAVORITE = 6;
    private static final int MENU_SHOW_FAVORITES = 7;
    private static final int MENU_DOWNLOADS = 8;
    private static final int MENU_DEFAULT_BROWSER = 9;
    private static final int MEDIA_NOTIFICATION_ID = 1042;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1043;
    private static final String MEDIA_NOTIFICATION_CHANNEL = "media_playback";
    private static GeckoSession sRetainedSession;
    private static MainActivity sActiveInstance;
    private static final String LITE_EXTENSION_ID = "lite@geckobrowser";
    private static MediaSessionCompat sAndroidMediaSession;
    private static MediaSession sGeckoMediaSession;

    // No SYSTEM_UI_FLAG_LAYOUT_* flags: they leave stale status-bar insets behind
    // (white bar in fullscreen, page shifted/cut off after leaving it).
    private static final int FULLSCREEN_UI_FLAGS = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    public String SEARCH_URI_BASE = "https://duckduckgo.com/?q=";
    public String INITIAL_URL = "https://start.duckduckgo.com";
    boolean autoHideBar;
    boolean hideWhiteSplash;
    boolean searchSuggestions;
    boolean homepageForNewTabs = true;
    private GeckoView mGeckoView;
    private GeckoSession mGeckoSession;
    private static GeckoRuntime sGeckoRuntime;
    private GeckoSessionSettings.Builder settingsBuilder = new GeckoSessionSettings.Builder();
    private EditText urlEditText;
    private ImageButton browserBackButton;
    private ImageButton browserForwardButton;
    private ImageButton bookmarksButton;
    private boolean canGoForward;

    /** Live tabs: every tab keeps its own page loaded (for more capable devices). */
    private boolean liveTabs;
    /** Live mode only: one session per tab, aligned with tabURLs; null = not loaded yet. */
    private final ArrayList<GeckoSession> tabSessions = new ArrayList<>();
    private final java.util.IdentityHashMap<GeckoSession, TabState> tabStates =
            new java.util.IdentityHashMap<>();

    /** What the toolbar shows for a tab; kept per session so background tabs stay correct. */
    private static final class TabState {
        String url;
        String title;
        boolean canGoBack;
        boolean canGoForward;
        boolean loading;
        int progress = 100;
        GeckoSession.ProgressDelegate.SecurityInformation security;
    }
    private ProgressBar mProgressView;
    private ImageButton sslIndicatorView;
    private String sslStatusMessage;
    private String[] tabURLs;
    private AlertDialog downloadsDialog;
    private LinearLayout downloadRows;
    private final Handler downloadsHandler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<Long, long[]> downloadSamples = new java.util.HashMap<>();
    private boolean downloadsDialogVisible;
    private WebResponse pendingDownloadResponse;
    private static final int DOWNLOAD_STORAGE_PERMISSION_REQUEST = 1044;
    private static final int MAX_RECENT_DOWNLOADS = 20;
    private String currentPageUrl;
    private String currentPageTitle;
    private int activeTab = 0;
    private boolean browserFullScreen;
    private boolean fullscreenMode;
    private boolean canGoBack;
    private MediaSessionCompat androidMediaSession;
    private MediaSession geckoMediaSession;
    private static long mediaFeatures;
    private static long mediaPositionMs;
    private static long mediaDurationMs;
    private static int mediaPlaybackState = PlaybackStateCompat.STATE_NONE;
    private static String mediaTitle;
    private static String mediaArtist;
    private static boolean mediaNotificationPermissionRequested;
    private static boolean playbackServiceStarted;
    private AudioManager audioManager;
    private static final AudioManager.OnAudioFocusChangeListener AUDIO_FOCUS_CHANGE_LISTENER =
            focusChange -> {
        if (focusChange <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && sGeckoMediaSession != null) {
            sGeckoMediaSession.pause();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity previous = sActiveInstance;
        sActiveInstance = this;
        if (previous != null && previous.mGeckoView != null) {
            // Old instance may not be destroyed yet; free the shared session for this one.
            previous.mGeckoView.releaseSession();
            if (previous.mGeckoSession != null && previous.mGeckoSession.isOpen()) {
                sRetainedSession = previous.mGeckoSession;
            }
            for (GeckoSession other : previous.tabSessions) {
                if (other != null && other != previous.mGeckoSession) {
                    other.close();
                }
            }
            previous.tabSessions.clear();
            previous.mGeckoSession = null;
        }
        ui = UiTheme.current(this);
        setContentView(R.layout.activity_main);

        browserBackButton = findViewById(R.id.browserBackButton);
        browserBackButton.setEnabled(false);
        browserForwardButton = findViewById(R.id.browserForwardButton);
        browserForwardButton.setEnabled(false);
        browserForwardButton.setOnClickListener(view -> {
            if (canGoForward) {
                mGeckoSession.goForward();
            }
        });
        // Tap: add / remove the current page from favorites. Long press: open the list.
        bookmarksButton = findViewById(R.id.bookmarksButton);
        bookmarksButton.setOnClickListener(view -> toggleCurrentFavorite());
        bookmarksButton.setOnLongClickListener(view -> {
            showFavorites();
            return true;
        });
        browserBackButton.setOnClickListener(view -> {
            if (canGoBack) {
                mGeckoSession.goBack();
            }
        });

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            int hiddenBars = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if ((browserFullScreen || fullscreenMode) && (visibility & hiddenBars) != hiddenBars) {
                hideSystemBars();
            }
        });

        mProgressView = findViewById(R.id.pageProgressView);
        setupAndroidMediaSession();

        setupUrlEditText();
        setupSettings();
        setupGeckoView();
        setupTabs();
        if (liveTabs) {
            tabSessions.clear();
            ensureTabSessionSlots();
            tabSessions.set(activeTab, mGeckoSession);
        }
        applyUiTheme();
        setupSuggestions();
        browserBackButton.setAlpha(canGoBack ? 1f : 0.4f);
        browserForwardButton.setAlpha(canGoForward ? 1f : 0.4f);
        // Dark / OLED themes also ask websites for their dark style (prefers-color-scheme).
        sGeckoRuntime.getSettings().setPreferredColorScheme(ui.dark
                ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);

        Intent mIntent = getIntent();
        if (Intent.ACTION_VIEW.equals(mIntent.getAction()) && mIntent.getData() != null) {
            commitURL(mIntent.getData().toString());
        } else {
            commitURL(tabURLs[activeTab]);
        }

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                && mGeckoSession != null) {
            captureCurrentTabPreview();
            openInNewTab(intent.getData().toString());
        }
    }

    private void setupTabs() {

        SharedPreferences prefs = getSharedPreferences("dataPrefs", MODE_PRIVATE);
        String savedTabs = prefs.getString("tabURLs", "");
        if (savedTabs.startsWith("[")) {
            try {
                JSONArray savedArray = new JSONArray(savedTabs);
                tabURLs = new String[savedArray.length()];
                for (int i = 0; i < savedArray.length(); i++) {
                    tabURLs[i] = savedArray.optString(i, "");
                }
            } catch (JSONException e) {
                tabURLs = new String[]{""};
            }
        } else if (!savedTabs.isEmpty()) {
            String[] legacyTabs = savedTabs.split(", ");
            int length = legacyTabs.length;
            if (length > 1 && legacyTabs[length - 1].isEmpty()) {
                length--;
            }
            tabURLs = Arrays.copyOf(legacyTabs, Math.max(length, 1));
        } else {
            tabURLs = new String[]{""};
        }
        if (tabURLs.length == 0) {
            tabURLs = new String[]{""};
        }
        activeTab = prefs.getInt("activeTab", 0);
        if (activeTab < 0 || activeTab >= tabURLs.length) {
            activeTab = 0;
        }

//        <TextView
//        app:layout_rowWeight="1"
//        app:layout_columnWeight="1"
//        android:text="A"
//        android:textAppearance="?android:attr/textAppearanceLarge"
//        android:padding="30dp"
//        app:layout_column="0"
//        app:layout_row="0"
//        app:layout_gravity="fill" />


        TextView tabsCountView = findViewById(R.id.tabsCountView);
        tabsCountView.setOnClickListener(view -> showTabsDialog());
        refreshTabCount();
    }

    // ---- Full-screen tab switcher -------------------------------------------------------

    /** Card preview height relative to its width (portrait page crop). */
    private static final float PREVIEW_ASPECT = 1.2f;
    private static final int PREVIEW_WIDTH = 360;
    private View tabSwitcher;
    private LinearLayout tabSwitcherGrid;

    private boolean isTabSwitcherVisible() {
        return tabSwitcher != null && tabSwitcher.getVisibility() == View.VISIBLE;
    }

    private void showTabsDialog() {
        hideBottomMenu();
        if (urlEditText.hasFocus()) {
            urlEditText.clearFocus();
        }
        if (tabSwitcher == null) {
            tabSwitcher = findViewById(R.id.tabSwitcher);
            tabSwitcherGrid = findViewById(R.id.tabSwitcherGrid);
            findViewById(R.id.tabSwitcherBack).setOnClickListener(view -> hideTabSwitcher());
            findViewById(R.id.tabSwitcherNew).setOnClickListener(view -> {
                hideTabSwitcher();
                createNewTab();
            });
        }
        // Theme colours.
        tabSwitcher.setBackgroundColor(ui.sectionBackground);
        findViewById(R.id.tabSwitcherHeader).setBackgroundColor(ui.toolbar);
        ((TextView) findViewById(R.id.tabSwitcherTitle)).setTextColor(ui.toolbarIcon);
        ((ImageView) findViewById(R.id.tabSwitcherBack)).setColorFilter(ui.toolbarIcon);
        ((ImageView) findViewById(R.id.tabSwitcherNew)).setColorFilter(ui.toolbarIcon);

        renderTabSwitcher();
        tabSwitcher.setVisibility(View.VISIBLE);
        tabSwitcher.bringToFront();
        tabSwitcher.setAlpha(0f);
        tabSwitcher.animate().alpha(1f).setDuration(150).start();
        // Refresh the active tab's picture, then redraw its card.
        captureCurrentTabPreview(() -> {
            if (isTabSwitcherVisible()) {
                renderTabSwitcher();
            }
        });
    }

    private void hideTabSwitcher() {
        if (isTabSwitcherVisible()) {
            tabSwitcher.animate().alpha(0f).setDuration(120)
                    .withEndAction(() -> tabSwitcher.setVisibility(View.GONE)).start();
        }
    }

    private void renderTabSwitcher() {
        ((TextView) findViewById(R.id.tabSwitcherTitle))
                .setText(getString(R.string.tabs_with_count, tabURLs.length));
        tabSwitcherGrid.removeAllViews();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int columns = metrics.widthPixels > metrics.heightPixels ? 3 : 2;
        int gap = dp(6);
        int cardWidth = (metrics.widthPixels - dp(12) - gap * 2 * columns) / columns;
        LinearLayout row = null;
        for (int i = 0; i < tabURLs.length; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                tabSwitcherGrid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(gap, gap, gap, gap);
            row.addView(buildTabCard(i, cardWidth), params);
        }
        if (activeTab >= columns * 2) {
            // Keep the active tab in view.
            final int activeRow = activeTab / columns;
            tabSwitcherGrid.post(() -> {
                View target = tabSwitcherGrid.getChildAt(activeRow);
                if (target != null) {
                    ((ScrollView) findViewById(R.id.tabSwitcherScroll))
                            .scrollTo(0, Math.max(0, target.getTop() - dp(40)));
                }
            });
        }
    }

    private View buildTabCard(final int index, int cardWidth) {
        final String tabUrl = tabURLs[index];
        boolean active = index == activeTab;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int stroke = active ? dp(3) : Math.max(1, dp(1));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(ui.cardBackground);
        background.setCornerRadius(dp(6));
        background.setStroke(stroke, active ? ui.accent : ui.separator);
        card.setBackgroundDrawable(background);
        card.setPadding(stroke, stroke, stroke, stroke);

        // Title row: page title + close button.
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(tabLabel(tabUrl));
        title.setTextSize(14);
        title.setTextColor(ui.primaryText);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(10), 0, dp(2), 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton close = new ImageButton(this);
        close.setImageDrawable(androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.ic_nav_clear));
        close.setColorFilter(ui.secondaryText);
        close.setBackgroundResource(R.drawable.bottom_menu_item);
        close.setScaleType(ImageView.ScaleType.FIT_CENTER);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setContentDescription(getString(R.string.close_tab));
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        // Page picture, or the site name when there is none yet.
        int innerWidth = cardWidth - stroke * 2;
        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(ui.previewBackground);
        card.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(innerWidth * PREVIEW_ASPECT)));
        Bitmap picture = loadTabPreview(tabUrl, innerWidth);
        if (picture != null) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(picture);
            preview.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            TextView letter = new TextView(this);
            String host = tabDomain(tabUrl);
            letter.setText(host.isEmpty() ? "+" : host.substring(0, 1).toUpperCase(
                    java.util.Locale.getDefault()));
            letter.setTextSize(44);
            letter.setTextColor(ui.accent);
            letter.setGravity(Gravity.CENTER);
            preview.addView(letter, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        // Footer: domain.
        TextView domain = new TextView(this);
        domain.setText(tabDomain(tabUrl).isEmpty() ? getString(R.string.new_tab) : tabDomain(tabUrl));
        domain.setTextSize(12);
        domain.setTextColor(ui.secondaryText);
        domain.setSingleLine(true);
        domain.setEllipsize(android.text.TextUtils.TruncateAt.END);
        domain.setGravity(Gravity.CENTER_VERTICAL);
        domain.setPadding(dp(10), 0, dp(10), 0);
        card.addView(domain, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

        card.setClickable(true);
        card.setOnClickListener(view -> {
            if (index != activeTab) {
                captureCurrentTabPreview(null);
                selectTab(index);
            }
            hideTabSwitcher();
        });
        close.setOnClickListener(view -> card.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f)
                .setDuration(140).withEndAction(() -> closeTab(index)).start());
        return card;
    }

    /** Domain for card footers, without "www.". */
    private String tabDomain(String url) {
        if (url == null || url.isEmpty() || "about:blank".equals(url)) {
            return "";
        }
        String host = Uri.parse(url).getHost();
        if (host == null || host.isEmpty()) {
            return url;
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private Bitmap loadTabPreview(String url, int targetWidth) {
        File file = tabPreviewFile(url);
        if (!file.isFile()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inSampleSize = targetWidth > 0 && PREVIEW_WIDTH / targetWidth >= 2 ? 2 : 1;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void createNewTab() {
        openInNewTab(homepageForNewTabs ? INITIAL_URL : "about:blank");
    }

    private void openInNewTab(String url) {
        if (liveTabs) {
            ensureTabSessionSlots();
        }
        ArrayList<String> updatedTabs = new ArrayList<>(Arrays.asList(tabURLs));
        updatedTabs.add(url);
        tabURLs = updatedTabs.toArray(new String[0]);
        if (liveTabs) {
            tabSessions.add(null);
        }
        selectTab(tabURLs.length - 1);
        refreshTabCount();
    }

    private void closeTab(int index) {
        if (liveTabs) {
            closeLiveTab(index);
            refreshTabCount();
            if (isTabSwitcherVisible()) {
                renderTabSwitcher();
            }
            return;
        }
        if (tabURLs.length == 1) {
            tabURLs[0] = homepageForNewTabs ? INITIAL_URL : "about:blank";
            activeTab = 0;
            commitURL(tabURLs[0]);
        } else {
            ArrayList<String> updatedTabs = new ArrayList<>(Arrays.asList(tabURLs));
            updatedTabs.remove(index);
            tabURLs = updatedTabs.toArray(new String[0]);
            if (index < activeTab) {
                activeTab--;
            } else if (index == activeTab) {
                activeTab = Math.min(index, tabURLs.length - 1);
                commitURL(tabURLs[activeTab]);
            }
        }
        refreshTabCount();
        if (isTabSwitcherVisible()) {
            renderTabSwitcher();
        }
    }

    private void closeLiveTab(int index) {
        ensureTabSessionSlots();
        String home = homepageForNewTabs ? INITIAL_URL : "about:blank";
        if (tabURLs.length == 1) {
            tabURLs[0] = home;
            selectTab(0);
            commitURL(home);
            return;
        }
        GeckoSession closing = tabSessions.remove(index);
        ArrayList<String> updatedTabs = new ArrayList<>(Arrays.asList(tabURLs));
        updatedTabs.remove(index);
        tabURLs = updatedTabs.toArray(new String[0]);
        if (closing != null) {
            if (closing == mGeckoSession) {
                mGeckoView.releaseSession();
                mGeckoSession = null;
            }
            closing.close();
            tabStates.remove(closing);
        }
        if (index < activeTab) {
            activeTab--;
        } else if (index == activeTab) {
            selectTab(Math.min(index, tabURLs.length - 1));
        }
    }

    private String tabLabel(String url) {
        if (url == null || url.isEmpty() || "about:blank".equals(url)) {
            return getString(R.string.new_tab);
        }
        if (url.equals(currentPageUrl) && currentPageTitle != null
                && !currentPageTitle.trim().isEmpty()) {
            return currentPageTitle;
        }
        String saved = HistoryStore.get(this).titleFor(url);
        if (saved != null && !saved.trim().isEmpty()) {
            return saved;
        }
        String domain = tabDomain(url);
        return domain.isEmpty() ? url : domain;
    }

    private File tabPreviewFile(String url) {
        String key = Integer.toHexString(url == null ? 0 : url.hashCode());
        return new File(getCacheDir(), "tab-preview-" + key + ".jpg");
    }

    private void captureCurrentTabPreview() {
        captureCurrentTabPreview(null);
    }

    /** Saves a picture of the top of the current page; onSaved runs on the main thread. */
    private void captureCurrentTabPreview(final Runnable onSaved) {
        if (mGeckoView == null || currentPageUrl == null || currentPageUrl.isEmpty()
                || isPrivateMode()) {
            return;
        }
        final File outputFile = tabPreviewFile(currentPageUrl);
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            mGeckoView.capturePixels().accept(bitmap -> new Thread(() -> {
                // Crop the top of the page to the card's portrait shape instead of squashing it.
                int cropHeight = Math.min(bitmap.getHeight(),
                        Math.round(bitmap.getWidth() * PREVIEW_ASPECT));
                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), cropHeight);
                Bitmap thumbnail = Bitmap.createScaledBitmap(cropped, PREVIEW_WIDTH,
                        Math.round(PREVIEW_WIDTH * cropHeight / (float) bitmap.getWidth()), true);
                if (cropped != bitmap && cropped != thumbnail) {
                    cropped.recycle();
                }
                try (FileOutputStream output = new FileOutputStream(outputFile)) {
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 75, output);
                } catch (Exception e) {
                    Log.w("GeckoBrowser", "Unable to save tab preview", e);
                } finally {
                    if (thumbnail != bitmap) {
                        thumbnail.recycle();
                    }
                    bitmap.recycle();
                }
                if (onSaved != null) {
                    mainHandler.post(onSaved);
                }
            }).start(), error -> Log.d("GeckoBrowser", "Page preview failed", error));
        } catch (IllegalStateException e) {
            Log.d("GeckoBrowser", "Page preview is not available yet", e);
        }
    }

    /** Current light / dark / OLED palette; dialogs use the phone's own matching theme. */
    private UiTheme ui;

    private int dialogTheme() {
        return ui.dialogTheme();
    }

    /** Context with the device's theme, for list rows inside dialogs. */
    private android.content.Context dialogContext() {
        return new ContextThemeWrapper(this, ui.contextTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupSettings() {
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(this::toggleBrowserMenu);
        // Menu lives on the hardware menu key; show an on-screen button only without one.
        settingsButton.setVisibility(android.view.ViewConfiguration.get(this).hasPermanentMenuKey()
                ? View.GONE : View.VISIBLE);
        setupBottomMenu();

        SharedPreferences prefs = getSharedPreferences("settingsPrefs", MODE_PRIVATE);

//        if (cookieStoreId != null) {
//            settingsBuilder.contextId(cookieStoreId);
//        }
        settingsBuilder.allowJavascript(prefs.getBoolean("javascript", true));
        // Ad blocking needs the session-level filter switched on as well.
        settingsBuilder.useTrackingProtection(prefs.getBoolean("trackingProtection", true)
                || prefs.getBoolean("blockAds", false));
        settingsBuilder.usePrivateMode(prefs.getBoolean("privateMode", false));

        if (prefs.getBoolean("desktopMode", false)) {
            settingsBuilder.userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
            settingsBuilder.displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER);
            settingsBuilder.viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        } else {
            String customUserAgent = prefs.getString("userAgent", "");
            if (!customUserAgent.isEmpty()) {
                settingsBuilder.userAgentOverride(customUserAgent);
            }
            settingsBuilder.userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            settingsBuilder.displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER);
            settingsBuilder.viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }

        autoHideBar = prefs.getBoolean("autoHideBar", false);
        liveTabs = isLiveTabsMode(this);
        hideWhiteSplash = prefs.getBoolean("hideWhiteSplash", false);
        searchSuggestions = prefs.getBoolean("searchSuggestions", true);

        if (!prefs.getString("searchEngine", "").equals("")) {
            SEARCH_URI_BASE = prefs.getString("searchEngine", "https://duckduckgo.com/?q=");
        }
        String configuredHomepage = prefs.getString("homepage", "").trim();
        if (!configuredHomepage.isEmpty()) {
            INITIAL_URL = configuredHomepage.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
                    ? configuredHomepage : "https://" + configuredHomepage;
        }
        homepageForNewTabs = prefs.getBoolean("homepageForNewTabs", true);
    }

    private void setupGeckoView() {
        mGeckoView = findViewById(R.id.geckoView);
        if (sGeckoRuntime == null) {
            sGeckoRuntime = GeckoRuntime.create(getApplicationContext(), buildRuntimeSettings());
            applyLiteExtension();
            applyAdBlocker();
        }
        sslIndicatorView = findViewById(R.id.sslIndicatorView);
        sslIndicatorView.setVisibility(View.GONE);
        sslIndicatorView.setOnClickListener(view -> new AlertDialog.Builder(this, dialogTheme())
            .setTitle(R.string.connection_security)
            .setMessage(sslStatusMessage == null ? getString(R.string.ssl_unknown)
                : sslStatusMessage)
            .setPositiveButton(android.R.string.ok, null)
            .show());
        openGeckoSession();
    }

    /**
     * Old GPUs/drivers (e.g. Adreno 3xx on Android 4.x) crash while compiling
     * hardware WebRender shaders in the :gpu process, which Android reports as
     * "app has stopped". Use software WebRender there instead.
     */
    private GeckoRuntimeSettings buildRuntimeSettings() {
        // GeckoView ignores configFilePath below API 21, so engine prefs go into user.js.
        // Every pref is written in both modes so switching lite mode off restores it.
        boolean lite = isLiteMode(this);
        StringBuilder prefs = new StringBuilder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            prefs.append(userPref("gfx.webrender.software", "true"));
            // GeckoView 118 on Android 4.4: touch scrolling (APZ) does nothing when the
            // compositor lives in the separate :gpu process. Keep it in the main process.
            prefs.append(userPref("layers.gpu-process.enabled", "false"));
        }
        // Sites that honour prefers-reduced-motion drop their own animations.
        prefs.append(userPref("ui.prefersReducedMotion", lite ? "1" : "0"));
        // Animated GIF/APNG play once instead of looping forever.
        prefs.append(userPref("image.animation_mode", lite ? "\"once\"" : "\"normal\""));
        // 5 = block all autoplay (also muted video), 1 = block audible autoplay only.
        prefs.append(userPref("media.autoplay.default", lite ? "5" : "1"));
        // No VP9/AV1 in MSE: YouTube & co. fall back to H.264, which old phones decode in hardware.
        prefs.append(userPref("media.mediasource.webm.enabled", lite ? "false" : "true"));
        prefs.append(userPref("media.av1.enabled", lite ? "false" : "true"));
        // Don't keep whole pages of back/forward history alive in RAM.
        prefs.append(userPref("browser.sessionhistory.max_total_viewers", lite ? "0" : "-1"));
        // Fewer intermediate reflows while a page is still loading.
        prefs.append(userPref("content.notify.interval", lite ? "500000" : "120000"));
        prefs.append(userPref("network.prefetch-next", lite ? "false" : "true"));
        prefs.append(userPref("general.smoothScroll", lite ? "false" : "true"));
        prefs.append(userPref("layout.css.backdrop-filter.enabled", lite ? "false" : "true"));
        writeGeckoUserPrefs(prefs.toString());
        return new GeckoRuntimeSettings.Builder().build();
    }

    /** Live tabs by default on Android 5+; classic (one loaded page) on older phones. */
    static boolean isLiveTabsMode(android.content.Context context) {
        String mode = context.getSharedPreferences("settingsPrefs", MODE_PRIVATE)
                .getString("tabMode", "");
        if (mode.isEmpty()) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        }
        return "live".equals(mode);
    }

    static boolean isLiteMode(android.content.Context context) {
        return context.getSharedPreferences("settingsPrefs", MODE_PRIVATE).getBoolean("liteMode",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
    }

    private static String userPref(String name, String value) {
        return "user_pref(\"" + name + "\", " + value + ");\n";
    }

    private static final String UBLOCK_ID = "uBlock0@raymondhill.net";
    /**
     * Pinned to 1.67.0: newer releases add browser_specific_settings.gecko
     * .data_collection_permissions, which GeckoView 118 rejects as a corrupt package.
     * The filter lists still update on their own.
     */
    private static final String UBLOCK_URL =
            "https://addons.mozilla.org/firefox/downloads/file/4598854/ublock_origin-1.67.0.xpi";

    /**
     * Optional ad blocking with uBlock Origin from addons.mozilla.org (signed by Mozilla).
     * Ads and their scripts are the main cause of scroll lag on many streaming sites.
     */
    private void applyAdBlocker() {
        final boolean blockAds = getSharedPreferences("settingsPrefs", MODE_PRIVATE)
                .getBoolean("blockAds", false);
        // Gecko's own block lists (Mozilla / Disconnect): ad, analytics, social, cryptomining
        // and fingerprinting hosts. Works everywhere, including Android 4.4. CONTENT is left
        // out on purpose: it can block embedded video players.
        sGeckoRuntime.getSettings().getContentBlocking().setAntiTracking(blockAds
                ? org.mozilla.geckoview.ContentBlocking.AntiTracking.AD
                        | org.mozilla.geckoview.ContentBlocking.AntiTracking.ANALYTIC
                        | org.mozilla.geckoview.ContentBlocking.AntiTracking.SOCIAL
                        | org.mozilla.geckoview.ContentBlocking.AntiTracking.CRYPTOMINING
                        | org.mozilla.geckoview.ContentBlocking.AntiTracking.FINGERPRINTING
                : org.mozilla.geckoview.ContentBlocking.AntiTracking.DEFAULT);
        // uBlock Origin on top where GeckoView accepts AMO packages (it rejects them on
        // Android 4.4 with InstallException -3).
        final boolean wanted = blockAds && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        final org.mozilla.geckoview.WebExtensionController controller =
                sGeckoRuntime.getWebExtensionController();
        controller.setPromptDelegate(new org.mozilla.geckoview.WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<org.mozilla.geckoview.AllowOrDeny> onInstallPrompt(
                    org.mozilla.geckoview.WebExtension extension) {
                // Only the ad blocker the user switched on in settings may install itself.
                return GeckoResult.fromValue(UBLOCK_ID.equals(extension.id)
                        ? org.mozilla.geckoview.AllowOrDeny.ALLOW
                        : org.mozilla.geckoview.AllowOrDeny.DENY);
            }

            @Override
            public GeckoResult<org.mozilla.geckoview.AllowOrDeny> onUpdatePrompt(
                    org.mozilla.geckoview.WebExtension current,
                    org.mozilla.geckoview.WebExtension updated, String[] newPermissions,
                    String[] newOrigins) {
                return GeckoResult.fromValue(UBLOCK_ID.equals(current.id)
                        ? org.mozilla.geckoview.AllowOrDeny.ALLOW
                        : org.mozilla.geckoview.AllowOrDeny.DENY);
            }
        });
        controller.list().accept(extensions -> {
            org.mozilla.geckoview.WebExtension installed = null;
            if (extensions != null) {
                for (org.mozilla.geckoview.WebExtension extension : extensions) {
                    if (UBLOCK_ID.equals(extension.id)) {
                        installed = extension;
                    }
                }
            }
            if (wanted && installed == null) {
                resolveAdBlockerUrl(url -> controller.install(url).accept(
                        extension -> Toast.makeText(this, R.string.ad_blocker_installed,
                                Toast.LENGTH_SHORT).show(),
                        error -> {
                            Log.w("GeckoBrowser", "Ad blocker install failed: " + url, error);
                            Toast.makeText(this, R.string.ad_blocker_failed,
                                    Toast.LENGTH_LONG).show();
                        }));
            } else if (!wanted && installed != null) {
                controller.uninstall(installed);
            }
        });
    }

    /** Plain callback: Consumer and friends only exist from Android 7. */
    private interface UrlCallback {
        void accept(String url);
    }

    /** Fetches the pinned package through Gecko and installs it from a local file. */
    private void resolveAdBlockerUrl(final UrlCallback onUrl) {
        if (webExecutor == null) {
            webExecutor = new org.mozilla.geckoview.GeckoWebExecutor(sGeckoRuntime);
        }
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String local = downloadToFile(UBLOCK_URL, new File(getFilesDir(), "ublock-origin.xpi"));
            final String resolved = local != null ? local : UBLOCK_URL;
            main.post(() -> onUrl.accept(resolved));
        }).start();
    }

    /** Blocking download through Gecko's network stack; returns a file:// URI or null. */
    private String downloadToFile(String url, File target) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final WebResponse[] holder = new WebResponse[1];
        new Handler(Looper.getMainLooper()).post(() -> webExecutor.fetch(
                new org.mozilla.geckoview.WebRequest(url)).accept(response -> {
                    holder[0] = response;
                    done.countDown();
                }, error -> done.countDown()));
        try {
            done.await(60, java.util.concurrent.TimeUnit.SECONDS);
            WebResponse response = holder[0];
            if (response == null || response.body == null || response.statusCode >= 400) {
                return null;
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = response.body.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            response.body.close();
            Log.d("GeckoBrowser", "Ad blocker package saved: " + target.length() + " bytes");
            return Uri.fromFile(target).toString();
        } catch (Exception e) {
            Log.w("GeckoBrowser", "Ad blocker package download failed", e);
            return null;
        }
    }

    private void applyLiteExtension() {
        org.mozilla.geckoview.WebExtensionController controller =
                sGeckoRuntime.getWebExtensionController();
        if (isLiteMode(this)) {
            controller.ensureBuiltIn("resource://android/assets/lite/", LITE_EXTENSION_ID)
                    .accept(extension -> Log.d("GeckoBrowser", "Lite extension active"),
                            error -> Log.w("GeckoBrowser", "Lite extension failed", error));
        } else {
            controller.list().accept(extensions -> {
                if (extensions == null) {
                    return;
                }
                for (org.mozilla.geckoview.WebExtension extension : extensions) {
                    if (LITE_EXTENSION_ID.equals(extension.id)) {
                        controller.uninstall(extension);
                    }
                }
            });
        }
    }

    private void writeGeckoUserPrefs(String prefs) {
        File mozillaDir = new File(getFilesDir(), "mozilla");
        File profilesIni = new File(mozillaDir, "profiles.ini");
        try {
            String profilePath = null;
            if (profilesIni.isFile()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(profilesIni))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("Path=")) {
                            profilePath = line.substring(5).trim();
                            break;
                        }
                    }
                }
            }
            if (profilePath == null || profilePath.isEmpty()) {
                profilePath = "geckobrowser.default";
                if (!mozillaDir.isDirectory() && !mozillaDir.mkdirs()) {
                    return;
                }
                writeTextFile(profilesIni, "[Profile0]\nName=default\nIsRelative=1\nPath="
                        + profilePath + "\nDefault=1\n\n[General]\nStartWithLastProfile=1\nVersion=2\n");
            }
            File profileDir = new File(mozillaDir, profilePath);
            if (!profileDir.isDirectory() && !profileDir.mkdirs()) {
                return;
            }
            File userJs = new File(profileDir, "user.js");
            if (!prefs.equals(readTextFile(userJs))) {
                writeTextFile(userJs, prefs);
            }
        } catch (Exception e) {
            Log.w("GeckoBrowser", "Unable to write Gecko user prefs", e);
        }
    }

    private static String readTextFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                new java.io.FileInputStream(file), "UTF-8")) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                text.append(buffer, 0, read);
            }
        } catch (java.io.IOException e) {
            return null;
        }
        return text.toString();
    }

    private static void writeTextFile(File file, String text) throws java.io.IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes("UTF-8"));
        }
    }

    private void openGeckoSession() {
        if (sRetainedSession != null && sRetainedSession.isOpen()) {
            mGeckoSession = sRetainedSession;
        } else {
            mGeckoSession = new GeckoSession(settingsBuilder.build());
            sRetainedSession = mGeckoSession;
        }
        configureSession(mGeckoSession);

        if (!mGeckoSession.isOpen()) {
            mGeckoSession.open(sGeckoRuntime);
        }
        try {
            mGeckoView.setSession(mGeckoSession);
        } catch (IllegalStateException e) {
            // Retained session still attached elsewhere: start with a fresh one.
            Log.w("GeckoBrowser", "Retained session unavailable", e);
            sRetainedSession = null;
            openGeckoSession();
            return;
        }
        if (sGeckoMediaSession != null && sGeckoMediaSession.isActive()) {
            geckoMediaSession = sGeckoMediaSession;
            androidMediaSession.setActive(true);
            publishMediaMetadata(null);
            publishMediaPlaybackState();
        }
    }

    /** Attaches all browser delegates; callbacks only touch the toolbar for the visible tab. */
    private void configureSession(final GeckoSession target) {
        target.setProgressDelegate(new createProgressDelegate());
        target.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                if (session == mGeckoSession) {
                    setBrowserFullScreen(fullScreen);
                }
            }

            @Override
            public void onTitleChange(GeckoSession session, String title) {
                TabState state = tabState(session);
                state.title = title;
                if (!isPrivateMode()) {
                    HistoryStore.get(MainActivity.this).updateTitle(state.url, title);
                }
                if (session == mGeckoSession) {
                    currentPageTitle = title;
                }
            }

            @Override
            public void onExternalResponse(GeckoSession session, WebResponse response) {
                enqueueDownload(response);
            }

            @Override
            public void onContextMenu(GeckoSession session, int screenX, int screenY,
                    ContextElement element) {
                if (session == mGeckoSession) {
                    showContextMenu(element);
                }
            }
        });
        target.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean value) {
                tabState(session).canGoBack = value;
                if (session == mGeckoSession) {
                    canGoBack = value;
                    updateNavButtons();
                }
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean value) {
                tabState(session).canGoForward = value;
                if (session == mGeckoSession) {
                    canGoForward = value;
                    updateNavButtons();
                }
            }

            /** target="_blank" links and window.open(): without this nothing happens. */
            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                return openNewWindow(uri);
            }

            @Override
            public void onLocationChange(GeckoSession session, String url,
                    java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
                // Fires for redirects and in-page navigation (history.pushState),
                // which onPageStart does not report.
                if (url == null || url.isEmpty()) {
                    return;
                }
                TabState state = tabState(session);
                boolean changed = !url.equals(state.url);
                state.url = url;
                int index = tabIndexOf(session);
                if (index >= 0 && tabURLs != null && index < tabURLs.length) {
                    tabURLs[index] = url;
                }
                if (session != mGeckoSession) {
                    if (changed && !state.loading) {
                        recordHistory(url);
                    }
                    return;
                }
                if (url.equals(currentPageUrl)) {
                    return;
                }
                currentPageUrl = url;
                updateBookmarkButton();
                if (!pageLoading) {
                    // In-page navigation (history.pushState); full loads are recorded on stop.
                    recordHistory(url);
                }
                if (!urlEditText.hasFocus()) {
                    urlEditText.setText(displayUrl(url));
                }
                if (!url.regionMatches(true, 0, "https://", 0, 8)) {
                    updateSslIndicator(url, null);
                }
            }
        });
        target.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session,
                    ChoicePrompt prompt) {
                return showChoicePrompt(prompt);
            }
        });
        target.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override
            public void onActivated(GeckoSession session, MediaSession mediaSession) {
                geckoMediaSession = mediaSession;
                sGeckoMediaSession = mediaSession;
                mediaFeatures = MediaSession.Feature.PLAY | MediaSession.Feature.PAUSE
                        | MediaSession.Feature.STOP;
                mediaPlaybackState = PlaybackStateCompat.STATE_PAUSED;
                requestMusicAudioFocus();
                androidMediaSession.setActive(true);
                publishMediaPlaybackState();
                publishMediaMetadata(null);
                showMediaNotification();
            }

            @Override
            public void onDeactivated(GeckoSession session, MediaSession mediaSession) {
                if (geckoMediaSession == mediaSession) {
                    geckoMediaSession = null;
                    sGeckoMediaSession = null;
                    mediaFeatures = MediaSession.Feature.NONE;
                    mediaPlaybackState = PlaybackStateCompat.STATE_NONE;
                    androidMediaSession.setActive(false);
                    publishMediaPlaybackState();
                    dismissMediaNotification();
                    releaseMusicAudioFocus();
                }
            }

            @Override
            public void onMetadata(GeckoSession session, MediaSession mediaSession,
                    MediaSession.Metadata metadata) {
                mediaTitle = metadata.title;
                mediaArtist = metadata.artist;
                publishMediaMetadata(metadata);
                showMediaNotification();
            }

            @Override
            public void onFeatures(GeckoSession session, MediaSession mediaSession,
                    long features) {
                mediaFeatures = features;
                publishMediaPlaybackState();
            }

            @Override
            public void onPositionState(GeckoSession session, MediaSession mediaSession,
                    MediaSession.PositionState state) {
                mediaPositionMs = Math.max(0, Math.round(state.position * 1000));
                mediaDurationMs = Math.max(0, Math.round(state.duration * 1000));
                publishMediaMetadata(null);
                publishMediaPlaybackState();
                showMediaNotification();
            }

            @Override
            public void onPlay(GeckoSession session, MediaSession mediaSession) {
                if (!requestMusicAudioFocus()) {
                    mediaSession.pause();
                    return;
                }
                mediaPlaybackState = PlaybackStateCompat.STATE_PLAYING;
                publishMediaPlaybackState();
                showMediaNotification();
            }

            @Override
            public void onPause(GeckoSession session, MediaSession mediaSession) {
                mediaPlaybackState = PlaybackStateCompat.STATE_PAUSED;
                publishMediaPlaybackState();
                showMediaNotification();
            }

            @Override
            public void onStop(GeckoSession session, MediaSession mediaSession) {
                mediaPlaybackState = PlaybackStateCompat.STATE_STOPPED;
                publishMediaPlaybackState();
                showMediaNotification();
                releaseMusicAudioFocus();
            }
        });
        target.setScrollDelegate(new GeckoSession.ScrollDelegate() {
            @Override
            public void onScrollChanged(GeckoSession session, int scrollX, int scrollY) {
                if (session == mGeckoSession) {
                    onPageScrolled(scrollY);
                }
            }
        });
    }

    /**
     * Live tabs: hand Gecko a fresh (unopened) session so the page keeps its link to the
     * opener (window.opener, postMessage); it becomes a new tab. Classic mode has only one
     * loaded page, so the address simply opens in a new tab.
     */
    private GeckoResult<GeckoSession> openNewWindow(String uri) {
        if (!liveTabs) {
            final String target = uri == null || uri.isEmpty() ? "about:blank" : uri;
            new Handler(Looper.getMainLooper()).post(() -> openInNewTab(target));
            return null;
        }
        ensureTabSessionSlots();
        GeckoSession created = new GeckoSession(settingsBuilder.build());
        configureSession(created);
        tabState(created).url = uri;
        ArrayList<String> updatedTabs = new ArrayList<>(Arrays.asList(tabURLs));
        updatedTabs.add(uri == null ? "about:blank" : uri);
        tabURLs = updatedTabs.toArray(new String[0]);
        tabSessions.add(created);
        refreshTabCount();
        showNewWindowWhenOpen(created, 0);
        return GeckoResult.fromValue(created);
    }

    /** Gecko opens the returned session itself; switch to it once that has happened. */
    private void showNewWindowWhenOpen(final GeckoSession created, final int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            int index = tabIndexOf(created);
            if (index < 0) {
                return; // closed meanwhile
            }
            if (created.isOpen() || attempt >= 40) {
                // After ~2 s without Gecko opening it, selectTab opens and loads it itself.
                captureCurrentTabPreview(null);
                selectTab(index);
            } else {
                showNewWindowWhenOpen(created, attempt + 1);
            }
        }, 50);
    }

    private TabState tabState(GeckoSession session) {
        TabState state = tabStates.get(session);
        if (state == null) {
            state = new TabState();
            tabStates.put(session, state);
        }
        return state;
    }

    private int tabIndexOf(GeckoSession session) {
        if (!liveTabs) {
            return session == mGeckoSession ? activeTab : -1;
        }
        for (int i = 0; i < tabSessions.size(); i++) {
            if (tabSessions.get(i) == session) {
                return i;
            }
        }
        return -1;
    }

    private void updateNavButtons() {
        if (browserBackButton != null) {
            browserBackButton.setEnabled(canGoBack);
            browserBackButton.setAlpha(canGoBack ? 1f : 0.4f);
        }
        if (browserForwardButton != null) {
            browserForwardButton.setEnabled(canGoForward);
            browserForwardButton.setAlpha(canGoForward ? 1f : 0.4f);
        }
    }

    private void ensureTabSessionSlots() {
        while (tabSessions.size() < tabURLs.length) {
            tabSessions.add(null);
        }
        while (tabSessions.size() > tabURLs.length) {
            GeckoSession extra = tabSessions.remove(tabSessions.size() - 1);
            if (extra != null && extra != mGeckoSession) {
                extra.close();
                tabStates.remove(extra);
            }
        }
    }

    /**
     * Shows tab {@code index}. Classic mode reloads its address in the single session;
     * live mode swaps in that tab's own session, so its page stays exactly where it was.
     */
    private void selectTab(int index) {
        index = Math.max(0, Math.min(index, tabURLs.length - 1));
        activeTab = index;
        if (!liveTabs) {
            commitURL(tabURLs[index]);
            return;
        }
        ensureTabSessionSlots();
        GeckoSession target = tabSessions.get(index);
        boolean fresh = target == null || !target.isOpen();
        if (fresh) {
            target = new GeckoSession(settingsBuilder.build());
            configureSession(target);
            target.open(sGeckoRuntime);
            tabSessions.set(index, target);
        }
        if (target != mGeckoSession) {
            if (browserFullScreen && mGeckoSession != null) {
                mGeckoSession.exitFullScreen();
                setBrowserFullScreen(false);
            }
            mGeckoView.releaseSession();
            mGeckoSession = target;
            sRetainedSession = target;
            mGeckoView.setSession(target);
        }
        restoreUiFromState(tabState(target));
        if (fresh) {
            commitURL(tabURLs[index]);
        }
    }

    private void restoreUiFromState(TabState state) {
        currentPageUrl = state.url;
        currentPageTitle = state.title;
        pageLoading = state.loading;
        canGoBack = state.canGoBack;
        canGoForward = state.canGoForward;
        updateNavButtons();
        if (!urlEditText.hasFocus()) {
            urlEditText.setText(displayUrl(state.url));
        }
        updateSslIndicator(state.url, state.security);
        applyPageProgress(state.loading ? state.progress : 100);
        updateBookmarkButton();
        showToolbarForAutoHide();
    }

    /** Long-press menu for images and links. */
    private void showContextMenu(final GeckoSession.ContentDelegate.ContextElement element) {
        final boolean isImage = element.type
                == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                && element.srcUri != null;
        final String link = element.linkUri;
        if (!isImage && link == null) {
            return;
        }
        final ArrayList<String> labels = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        if (link != null) {
            labels.add(getString(R.string.context_open_new_tab));
            actions.add(() -> openInNewTab(link));
            labels.add(getString(R.string.context_copy_link));
            actions.add(() -> copyToClipboard(link));
        }
        if (isImage) {
            labels.add(getString(R.string.context_save_image));
            actions.add(() -> downloadUrl(element.srcUri));
            labels.add(getString(R.string.context_copy_image));
            actions.add(() -> copyImageToClipboard(element.srcUri));
            labels.add(getString(R.string.context_open_image));
            actions.add(() -> openInNewTab(element.srcUri));
        }
        labels.add(getString(R.string.share_page));
        final String shareTarget = isImage && link == null ? element.srcUri : link;
        actions.add(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareTarget),
                getString(R.string.share_page))));
        String title = element.title != null && !element.title.isEmpty() ? element.title
                : element.altText != null && !element.altText.isEmpty() ? element.altText
                : isImage && link == null ? element.srcUri : link;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    @SuppressWarnings("deprecation")
    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * Copies the picture itself: fetched through Gecko, cached in the app's cache and shared
     * via a content:// URI so other apps can paste it; the address is included as text too.
     */
    private void copyImageToClipboard(final String imageUrl) {
        if (webExecutor == null) {
            webExecutor = new org.mozilla.geckoview.GeckoWebExecutor(sGeckoRuntime);
        }
        final Handler main = new Handler(Looper.getMainLooper());
        webExecutor.fetch(new org.mozilla.geckoview.WebRequest.Builder(imageUrl)
                        .referrer(currentPageUrl).build())
                .accept(response -> new Thread(() -> {
                    Uri contentUri = null;
                    try {
                        File dir = new File(getCacheDir(), "clipboard");
                        dir.mkdirs();
                        String mime = response.headers.get("Content-Type");
                        String ext = mime == null ? null : android.webkit.MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(mime.split(";")[0].trim());
                        File file = new File(dir, "image." + (ext == null ? "png" : ext));
                        try (FileOutputStream out = new FileOutputStream(file)) {
                            byte[] buffer = new byte[32 * 1024];
                            int read;
                            while ((read = response.body.read(buffer)) > 0) {
                                out.write(buffer, 0, read);
                            }
                        }
                        response.body.close();
                        contentUri = androidx.core.content.FileProvider.getUriForFile(
                                MainActivity.this, getPackageName() + ".files", file);
                    } catch (Exception e) {
                        Log.w("GeckoBrowser", "Unable to copy image", e);
                    }
                    final Uri uri = contentUri;
                    main.post(() -> {
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (uri != null) {
                            android.content.ClipData clip = android.content.ClipData.newUri(
                                    getContentResolver(), "image", uri);
                            clip.addItem(new android.content.ClipData.Item(imageUrl));
                            clipboard.setPrimaryClip(clip);
                        } else {
                            clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("image", imageUrl));
                        }
                        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                    });
                }).start(), error -> copyToClipboard(imageUrl));
    }

    /** Native list dialog for HTML &lt;select&gt; (single, multiple) and menus. */
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> showChoicePrompt(
            GeckoSession.PromptDelegate.ChoicePrompt prompt) {
        final GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = new GeckoResult<>();
        final ArrayList<GeckoSession.PromptDelegate.ChoicePrompt.Choice> choices = new ArrayList<>();
        final ArrayList<Integer> depths = new ArrayList<>();
        flattenChoices(prompt.choices, 0, choices, depths);
        if (choices.isEmpty()) {
            result.complete(prompt.dismiss());
            return result;
        }
        final boolean multiple =
                prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE;
        final boolean single =
                prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.SINGLE;
        final boolean[] done = {false};

        int rowLayout = multiple ? android.R.layout.simple_list_item_multiple_choice
                : single ? android.R.layout.simple_list_item_single_choice
                : android.R.layout.simple_list_item_1;
        ArrayAdapter<GeckoSession.PromptDelegate.ChoicePrompt.Choice> adapter =
                new ArrayAdapter<GeckoSession.PromptDelegate.ChoicePrompt.Choice>(
                        dialogContext(), rowLayout, choices) {
            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(int position) {
                GeckoSession.PromptDelegate.ChoicePrompt.Choice choice = getItem(position);
                return !choice.disabled && !choice.separator && choice.items == null;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int position) {
                GeckoSession.PromptDelegate.ChoicePrompt.Choice choice = getItem(position);
                return choice.items != null || choice.separator ? 1 : 0;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                GeckoSession.PromptDelegate.ChoicePrompt.Choice choice = getItem(position);
                if (getItemViewType(position) == 1) {
                    // Optgroup header or separator: plain row, no radio/checkbox.
                    TextView header = convertView instanceof TextView
                            ? (TextView) convertView : new TextView(getContext());
                    if (choice.separator) {
                        header.setText("");
                        header.setMinHeight(0);
                        header.setHeight(dp(1));
                        header.setPadding(0, 0, 0, 0);
                        header.setBackgroundColor(ui.separator);
                    } else {
                        header.setText(choice.label);
                        header.setTextSize(14);
                        header.setTextColor(ui.accent);
                        header.setTypeface(null, android.graphics.Typeface.BOLD);
                        header.setBackgroundColor(ui.sectionBackground);
                        header.setMinHeight(dp(36));
                        header.setGravity(Gravity.CENTER_VERTICAL);
                        header.setPadding(dp(16 + 16 * depths.get(position)), dp(6), dp(16), dp(6));
                    }
                    return header;
                }
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(choice.label);
                view.setEnabled(isEnabled(position));
                view.setAlpha(isEnabled(position) ? 1f : 0.4f);
                view.setPadding(dp(16 + 16 * depths.get(position)), view.getPaddingTop(),
                        dp(16), view.getPaddingBottom());
                return view;
            }
        };

        ListView listView = new ListView(dialogContext());
        listView.setAdapter(adapter);
        listView.setChoiceMode(multiple ? ListView.CHOICE_MODE_MULTIPLE
                : single ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
        int firstSelected = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).selected && choices.get(i).items == null) {
                listView.setItemChecked(i, true);
                if (firstSelected < 0) {
                    firstSelected = i;
                }
            }
        }
        if (firstSelected > 0) {
            listView.setSelection(Math.max(0, firstSelected - 2));
        }

        String title = prompt.title != null && !prompt.title.isEmpty() ? prompt.title
                : prompt.message;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogTheme()).setView(listView);
        if (title != null && !title.isEmpty()) {
            builder.setTitle(title);
        }
        if (multiple) {
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, (d, which) -> {
                ArrayList<String> ids = new ArrayList<>();
                android.util.SparseBooleanArray checked = listView.getCheckedItemPositions();
                for (int i = 0; i < choices.size(); i++) {
                    if (checked.get(i) && choices.get(i).items == null) {
                        ids.add(choices.get(i).id);
                    }
                }
                done[0] = true;
                result.complete(prompt.confirm(ids.toArray(new String[0])));
            });
        }
        final AlertDialog dialog = builder.create();
        if (!multiple) {
            listView.setOnItemClickListener((parent, view, position, id) -> {
                done[0] = true;
                result.complete(prompt.confirm(choices.get(position)));
                dialog.dismiss();
            });
        }
        dialog.setOnDismissListener(d -> {
            if (!done[0]) {
                done[0] = true;
                result.complete(prompt.dismiss());
            }
        });
        // The page can close the <select> itself (e.g. navigation, script).
        prompt.setDelegate(new GeckoSession.PromptDelegate.PromptInstanceDelegate() {
            @Override
            public void onPromptDismiss(GeckoSession.PromptDelegate.BasePrompt basePrompt) {
                done[0] = true;
                dialog.dismiss();
            }
        });
        dialog.show();
        return result;
    }

    private static void flattenChoices(GeckoSession.PromptDelegate.ChoicePrompt.Choice[] source,
            int depth, ArrayList<GeckoSession.PromptDelegate.ChoicePrompt.Choice> out,
            ArrayList<Integer> depths) {
        if (source == null) {
            return;
        }
        for (GeckoSession.PromptDelegate.ChoicePrompt.Choice choice : source) {
            out.add(choice);
            depths.add(depth);
            if (choice.items != null) {
                flattenChoices(choice.items, depth + 1, out, depths);
            }
        }
    }

    private void showMediaNotification() {
        if (androidMediaSession == null || geckoMediaSession == null || !androidMediaSession.isActive()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (!mediaNotificationPermissionRequested) {
                mediaNotificationPermissionRequested = true;
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            }
            return;
        }

        createMediaNotificationChannel();
        boolean playing = mediaPlaybackState == PlaybackStateCompat.STATE_PLAYING;
        int actionIcon = playing ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        String actionTitle = getString(playing ? R.string.pause_playback : R.string.playback_play);
        int actionKeyCode = playing ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, MEDIA_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(mediaTitle == null || mediaTitle.trim().isEmpty()
                        ? (currentPageUrl == null ? getString(R.string.app_name) : currentPageUrl)
                        : mediaTitle)
                .setContentText(mediaArtist == null || mediaArtist.trim().isEmpty()
                        ? getString(R.string.app_name) : mediaArtist)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setContentIntent(getMediaSessionActivityIntent())
                .addAction(actionIcon, actionTitle, buildMediaAction(actionKeyCode, 10))
                .setStyle(new MediaStyle()
                        .setMediaSession(androidMediaSession.getSessionToken())
                    .setShowActionsInCompactView(0));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        notificationManager.notify(MEDIA_NOTIFICATION_ID, notification);
        Intent serviceIntent = new Intent(this, BrowserPlaybackService.class)
            .setAction(BrowserPlaybackService.ACTION_START)
            .putExtra(BrowserPlaybackService.EXTRA_NOTIFICATION, notification);
        if (!playbackServiceStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                playbackServiceStarted = true;
            } catch (RuntimeException e) {
                // e.g. ForegroundServiceStartNotAllowedException while in background (API 31+)
                Log.w("GeckoBrowser", "Unable to start playback service", e);
            }
        }
    }

    private void createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager.getNotificationChannel(MEDIA_NOTIFICATION_CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(
                        MEDIA_NOTIFICATION_CHANNEL,
                        getString(R.string.media_playback_channel),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                channel.setShowBadge(false);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent buildMediaAction(int keyCode, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON)
                .setClass(this, MediaButtonEventReceiver.class)
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    private PendingIntent getMediaSessionActivityIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private void dismissMediaNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(MEDIA_NOTIFICATION_ID);
        releaseMusicAudioFocus();
        stopService(new Intent(this, BrowserPlaybackService.class)
                .setAction(BrowserPlaybackService.ACTION_STOP));
        playbackServiceStarted = false;
    }

    private boolean requestMusicAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
        return audioManager.requestAudioFocus(AUDIO_FOCUS_CHANGE_LISTENER,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void releaseMusicAudioFocus() {
        if (audioManager != null) {
            audioManager.abandonAudioFocus(AUDIO_FOCUS_CHANGE_LISTENER);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_STORAGE_PERMISSION_REQUEST) {
            WebResponse response = pendingDownloadResponse;
            pendingDownloadResponse = null;
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                    && response != null) {
                enqueueDownload(response);
            } else if (response != null) {
                closeResponseBody(response);
                Toast.makeText(this, R.string.download_storage_permission_denied,
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showMediaNotification();
        }
    }

    private void setupAndroidMediaSession() {
        if (sAndroidMediaSession == null) {
            Intent receiverIntent = new Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClass(getApplicationContext(), MediaButtonEventReceiver.class);
            int receiverFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_MUTABLE : 0;
            // Explicit receiver PendingIntent: the implicit one in androidx.media 1.0
            // has no mutability flag and crashes on Android 12+.
            sAndroidMediaSession = new MediaSessionCompat(getApplicationContext(), "GeckoBrowser",
                    new android.content.ComponentName(getApplicationContext(),
                            MediaButtonEventReceiver.class),
                    PendingIntent.getBroadcast(getApplicationContext(), 1, receiverIntent,
                            receiverFlags));
            sAndroidMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            sAndroidMediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        }
        androidMediaSession = sAndroidMediaSession;
        androidMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (geckoMediaSession != null) {
                    geckoMediaSession.play();
                }
            }

            @Override
            public void onPause() {
                if (geckoMediaSession != null) {
                    geckoMediaSession.pause();
                }
            }

            @Override
            public void onStop() {
                if (geckoMediaSession != null) {
                    geckoMediaSession.stop();
                }
            }

            @Override
            public void onSeekTo(long position) {
                if (geckoMediaSession != null
                        && (mediaFeatures & MediaSession.Feature.SEEK_TO) != 0) {
                    geckoMediaSession.seekTo(position / 1000.0, false);
                }
            }
        });
        Intent openBrowserIntent = new Intent(this, MainActivity.class);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        androidMediaSession.setSessionActivity(PendingIntent.getActivity(
                this, 0, openBrowserIntent, pendingIntentFlags));
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(this, MediaButtonEventReceiver.class);
        int mediaButtonPendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaButtonPendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }
        androidMediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(
            this, 1, mediaButtonIntent, mediaButtonPendingIntentFlags));
        MediaButtonEventReceiver.mediaSession = androidMediaSession;
        if (sGeckoMediaSession == null || !sGeckoMediaSession.isActive()) {
            androidMediaSession.setActive(false);
        } else {
            androidMediaSession.setActive(true);
        }
    }

    private void publishMediaMetadata(MediaSession.Metadata metadata) {
        if (androidMediaSession == null || geckoMediaSession == null) {
            return;
        }
        String title = metadata == null ? mediaTitle : metadata.title;
        String artist = metadata == null ? mediaArtist : metadata.artist;
        if (title == null || title.trim().isEmpty()) {
            title = currentPageUrl == null ? getString(R.string.app_name) : currentPageUrl;
        }
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        artist == null || artist.isEmpty() ? getString(R.string.app_name) : artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaDurationMs);
        androidMediaSession.setMetadata(builder.build());
    }

    private void publishMediaPlaybackState() {
        if (androidMediaSession == null) {
            return;
        }
        long actions = 0;
        if ((mediaFeatures & MediaSession.Feature.PLAY) != 0) {
            actions |= PlaybackStateCompat.ACTION_PLAY;
        }
        if ((mediaFeatures & MediaSession.Feature.PAUSE) != 0) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        if ((mediaFeatures & (MediaSession.Feature.PLAY | MediaSession.Feature.PAUSE))
                == (MediaSession.Feature.PLAY | MediaSession.Feature.PAUSE)) {
            actions |= PlaybackStateCompat.ACTION_PLAY_PAUSE;
        }
        if ((mediaFeatures & MediaSession.Feature.STOP) != 0) {
            actions |= PlaybackStateCompat.ACTION_STOP;
        }
        if ((mediaFeatures & MediaSession.Feature.SEEK_TO) != 0) {
            actions |= PlaybackStateCompat.ACTION_SEEK_TO;
        }
        androidMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(mediaPlaybackState, mediaPositionMs, 1.0f,
                        SystemClock.elapsedRealtime())
                .build());
    }

    private static final int MENU_NEW_TAB = 10;
    private static final int MENU_HISTORY = 11;
    private static final int MENU_SHARE = 12;
    private static final int MENU_FULLSCREEN = 13;
    private static final int MENU_COLUMNS = 4;
    /** id, icon, label for the bottom menu grid. */
    private static final int[][] BOTTOM_MENU_ITEMS = {
            {MENU_NEW_TAB, R.drawable.ic_menu_new_tab, R.string.menu_new_tab},
            {MENU_ADD_FAVORITE, R.drawable.ic_menu_add_bookmark, R.string.menu_add_bookmark},
            {MENU_SHOW_FAVORITES, R.drawable.ic_menu_bookmarks, R.string.menu_bookmarks},
            {MENU_HISTORY, R.drawable.ic_menu_history, R.string.menu_history},
            {MENU_DOWNLOADS, R.drawable.ic_menu_downloads, R.string.menu_downloads},
            {MENU_SHARE, R.drawable.ic_menu_share, R.string.menu_share},
            {MENU_DESKTOP_MODE, R.drawable.ic_menu_desktop, R.string.menu_desktop},
            {MENU_FULLSCREEN, R.drawable.ic_menu_fullscreen, R.string.menu_fullscreen},
            {MENU_CLEAR_SITE_DATA, R.drawable.ic_menu_clear_data, R.string.menu_clear_data},
            {MENU_DEFAULT_BROWSER, R.drawable.ic_menu_default_browser, R.string.menu_default_browser},
            {MENU_SETTINGS, R.drawable.ic_menu_settings, R.string.menu_settings},
            {MENU_EXIT, R.drawable.ic_menu_exit, R.string.menu_exit},
    };
    private boolean pageLoading;
    private View menuOverlay;
    private View bottomMenuScroll;
    private LinearLayout bottomMenuPanel;
    private View desktopMenuCheck;
    private View fullscreenMenuCheck;

    /** Menu as a list rising from the bottom: icon + label rows separated by thin lines. */
    private void setupBottomMenu() {
        menuOverlay = findViewById(R.id.menuOverlay);
        bottomMenuScroll = findViewById(R.id.bottomMenuScroll);
        bottomMenuPanel = findViewById(R.id.bottomMenuPanel);
        menuOverlay.setOnClickListener(view -> hideBottomMenu());
        bottomMenuPanel.removeAllViews();
        bottomMenuPanel.setBackgroundColor(ui.menuBackground);
        for (int i = 0; i < BOTTOM_MENU_ITEMS.length; i++) {
            final int[] item = BOTTOM_MENU_ITEMS[i];
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(ui.menuDivider);
                bottomMenuPanel.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bottom_menu_item);
            row.setPadding(dp(16), 0, dp(14), 0);
            row.setClickable(true);
            row.setFocusable(true);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(this, item[1]));
            icon.setColorFilter(ui.menuIcon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

            TextView label = new TextView(this);
            label.setText(item[2]);
            label.setTextSize(18);
            label.setTextColor(ui.menuText);
            label.setSingleLine(true);
            label.setPadding(dp(18), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (item[0] == MENU_DESKTOP_MODE) {
                desktopMenuCheck = new View(this);
                row.addView(desktopMenuCheck, new LinearLayout.LayoutParams(dp(22), dp(22)));
            } else if (item[0] == MENU_FULLSCREEN) {
                fullscreenMenuCheck = new View(this);
                row.addView(fullscreenMenuCheck, new LinearLayout.LayoutParams(dp(22), dp(22)));
            }
            row.setOnClickListener(view -> {
                hideBottomMenu();
                performMenuAction(item[0]);
            });
            bottomMenuPanel.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        }
    }

    private boolean isBottomMenuVisible() {
        return menuOverlay != null && menuOverlay.getVisibility() == View.VISIBLE;
    }

    private void toggleBrowserMenu(View ignored) {
        if (isBottomMenuVisible()) {
            hideBottomMenu();
        } else {
            showBottomMenu();
        }
    }

    private void showBottomMenu() {
        hideKeyboard(this);
        boolean desktop = getSharedPreferences("settingsPrefs", MODE_PRIVATE)
                .getBoolean("desktopMode", false);
        styleMenuCheck(desktopMenuCheck, desktop);
        styleMenuCheck(fullscreenMenuCheck, fullscreenMode);
        menuOverlay.setVisibility(View.VISIBLE);
        menuOverlay.bringToFront();
        bottomMenuScroll.scrollTo(0, 0);
        bottomMenuScroll.post(() -> {
            bottomMenuScroll.setTranslationY(bottomMenuScroll.getHeight());
            bottomMenuScroll.animate().translationY(0).setDuration(180).start();
        });
    }

    /** Draws the small square that marks an active menu toggle (desktop, fullscreen). */
    private void styleMenuCheck(View check, boolean checked) {
        if (check == null) {
            return;
        }
        android.graphics.drawable.GradientDrawable box =
                new android.graphics.drawable.GradientDrawable();
        box.setStroke(Math.max(1, dp(1)), checked ? ui.accent : ui.menuIcon);
        box.setColor(checked ? ui.accent : 0x00000000);
        check.setBackgroundDrawable(box);
    }

    private void hideBottomMenu() {
        if (!isBottomMenuVisible()) {
            return;
        }
        bottomMenuScroll.animate().translationY(bottomMenuScroll.getHeight()).setDuration(140)
                .withEndAction(() -> menuOverlay.setVisibility(View.GONE)).start();
    }

    /** Colours every piece of browser chrome from the selected light / dark / OLED palette. */
    private void applyUiTheme() {
        findViewById(R.id.mainRelativeLayout).setBackgroundColor(ui.pageBackground);
        // Private mode: purple bar so it is always obvious.
        findViewById(R.id.toolbarView).setBackgroundColor(isPrivateMode()
                ? (ui.dark ? 0xFF2B1F45 : 0xFF4B3B6E) : ui.toolbar);
        findViewById(R.id.toolbarDivider1).setBackgroundColor(ui.toolbarDivider);
        findViewById(R.id.toolbarDivider2).setBackgroundColor(ui.toolbarDivider);
        int[] toolbarIcons = {R.id.browserBackButton, R.id.browserForwardButton,
                R.id.tabsCountViewImage, R.id.bookmarksButton, R.id.settingsButton};
        for (int id : toolbarIcons) {
            ((ImageView) findViewById(id)).setColorFilter(ui.toolbarIcon);
        }
        ((TextView) findViewById(R.id.tabsCountView)).setTextColor(ui.toolbarIcon);
        updateBookmarkButton();

        android.graphics.drawable.GradientDrawable field =
                new android.graphics.drawable.GradientDrawable();
        field.setColor(ui.addressBackground);
        field.setStroke(Math.max(1, dp(1)), ui.addressBorder);
        field.setCornerRadius(dp(3));
        findViewById(R.id.addressField).setBackgroundDrawable(field);
        urlEditText.setTextColor(ui.addressText);
        urlEditText.setHintTextColor(ui.addressHint);
        ((ImageView) findViewById(R.id.reloadButton)).setColorFilter(ui.addressIcon);
        // Tint only the filled part; tinting the whole LayerDrawable also painted the track
        // in the accent colour, so the bar always looked 100% loaded.
        android.graphics.drawable.Drawable progress = mProgressView.getProgressDrawable();
        if (progress instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers =
                    (android.graphics.drawable.LayerDrawable) progress;
            android.graphics.drawable.Drawable track =
                    layers.findDrawableByLayerId(android.R.id.background);
            android.graphics.drawable.Drawable fill =
                    layers.findDrawableByLayerId(android.R.id.progress);
            if (track != null) {
                track.setColorFilter(ui.toolbarDivider, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (fill != null) {
                fill.setColorFilter(ui.accent, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private void performMenuAction(int id) {
        switch (id) {
            case MENU_NEW_TAB:
                captureCurrentTabPreview();
                createNewTab();
                break;
            case MENU_RELOAD:
                mGeckoSession.reload();
                break;
            case MENU_DESKTOP_MODE:
                toggleDesktopMode();
                break;
            case MENU_FULLSCREEN:
                setFullscreenMode(!fullscreenMode);
                break;
            case MENU_ADD_FAVORITE:
                addCurrentPageToFavorites();
                break;
            case MENU_SHOW_FAVORITES:
                showFavorites();
                break;
            case MENU_HISTORY:
                showHistory();
                break;
            case MENU_DOWNLOADS:
                showDownloads();
                break;
            case MENU_SHARE:
                shareCurrentPage();
                break;
            case MENU_DEFAULT_BROWSER:
                showDefaultBrowserSettings();
                break;
            case MENU_CLEAR_SITE_DATA:
                confirmClearSiteData();
                break;
            case MENU_SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case MENU_EXIT:
                exitBrowser();
                break;
            default:
                break;
        }
    }

    private void shareCurrentPage() {
        if (currentPageUrl == null || !HistoryStore.isRecordable(currentPageUrl)) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, currentPageUrl);
        if (currentPageTitle != null) {
            share.putExtra(Intent.EXTRA_SUBJECT, currentPageTitle);
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_page)));
    }

    private void recordHistory(String url) {
        if (!isPrivateMode()) {
            HistoryStore.get(this).recordVisit(url, null);
        }
    }

    private boolean isPrivateMode() {
        return getSharedPreferences("settingsPrefs", MODE_PRIVATE).getBoolean("privateMode", false);
    }

    private void showHistory() {
        final java.util.List<HistoryStore.Entry> entries = HistoryStore.get(this).recent(500);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this, dialogTheme())
                    .setTitle(R.string.menu_history)
                    .setMessage(R.string.history_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        // Flatten into day headers (String) and entries.
        final ArrayList<Object> rows = new ArrayList<>();
        String lastDay = null;
        for (HistoryStore.Entry entry : entries) {
            String day = historyDayLabel(entry.visited);
            if (!day.equals(lastDay)) {
                rows.add(day);
                lastDay = day;
            }
            rows.add(entry);
        }
        final android.content.Context themed = dialogContext();
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) {
                return rows.get(position) instanceof String ? 0 : 1;
            }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) {
                return rows.get(position) instanceof HistoryStore.Entry;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                Object row = rows.get(position);
                if (row instanceof String) {
                    TextView header = convertView instanceof TextView
                            ? (TextView) convertView : new TextView(themed);
                    header.setText((String) row);
                    header.setTextSize(13);
                    header.setTypeface(null, android.graphics.Typeface.BOLD);
                    header.setTextColor(ui.accent);
                    header.setBackgroundColor(ui.sectionBackground);
                    header.setPadding(dp(14), dp(6), dp(14), dp(6));
                    return header;
                }
                HistoryStore.Entry entry = (HistoryStore.Entry) row;
                View view = convertView != null ? convertView : android.view.LayoutInflater
                        .from(themed).inflate(android.R.layout.simple_list_item_2, parent, false);
                TextView title = view.findViewById(android.R.id.text1);
                TextView url = view.findViewById(android.R.id.text2);
                title.setText(entry.title == null || entry.title.isEmpty() ? entry.url : entry.title);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                url.setText(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(new java.util.Date(entry.visited)) + "  " + entry.url);
                url.setSingleLine(true);
                url.setEllipsize(android.text.TextUtils.TruncateAt.END);
                return view;
            }
        };
        ListView list = new ListView(themed);
        list.setAdapter(adapter);
        final AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.menu_history)
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.clear_history, (d, which) ->
                        new AlertDialog.Builder(this, dialogTheme())
                                .setMessage(R.string.clear_history_confirmation)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.clear, (d2, w2) ->
                                        HistoryStore.get(this).clear())
                                .show())
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            commitURL(((HistoryStore.Entry) rows.get(position)).url);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            final HistoryStore.Entry entry = (HistoryStore.Entry) rows.get(position);
            new AlertDialog.Builder(this, dialogTheme())
                    .setMessage(entry.url)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove_history_entry, (d, which) -> {
                        HistoryStore.get(this).delete(entry.id);
                        dialog.dismiss();
                        list.postDelayed(this::showHistory, 150);
                    })
                    .show();
            return true;
        });
        dialog.show();
    }

    private String historyDayLabel(long time) {
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar day = java.util.Calendar.getInstance();
        day.setTimeInMillis(time);
        int diff = (today.get(java.util.Calendar.YEAR) - day.get(java.util.Calendar.YEAR)) * 366
                + today.get(java.util.Calendar.DAY_OF_YEAR) - day.get(java.util.Calendar.DAY_OF_YEAR);
        if (diff == 0) {
            return getString(R.string.history_today);
        }
        if (diff == 1) {
            return getString(R.string.history_yesterday);
        }
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
                .format(new java.util.Date(time));
    }

    private JSONArray readFavorites() {
        String savedFavorites = getSharedPreferences("dataPrefs", MODE_PRIVATE)
                .getString("favorites", "[]");
        try {
            return new JSONArray(savedFavorites);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** Gold bookmark icon marks pages already in favorites. */
    private static final int FAVORITE_ICON_COLOR = 0xFFFFC21A;

    private boolean isFavorite(String url) {
        if (url == null) {
            return false;
        }
        JSONArray favorites = readFavorites();
        for (int i = 0; i < favorites.length(); i++) {
            JSONObject favorite = favorites.optJSONObject(i);
            if (favorite != null && url.equals(favorite.optString("url"))) {
                return true;
            }
        }
        return false;
    }

    private void updateBookmarkButton() {
        if (bookmarksButton == null || ui == null) {
            return;
        }
        boolean favorite = isFavorite(currentPageUrl);
        bookmarksButton.setColorFilter(favorite ? FAVORITE_ICON_COLOR : ui.toolbarIcon);
        bookmarksButton.setContentDescription(getString(favorite
                ? R.string.remove_favorite : R.string.add_favorite));
    }

    private void toggleCurrentFavorite() {
        if (isFavorite(currentPageUrl)) {
            removeFavorite(currentPageUrl);
        } else {
            addCurrentPageToFavorites();
        }
    }

    private void addCurrentPageToFavorites() {
        String pageUrl = currentPageUrl;
        if (pageUrl == null || !(pageUrl.startsWith("http://") || pageUrl.startsWith("https://"))) {
            Toast.makeText(this, R.string.favorite_requires_webpage, Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray favorites = readFavorites();
        for (int i = 0; i < favorites.length(); i++) {
            JSONObject favorite = favorites.optJSONObject(i);
            if (favorite != null && pageUrl.equals(favorite.optString("url"))) {
                Toast.makeText(this, R.string.favorite_already_saved, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String title = currentPageTitle == null || currentPageTitle.trim().isEmpty()
                ? pageUrl : currentPageTitle.trim();
        JSONObject favorite = new JSONObject();
        try {
            favorite.put("title", title);
            favorite.put("url", pageUrl);
            favorites.put(favorite);
            getSharedPreferences("dataPrefs", MODE_PRIVATE).edit()
                    .putString("favorites", favorites.toString()).apply();
            Toast.makeText(this, R.string.favorite_added, Toast.LENGTH_SHORT).show();
            updateBookmarkButton();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.favorite_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFavorites() {
        JSONArray favorites = readFavorites();
        if (favorites.length() == 0) {
            new AlertDialog.Builder(this, dialogTheme())
                    .setTitle(R.string.show_favorites)
                    .setMessage(R.string.favorites_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < favorites.length(); i++) {
            JSONObject favorite = favorites.optJSONObject(i);
            if (favorite != null) {
                labels.add(favorite.optString("title", favorite.optString("url"))
                        + "\n" + favorite.optString("url"));
            }
        }

        ListView favoritesList = new ListView(dialogContext());
        favoritesList.setAdapter(new ArrayAdapter<>(dialogContext(),
                android.R.layout.simple_list_item_1, labels));
        AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.show_favorites)
                .setView(favoritesList)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        favoritesList.setOnItemClickListener((parent, view, position, id) -> {
            JSONObject favorite = favorites.optJSONObject(position);
            if (favorite != null) {
                dialog.dismiss();
                commitURL(favorite.optString("url", ""));
            }
        });
        favoritesList.setOnItemLongClickListener((parent, view, position, id) -> {
            JSONObject favorite = favorites.optJSONObject(position);
            if (favorite == null) {
                return true;
            }
            new AlertDialog.Builder(this, dialogTheme())
                    .setMessage(getString(R.string.remove_favorite_confirmation,
                            favorite.optString("title", favorite.optString("url"))))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove_favorite,
                            (removeDialog, which) -> {
                                removeFavorite(favorite.optString("url"));
                                dialog.dismiss();
                                showFavorites();
                            })
                    .show();
            return true;
        });
        dialog.show();
    }

    private void removeFavorite(String url) {
        JSONArray currentFavorites = readFavorites();
        JSONArray updatedFavorites = new JSONArray();
        for (int i = 0; i < currentFavorites.length(); i++) {
            JSONObject favorite = currentFavorites.optJSONObject(i);
            if (favorite != null && !url.equals(favorite.optString("url"))) {
                updatedFavorites.put(favorite);
            }
        }
        getSharedPreferences("dataPrefs", MODE_PRIVATE).edit()
                .putString("favorites", updatedFavorites.toString()).apply();
        Toast.makeText(this, R.string.favorite_removed, Toast.LENGTH_SHORT).show();
        updateBookmarkButton();
    }

    /**
     * Saves a download straight from Gecko's own connection. Handing the URL to the system
     * DownloadManager made it fetch the file a second time without the page's cookies and,
     * on Android 4.x, with an outdated TLS stack and certificate store, so most current
     * sites failed.
     */
    private void enqueueDownload(WebResponse response) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Keep the stream open; it is saved once the permission is granted.
            pendingDownloadResponse = response;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    DOWNLOAD_STORAGE_PERMISSION_REQUEST);
            return;
        }
        String contentType = response.headers.get("Content-Type");
        String mime = contentType == null ? null : contentType.split(";")[0].trim();
        long total = -1;
        try {
            String length = response.headers.get("Content-Length");
            if (length != null) {
                total = Long.parseLong(length.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        if (response.body == null) {
            Toast.makeText(this, R.string.download_start_failed, Toast.LENGTH_LONG).show();
            return;
        }
        startStreamDownload(response.uri, downloadFileName(response), mime, response.body, total);
    }

    /** In-progress downloads: temp id -> {status, downloaded, total}. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[]> sActiveDownloads =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void startStreamDownload(final String url, final String requestedName,
            final String mime, final java.io.InputStream body, final long total) {
        final long tempId = -System.currentTimeMillis();
        final String fileName = uniqueDownloadName(requestedName);
        sActiveDownloads.put(tempId, new long[]{DownloadManager.STATUS_RUNNING, 0, total});
        addRecentDownload(tempId, fileName, url, null);
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        if (downloadsDialogVisible) {
            renderDownloads();
        }
        final android.content.Context appContext = getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String savedUri = null;
            long finalId = tempId;
            long written = 0;
            java.io.OutputStream output = null;
            File targetFile = null;
            Uri mediaUri = null;
            Uri legacyMediaUri = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    if (mime != null) {
                        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                    }
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
                    mediaUri = appContext.getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    output = appContext.getContentResolver().openOutputStream(mediaUri);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.isDirectory()) {
                        dir.mkdirs();
                    }
                    targetFile = new File(dir, fileName);
                    try {
                        output = new FileOutputStream(targetFile);
                    } catch (java.io.FileNotFoundException denied) {
                        Log.w("GeckoBrowser", "Direct write denied, saving through MediaProvider: "
                                + denied.getMessage());
                        // Some ROMs (sdcardfs without derived permissions) only let system
                        // services write to shared storage. MediaProvider runs as one of them,
                        // so let it create and open the file for us.
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.MediaColumns.DATA,
                                targetFile.getAbsolutePath());
                        values.put(android.provider.MediaStore.MediaColumns.TITLE, fileName);
                        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        if (mime != null) {
                            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                        }
                        legacyMediaUri = appContext.getContentResolver().insert(
                                android.provider.MediaStore.Files.getContentUri("external"), values);
                        if (legacyMediaUri == null) {
                            throw denied;
                        }
                        output = appContext.getContentResolver().openOutputStream(legacyMediaUri);
                    }
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = body.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    written += read;
                    sActiveDownloads.put(tempId,
                            new long[]{DownloadManager.STATUS_RUNNING, written, total});
                }
                output.close();
                output = null;
                if (mediaUri != null) {
                    android.content.ContentValues done = new android.content.ContentValues();
                    done.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                    appContext.getContentResolver().update(mediaUri, done, null, null);
                    savedUri = mediaUri.toString();
                } else {
                    // Register with the system so it shows in Downloads with a notification.
                    DownloadManager manager =
                            (DownloadManager) appContext.getSystemService(DOWNLOAD_SERVICE);
                    finalId = manager.addCompletedDownload(fileName, url, true,
                            mime == null ? "application/octet-stream" : mime,
                            targetFile.getAbsolutePath(), written, true);
                }
                sActiveDownloads.put(tempId,
                        new long[]{DownloadManager.STATUS_SUCCESSFUL, written, written});
            } catch (Exception e) {
                Log.e("GeckoBrowser", "Download failed: " + url, e);
                sActiveDownloads.put(tempId,
                        new long[]{DownloadManager.STATUS_FAILED, written, total});
                if (legacyMediaUri != null) {
                    appContext.getContentResolver().delete(legacyMediaUri, null, null);
                } else if (targetFile != null) {
                    targetFile.delete();
                }
                if (mediaUri != null) {
                    appContext.getContentResolver().delete(mediaUri, null, null);
                }
            } finally {
                try {
                    body.close();
                } catch (java.io.IOException ignored) {
                }
                if (output != null) {
                    try {
                        output.close();
                    } catch (java.io.IOException ignored) {
                    }
                }
            }
            final boolean ok = sActiveDownloads.get(tempId)[0] == DownloadManager.STATUS_SUCCESSFUL;
            final long resultId = finalId;
            final String resultUri = savedUri;
            main.post(() -> {
                if (ok) {
                    replaceRecentDownload(tempId, resultId, resultUri);
                    sActiveDownloads.remove(tempId);
                }
                Toast.makeText(appContext, ok
                        ? getString(R.string.download_finished, fileName)
                        : getString(R.string.download_failed), Toast.LENGTH_LONG).show();
                if (downloadsDialogVisible) {
                    renderDownloads();
                }
            });
        }, "download-" + fileName).start();
    }

    /** Avoids overwriting: "file.pdf" -> "file-1.pdf" when the name is taken. */
    private String uniqueDownloadName(String name) {
        if (Build.VERSION.SDK_INT >= 29) {
            return name; // MediaStore picks a free name itself.
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!new File(dir, name).exists()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            String candidate = base + "-" + i + ext;
            if (!new File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis() + ext;
    }

    private void addRecentDownload(long id, String title, String url, String uri) {
        try {
            JSONArray downloads = readRecentDownloads();
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("title", title);
            item.put("url", url);
            item.put("created", System.currentTimeMillis());
            if (uri != null) {
                item.put("uri", uri);
            }
            JSONArray updated = new JSONArray();
            updated.put(item);
            for (int i = 0; i < downloads.length() && updated.length() < MAX_RECENT_DOWNLOADS; i++) {
                updated.put(downloads.get(i));
            }
            saveRecentDownloads(updated);
        } catch (JSONException e) {
            Log.w("GeckoBrowser", "Unable to record download", e);
        }
    }

    private void replaceRecentDownload(long tempId, long newId, String uri) {
        JSONArray downloads = readRecentDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && item.optLong("id") == tempId) {
                try {
                    item.put("id", newId);
                    if (uri != null) {
                        item.put("uri", uri);
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        saveRecentDownloads(downloads);
    }

    /** Saves an image (or any URL) through Gecko's network stack, e.g. from the long-press menu. */
    private void downloadUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (url.startsWith("data:")) {
            saveDataUrl(url);
            return;
        }
        if (webExecutor == null) {
            webExecutor = new org.mozilla.geckoview.GeckoWebExecutor(sGeckoRuntime);
        }
        org.mozilla.geckoview.WebRequest request = new org.mozilla.geckoview.WebRequest.Builder(url)
                .referrer(currentPageUrl)
                .build();
        webExecutor.fetch(request, org.mozilla.geckoview.GeckoWebExecutor.FETCH_FLAGS_NONE)
                .accept(response -> {
                    if (response == null || response.statusCode >= 400) {
                        Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    enqueueDownload(response);
                }, error -> Toast.makeText(this, R.string.download_failed,
                        Toast.LENGTH_LONG).show());
    }

    private void saveDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return;
        }
        String meta = dataUrl.substring(5, comma);
        String mime = meta.split(";")[0];
        byte[] bytes = meta.contains(";base64")
                ? android.util.Base64.decode(dataUrl.substring(comma + 1), android.util.Base64.DEFAULT)
                : Uri.decode(dataUrl.substring(comma + 1)).getBytes();
        String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        startStreamDownload(currentPageUrl, "image-" + System.currentTimeMillis()
                        + (ext == null ? "" : "." + ext), mime,
                new java.io.ByteArrayInputStream(bytes), bytes.length);
    }

    private String downloadFileName(WebResponse response) {
        String disposition = response.headers.get("Content-Disposition");
        if (disposition != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("filename\\*?=(?:UTF-8''|\\\")?([^;\\\"]+)",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(disposition);
            if (matcher.find()) {
                String candidate = Uri.decode(matcher.group(1).trim());
                if (!candidate.isEmpty()) {
                    return sanitizeFileName(candidate);
                }
            }
        }
        String path = Uri.parse(response.uri).getLastPathSegment();
        return sanitizeFileName(path == null || path.isEmpty()
                ? getString(R.string.download_file_fallback) : path);
    }

    private String sanitizeFileName(String name) {
        String safeName = name.replaceAll("[/\\\\\\p{Cntrl}]", "_").trim();
        return safeName.isEmpty() ? getString(R.string.download_file_fallback) : safeName;
    }

    private void closeResponseBody(WebResponse response) {
        if (response.body != null) {
            try {
                response.body.close();
            } catch (java.io.IOException e) {
                Log.w("GeckoBrowser", "Unable to close download response", e);
            }
        }
    }

    private JSONArray readRecentDownloads() {
        try {
            return new JSONArray(getSharedPreferences("dataPrefs", MODE_PRIVATE)
                    .getString("recentDownloads", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveRecentDownloads(JSONArray downloads) {
        getSharedPreferences("dataPrefs", MODE_PRIVATE).edit()
                .putString("recentDownloads", downloads.toString()).apply();
    }

    private void showDownloads() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        downloadRows = rows;
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(10);
        rows.setPadding(padding, padding, padding, padding);
        scrollView.addView(rows);
        downloadsDialog = new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.downloads)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        downloadsDialog.setOnShowListener(dialog -> {
            downloadsDialogVisible = true;
            renderDownloads();
        });
        downloadsDialog.setOnDismissListener(dialog -> {
            downloadsDialogVisible = false;
            downloadsDialog = null;
            downloadRows = null;
            downloadsHandler.removeCallbacks(downloadsRefreshRunnable);
        });
        downloadsDialog.show();
    }

    private final Runnable downloadsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (downloadsDialogVisible) {
                renderDownloads();
                downloadsHandler.postDelayed(this, 1500);
            }
        }
    };

    private void renderDownloads() {
        if (downloadsDialog == null || !downloadsDialogVisible) {
            return;
        }
        LinearLayout rows = downloadRows;
        if (rows == null) {
            return;
        }
        downloadsHandler.removeCallbacks(downloadsRefreshRunnable);
        rows.removeAllViews();
        JSONArray downloads = readRecentDownloads();
        if (downloads.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.downloads_empty);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            rows.addView(empty);
        }

        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        java.util.HashMap<Long, long[]> states = queryDownloadStates(manager, downloads);
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null) {
                long id = item.optLong("id", -1);
                long[] state = id < 0 ? sActiveDownloads.get(id) : states.get(id);
                if (state == null && item.has("uri")) {
                    state = new long[]{DownloadManager.STATUS_SUCCESSFUL, 0, 0};
                }
                rows.addView(createDownloadRow(manager, item, state == null
                        ? new long[]{DownloadManager.STATUS_FAILED, 0, -1} : state));
            }
        }
        downloadsHandler.postDelayed(downloadsRefreshRunnable, 1500);
    }

    private java.util.HashMap<Long, long[]> queryDownloadStates(
            DownloadManager manager, JSONArray downloads) {
        java.util.HashMap<Long, long[]> states = new java.util.HashMap<>();
        ArrayList<Long> validIds = new ArrayList<>();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            long id = item == null ? -1 : item.optLong("id", -1);
            if (id >= 0) {
                validIds.add(id);
            }
        }
        if (validIds.isEmpty()) {
            return states;
        }
        long[] ids = new long[validIds.size()];
        for (int i = 0; i < validIds.size(); i++) {
            ids[i] = validIds.get(i);
        }
        try (android.database.Cursor cursor = manager.query(
                new DownloadManager.Query().setFilterById(ids))) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadedColumn = cursor.getColumnIndex(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                while (cursor.moveToNext()) {
                    states.put(cursor.getLong(idColumn), new long[]{
                            cursor.getLong(statusColumn), cursor.getLong(downloadedColumn),
                            cursor.getLong(totalColumn)});
                }
            }
        } catch (Exception e) {
            Log.w("GeckoBrowser", "Unable to query recent downloads", e);
        }
        return states;
    }

    private View createDownloadRow(DownloadManager manager, JSONObject item, long[] status) {
        long id = item.optLong("id", -1);
        String title = item.optString("title", getString(R.string.download_file_fallback));
        long downloaded = status[1];
        long total = status[2];
        long speed = estimateDownloadSpeed(id, downloaded);
        long elapsedMs = Math.max(0, System.currentTimeMillis() - item.optLong("created",
            System.currentTimeMillis()));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundResource(R.drawable.address_bar_background);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(ui.primaryText);
        titleView.setMaxLines(1);
        row.addView(titleView);

        TextView detailView = new TextView(this);
        detailView.setText(downloadDetail(status[0], downloaded, total, speed, elapsedMs));
        detailView.setTextSize(12);
        detailView.setTextColor(ui.secondaryText);
        row.addView(detailView);

        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        if (total > 0 && status[0] == DownloadManager.STATUS_RUNNING) {
            progress.setIndeterminate(false);
            progress.setProgress((int) Math.min(1000, downloaded * 1000 / total));
        } else {
            progress.setIndeterminate(status[0] == DownloadManager.STATUS_PENDING
                    || status[0] == DownloadManager.STATUS_RUNNING
                    || status[0] == DownloadManager.STATUS_PAUSED);
            progress.setProgress(status[0] == DownloadManager.STATUS_SUCCESSFUL ? 1000 : 0);
        }
        row.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));

        row.setOnClickListener(view -> {
            if (status[0] == DownloadManager.STATUS_SUCCESSFUL) {
                Uri fileUri = item.has("uri") ? Uri.parse(item.optString("uri"))
                        : id >= 0 ? manager.getUriForDownloadedFile(id) : null;
                if (fileUri != null) {
                    Intent openIntent = new Intent(Intent.ACTION_VIEW).setDataAndType(fileUri,
                            item.has("uri") ? getContentResolver().getType(fileUri)
                                    : manager.getMimeTypeForDownloadedFile(id));
                    openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(openIntent);
                    } catch (android.content.ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.download_no_handler, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        row.setOnLongClickListener(view -> {
            new AlertDialog.Builder(this, dialogTheme())
                    .setMessage(getString(R.string.remove_download_confirmation, title))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove_download, (dialog, which) -> {
                        if (id >= 0) {
                            manager.remove(id);
                        } else if (item.has("uri")) {
                            getContentResolver().delete(Uri.parse(item.optString("uri")), null, null);
                        }
                        removeRecentDownload(id);
                        renderDownloads();
                    })
                    .show();
            return true;
        });
        return row;
    }

    private long estimateDownloadSpeed(long id, long downloaded) {
        long now = android.os.SystemClock.elapsedRealtime();
        long[] sample = downloadSamples.get(id);
        downloadSamples.put(id, new long[]{downloaded, now});
        if (sample == null || now <= sample[1] || downloaded < sample[0]) {
            return 0;
        }
        return (downloaded - sample[0]) * 1000 / (now - sample[1]);
    }

        private String downloadDetail(long status, long downloaded, long total, long speed,
            long elapsedMs) {
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            return getString(R.string.download_complete_size, formatBytes(downloaded));
        }
        if (status == DownloadManager.STATUS_FAILED) {
            return getString(R.string.download_failed);
        }
        if (status == DownloadManager.STATUS_PENDING) {
            return getString(R.string.download_pending);
        }
        if (status == DownloadManager.STATUS_PAUSED) {
            return getString(R.string.download_paused, formatBytes(downloaded));
        }
        String sizeText = total > 0
                ? getString(R.string.download_size_pair, formatBytes(downloaded), formatBytes(total))
                : getString(R.string.download_size_unknown, formatBytes(downloaded));
        String speedText = speed > 0 ? getString(R.string.download_speed, formatBytes(speed)) : "";
        String etaText = total > downloaded && speed > 0
                ? getString(R.string.download_eta, formatDuration((total - downloaded) / speed)) : "";
        String elapsedText = status == DownloadManager.STATUS_RUNNING
            ? getString(R.string.download_elapsed, formatDuration(elapsedMs / 1000)) : "";
        return sizeText + "  " + speedText + "  " + etaText + "  " + elapsedText;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB"};
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit]);
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return Math.max(seconds, 1) + "s";
    }

    private void removeRecentDownload(long id) {
        JSONArray oldItems = readRecentDownloads();
        JSONArray keptItems = new JSONArray();
        for (int i = 0; i < oldItems.length(); i++) {
            JSONObject item = oldItems.optJSONObject(i);
            if (item != null && item.optLong("id", -1) != id) {
                keptItems.put(item);
            }
        }
        getSharedPreferences("dataPrefs", MODE_PRIVATE).edit()
                .putString("recentDownloads", keptItems.toString()).apply();
    }

    private void showDefaultBrowserSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(
                        android.app.role.RoleManager.ROLE_BROWSER), 1045);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                return;
            } catch (android.content.ActivityNotFoundException ignored) {
            }
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        startActivity(browserIntent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                toggleBrowserMenu(findViewById(R.id.settingsButton));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void toggleDesktopMode() {
        SharedPreferences prefs = getSharedPreferences("settingsPrefs", MODE_PRIVATE);
        boolean desktopMode = !prefs.getBoolean("desktopMode", false);
        prefs.edit().putBoolean("desktopMode", desktopMode).apply();

        GeckoSessionSettings sessionSettings = mGeckoSession.getSettings();
        sessionSettings.setUserAgentMode(desktopMode
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        sessionSettings.setViewportMode(desktopMode
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        sessionSettings.setDisplayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER);
        String customUserAgent = prefs.getString("userAgent", "");
        sessionSettings.setUserAgentOverride(desktopMode || customUserAgent.isEmpty()
                ? null : customUserAgent);
        // New tabs (live mode) get the same mode.
        settingsBuilder.userAgentMode(desktopMode ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        settingsBuilder.viewportMode(desktopMode ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        settingsBuilder.userAgentOverride(desktopMode || customUserAgent.isEmpty()
                ? null : customUserAgent);
        mGeckoSession.reload();
        Toast.makeText(this, desktopMode ? R.string.desktop_mode_enabled
                : R.string.desktop_mode_disabled, Toast.LENGTH_SHORT).show();
    }

    private void confirmClearSiteData() {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.clear_site_data)
                .setMessage(R.string.clear_site_data_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear, (dialog, which) -> clearSiteData())
                .show();
    }

    private void clearSiteData() {
        String currentUri = tabURLs != null && activeTab >= 0 && activeTab < tabURLs.length
                ? tabURLs[activeTab] : urlEditText.getText().toString();
        if (currentUri == null || currentUri.isEmpty()) {
            currentUri = INITIAL_URL;
        }
        final String uriToRestore = currentUri;
        canGoBack = false;
        mGeckoView.releaseSession();
        tabStates.remove(mGeckoSession);
        mGeckoSession.close();
        sRetainedSession = null;
        sGeckoMediaSession = null;
        mGeckoSession = null;

        sGeckoRuntime.getStorageController()
                .clearData(StorageController.ClearFlags.SITE_DATA)
                .accept(ignored -> {
                    restoreSessionAfterDataClear(uriToRestore);
                    Toast.makeText(this, R.string.site_data_cleared, Toast.LENGTH_SHORT).show();
                }, error -> {
                    Log.e("GeckoBrowser", "Failed to clear site data", error);
                    restoreSessionAfterDataClear(uriToRestore);
                    Toast.makeText(this, R.string.site_data_clear_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void restoreSessionAfterDataClear(String uri) {
        boolean activityDestroyed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && isDestroyed();
        if (isFinishing() || activityDestroyed) {
            return;
        }
        openGeckoSession();
        if (liveTabs) {
            ensureTabSessionSlots();
            tabSessions.set(activeTab, mGeckoSession);
        }
        commitURL(uri);
    }

    private void exitBrowser() {
        if (clearTabsOnExit()) {
            saveSingleHomeTab();
        }
        dismissMediaNotification();
        mediaPlaybackState = PlaybackStateCompat.STATE_NONE;
        geckoMediaSession = null;
        sGeckoMediaSession = null;
        if (androidMediaSession != null) {
            androidMediaSession.setActive(false);
        }
        if (mGeckoView != null) {
            mGeckoView.releaseSession();
        }
        for (GeckoSession other : tabSessions) {
            if (other != null && other != mGeckoSession) {
                other.close();
            }
        }
        tabSessions.clear();
        if (mGeckoSession != null) {
            mGeckoSession.close();
            mGeckoSession = null;
        }
        sRetainedSession = null;
        sGeckoMediaSession = null;
        if (sGeckoRuntime != null) {
            sGeckoRuntime.shutdown();
            sGeckoRuntime = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finishAffinity();
        }
        // A GeckoRuntime cannot be created again in a process where it was shut down,
        // so end the process; the next launch starts clean instead of crashing.
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
    }

    @Override
    public void onBackPressed() {
        if (isBottomMenuVisible()) {
            hideBottomMenu();
        } else if (isTabSwitcherVisible()) {
            hideTabSwitcher();
        } else if (browserFullScreen && mGeckoSession != null) {
            mGeckoSession.exitFullScreen();
        } else if (fullscreenMode) {
            setFullscreenMode(false);
        } else if (canGoBack && mGeckoSession != null) {
            mGeckoSession.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void setBrowserFullScreen(boolean fullScreen) {
        browserFullScreen = fullScreen;
        applyImmersiveUi();
    }

    /** Menu toggle: hides the toolbar and system bars until the user presses back. */
    private void setFullscreenMode(boolean enabled) {
        fullscreenMode = enabled;
        applyImmersiveUi();
    }

    /**
     * Hides the toolbar and system bars while either HTML5 fullscreen or the
     * fullscreen setting is active, and restores them once both are off.
     */
    private void applyImmersiveUi() {
        boolean immersive = browserFullScreen || fullscreenMode;
        toolbarAutoHidden = false;
        View toolbar = findViewById(R.id.toolbarView);
        toolbar.setVisibility(immersive ? View.GONE : View.VISIBLE);
        mProgressView.setVisibility(immersive ? View.GONE : mProgressView.getVisibility());

        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mGeckoView.getLayoutParams();
        View root = findViewById(R.id.mainRelativeLayout);
        View decorView = getWindow().getDecorView();
        if (immersive) {
            hideKeyboard(this);
            removeRelativeLayoutRule(layoutParams, RelativeLayout.BELOW);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            root.setBackgroundColor(Color.BLACK);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            hideSystemBars();
        } else {
            removeRelativeLayoutRule(layoutParams, RelativeLayout.ALIGN_PARENT_TOP);
            layoutParams.addRule(RelativeLayout.BELOW, R.id.toolbarView);
            root.setBackgroundColor(ui.pageBackground);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        mGeckoView.setLayoutParams(layoutParams);
        // Recompute window insets so the layout matches the bars actually on screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            decorView.requestApplyInsets();
        } else {
            decorView.requestFitSystemWindows();
        }
        root.requestLayout();
    }

    private void removeRelativeLayoutRule(RelativeLayout.LayoutParams layoutParams, int rule) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            layoutParams.removeRule(rule);
        } else {
            layoutParams.addRule(rule, 0);
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(FULLSCREEN_UI_FLAGS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && (browserFullScreen || fullscreenMode)) {
            hideSystemBars();
        }
    }

    private void setupUrlEditText() {
        urlEditText = findViewById(R.id.urlEditText);

        urlEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
                // Hardware Enter fires DOWN and UP; load only once.
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                    commitURL(urlEditText.getText().toString());
                }
                return true;
            }
        });
        urlEditText.setOnFocusChangeListener((view, hasFocus) -> setAddressBarExpanded(hasFocus));
        // Inside the field: reload while browsing, clear the text while editing.
        findViewById(R.id.reloadButton).setOnClickListener(view -> {
            if (urlEditText.hasFocus()) {
                urlEditText.setText("");
            } else if (mGeckoSession != null) {
                mGeckoSession.reload();
            }
        });
    }

    /** Collapsed bar shows only the domain; the full address appears while editing. */
    private static String displayUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)
                || url.regionMatches(true, 0, "https://", 0, 8)) {
            Uri parsed = Uri.parse(url);
            String host = parsed.getHost();
            if (host != null && !host.isEmpty()) {
                return parsed.getPort() > 0 ? host + ":" + parsed.getPort() : host;
            }
        }
        return url;
    }

    private boolean addressBarExpanded;

    private void setAddressBarExpanded(boolean expanded) {
        if (expanded == addressBarExpanded) {
            return;
        }
        addressBarExpanded = expanded;
        int visibility = expanded ? View.GONE : View.VISIBLE;
        findViewById(R.id.browserBackButton).setVisibility(visibility);
        findViewById(R.id.toolbarDivider1).setVisibility(visibility);
        findViewById(R.id.browserForwardButton).setVisibility(visibility);
        findViewById(R.id.tabsButtonFrame).setVisibility(visibility);
        findViewById(R.id.toolbarDivider2).setVisibility(visibility);
        findViewById(R.id.bookmarksButton).setVisibility(visibility);
        if (!android.view.ViewConfiguration.get(this).hasPermanentMenuKey()) {
            findViewById(R.id.settingsButton).setVisibility(visibility);
        }
        ImageView actionButton = findViewById(R.id.reloadButton);
        actionButton.setImageDrawable(androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, expanded ? R.drawable.ic_nav_clear : R.drawable.ic_nav_reload));
        actionButton.setContentDescription(getString(expanded
                ? android.R.string.cancel : R.string.reload_page));
        if (expanded) {
            urlEditText.setText(currentPageUrl == null || "about:blank".equals(currentPageUrl)
                    ? "" : currentPageUrl);
            urlEditText.post(urlEditText::selectAll);
        } else {
            hideSuggestions();
            urlEditText.setText(displayUrl(currentPageUrl));
            hideKeyboard(this);
        }
    }

    private void commitURL(String url) {
        String address = url == null ? "" : url.trim();
        boolean hasScheme = address.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
                || address.matches("^(about|data|file|javascript|mailto):.*");
        if (address.isEmpty()) {
            mGeckoSession.loadUri(INITIAL_URL);
        } else if (hasScheme
                || ((address.contains(".") || address.equalsIgnoreCase("localhost"))
                && !address.contains(" "))) {
            if (!hasScheme) {
                address = "https://" + address;
            }
            mGeckoSession.loadUri(address);
        } else {
            mGeckoSession.loadUri(SEARCH_URI_BASE + Uri.encode(address));
        }

        hideSuggestions();
        mGeckoView.requestFocus();
        hideKeyboard(this);
    }

    private void refreshTabCount() {
        TextView tabsCountView = findViewById(R.id.tabsCountView);
        tabsCountView.setText("" + tabURLs.length);
    }

    // ---- Auto-hide address bar ---------------------------------------------------------

    private int lastScrollY;
    private int scrollTravel;
    private boolean toolbarAutoHidden;

    private void onPageScrolled(int scrollY) {
        int delta = scrollY - lastScrollY;
        lastScrollY = scrollY;
        if (!autoHideBar || browserFullScreen || fullscreenMode || addressBarExpanded || delta == 0) {
            return;
        }
        if ((delta > 0) != (scrollTravel > 0)) {
            scrollTravel = 0;
        }
        scrollTravel += delta;
        if (scrollY <= dp(8)) {
            setToolbarAutoHidden(false);
        } else if (scrollTravel > dp(48)) {
            setToolbarAutoHidden(true);
        } else if (scrollTravel < -dp(24)) {
            setToolbarAutoHidden(false);
        }
    }

    private void setToolbarAutoHidden(boolean hidden) {
        if (hidden == toolbarAutoHidden || browserFullScreen || fullscreenMode) {
            return;
        }
        toolbarAutoHidden = hidden;
        findViewById(R.id.toolbarView).setVisibility(hidden ? View.GONE : View.VISIBLE);
        if (hidden) {
            mProgressView.setVisibility(View.GONE);
        }
    }

    private void showToolbarForAutoHide() {
        scrollTravel = 0;
        setToolbarAutoHidden(false);
    }

    // ---- Address bar suggestions ------------------------------------------------------

    private ListView suggestionsList;
    /** Rows: {kind, label, detail, target}; kind = history | bookmark | search. */
    private final ArrayList<String[]> suggestionRows = new ArrayList<>();
    private BaseAdapter suggestionsAdapter;
    private int suggestionRequest;
    private final Handler suggestionsHandler = new Handler(Looper.getMainLooper());
    private org.mozilla.geckoview.GeckoWebExecutor webExecutor;

    private void setupSuggestions() {
        suggestionsList = findViewById(R.id.suggestionsList);
        suggestionsList.setBackgroundColor(ui.cardBackground);
        suggestionsList.setDivider(new android.graphics.drawable.ColorDrawable(ui.separator));
        suggestionsList.setDividerHeight(Math.max(1, dp(1) / 2));
        suggestionsAdapter = new BaseAdapter() {
            @Override public int getCount() { return suggestionRows.size(); }
            @Override public Object getItem(int position) { return suggestionRows.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                String[] row = suggestionRows.get(position);
                LinearLayout view = new LinearLayout(MainActivity.this);
                view.setOrientation(LinearLayout.HORIZONTAL);
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(14), 0, dp(14), 0);
                view.setMinimumHeight(dp(52));
                ImageView icon = new ImageView(MainActivity.this);
                int iconRes = "history".equals(row[0]) ? R.drawable.ic_menu_history
                        : "bookmark".equals(row[0]) ? R.drawable.ic_menu_bookmarks
                        : R.drawable.ic_suggest_search;
                icon.setImageDrawable(androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(MainActivity.this, iconRes));
                icon.setColorFilter(ui.secondaryText);
                view.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(14), dp(6), 0, dp(6));
                TextView label = new TextView(MainActivity.this);
                label.setText(row[1]);
                label.setTextSize(16);
                label.setTextColor(ui.primaryText);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(label);
                if (row[2] != null && !row[2].isEmpty()) {
                    TextView detail = new TextView(MainActivity.this);
                    detail.setText(row[2]);
                    detail.setTextSize(12);
                    detail.setTextColor(ui.accent);
                    detail.setSingleLine(true);
                    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    texts.addView(detail);
                }
                view.addView(texts, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                return view;
            }
        };
        suggestionsList.setAdapter(suggestionsAdapter);
        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            String target = suggestionRows.get(position)[3];
            hideSuggestions();
            commitURL(target);
        });
        urlEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(android.text.Editable text) {
                scheduleSuggestions(text.toString());
            }
        });
    }

    private void scheduleSuggestions(final String text) {
        suggestionsHandler.removeCallbacksAndMessages(null);
        final String query = text.trim();
        if (!addressBarExpanded || query.isEmpty() || query.equals(currentPageUrl)) {
            hideSuggestions();
            return;
        }
        suggestionsHandler.postDelayed(() -> loadSuggestions(query), 180);
    }

    private void loadSuggestions(final String query) {
        final int request = ++suggestionRequest;
        final ArrayList<String[]> local = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String lower = query.toLowerCase(java.util.Locale.getDefault());
        JSONArray favorites = readFavorites();
        for (int i = 0; i < favorites.length() && local.size() < 2; i++) {
            JSONObject favorite = favorites.optJSONObject(i);
            if (favorite == null) {
                continue;
            }
            String url = favorite.optString("url");
            String title = favorite.optString("title", url);
            if ((url.toLowerCase(java.util.Locale.getDefault()).contains(lower)
                    || title.toLowerCase(java.util.Locale.getDefault()).contains(lower))
                    && seen.add(url)) {
                local.add(new String[]{"bookmark", title, url, url});
            }
        }
        if (!isPrivateMode()) {
            for (HistoryStore.Entry entry : HistoryStore.get(this).search(query, 4)) {
                if (seen.add(entry.url)) {
                    local.add(new String[]{"history",
                            entry.title == null || entry.title.isEmpty() ? entry.url : entry.title,
                            entry.url, entry.url});
                }
            }
        }
        showSuggestions(local);
        boolean looksLikeAddress = query.contains("://")
                || (query.contains(".") && !query.contains(" "));
        if (searchSuggestions && !isPrivateMode() && !looksLikeAddress) {
            fetchSearchSuggestions(query, request, local);
        }
    }

    /** Search-engine suggestions, fetched through Gecko (modern TLS even on Android 4.4). */
    private void fetchSearchSuggestions(final String query, final int request,
            final ArrayList<String[]> local) {
        if (webExecutor == null) {
            webExecutor = new org.mozilla.geckoview.GeckoWebExecutor(sGeckoRuntime);
        }
        String base = SEARCH_URI_BASE.contains("google.")
                ? "https://suggestqueries.google.com/complete/search?client=firefox&q="
                : SEARCH_URI_BASE.contains("bing.")
                ? "https://api.bing.com/osjson.aspx?query="
                : "https://duckduckgo.com/ac/?type=list&q=";
        try {
            webExecutor.fetch(new org.mozilla.geckoview.WebRequest(base + Uri.encode(query)))
                    .accept(response -> new Thread(() -> {
                        final ArrayList<String[]> rows = new ArrayList<>(local);
                        try {
                            if (response.body != null) {
                                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = response.body.read(buffer)) > 0) {
                                    bytes.write(buffer, 0, read);
                                }
                                response.body.close();
                                JSONArray list = new JSONArray(bytes.toString("UTF-8")).getJSONArray(1);
                                for (int i = 0; i < list.length() && i < 5; i++) {
                                    String phrase = list.optString(i, "");
                                    if (!phrase.isEmpty()) {
                                        rows.add(new String[]{"search", phrase, "", phrase});
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.d("GeckoBrowser", "Search suggestions unavailable", e);
                        }
                        suggestionsHandler.post(() -> {
                            if (request == suggestionRequest && addressBarExpanded) {
                                showSuggestions(rows);
                            }
                        });
                    }).start(), error -> Log.d("GeckoBrowser", "Suggestion request failed", error));
        } catch (Exception e) {
            Log.d("GeckoBrowser", "Suggestion request failed", e);
        }
    }

    private void showSuggestions(ArrayList<String[]> rows) {
        suggestionRows.clear();
        suggestionRows.addAll(rows);
        suggestionsAdapter.notifyDataSetChanged();
        suggestionsList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (!rows.isEmpty()) {
            suggestionsList.bringToFront();
        }
    }

    private void hideSuggestions() {
        suggestionRequest++;
        suggestionsHandler.removeCallbacksAndMessages(null);
        if (suggestionsList != null) {
            suggestionsList.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void updateSslIndicator(String url,
            GeckoSession.ProgressDelegate.SecurityInformation securityInfo) {
        boolean isHttps = url != null && url.regionMatches(true, 0, "https://", 0, 8);
        boolean isHttp = url != null && url.regionMatches(true, 0, "http://", 0, 7);

        if (isHttp) {
            showSslIndicator(R.color.sslInsecure, R.string.ssl_insecure);
        } else if (!isHttps) {
            sslIndicatorView.setVisibility(View.GONE);
            sslStatusMessage = getString(R.string.ssl_unknown);
            sslIndicatorView.setContentDescription(sslStatusMessage);
        } else if (securityInfo == null) {
            showSslIndicator(R.color.sslWarning, R.string.ssl_warning);
        } else if (!securityInfo.isSecure || securityInfo.isException
                || securityInfo.mixedModeActive
                == GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED
                || securityInfo.mixedModePassive
                == GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED) {
            showSslIndicator(R.color.sslWarning, R.string.ssl_warning);
        } else {
            showSslIndicator(R.color.sslSecure, R.string.ssl_secure);
        }
    }

    private void showSslIndicator(int colorResource, int messageResource) {
        sslIndicatorView.setColorFilter(getResources().getColor(colorResource));
        sslIndicatorView.setContentDescription(getString(messageResource));
        sslIndicatorView.setVisibility(View.VISIBLE);
        sslStatusMessage = getString(messageResource);
    }

    @Override
    protected void onDestroy() {
        downloadsHandler.removeCallbacksAndMessages(null);
        if (sActiveInstance != this) {
            // A newer activity already owns the shared sessions; only drop our view.
            if (mGeckoView != null && mGeckoView.getSession() == mGeckoSession) {
                mGeckoView.releaseSession();
            }
            super.onDestroy();
            return;
        }
        sActiveInstance = null;
        for (GeckoSession other : tabSessions) {
            if (other != null && other != mGeckoSession) {
                other.close();
            }
        }
        tabSessions.clear();
        boolean keepMediaSession = geckoMediaSession != null
                && geckoMediaSession.isActive()
                && mediaPlaybackState == PlaybackStateCompat.STATE_PLAYING;
        if (keepMediaSession && mGeckoSession != null) {
            if (mGeckoView != null) {
                mGeckoView.releaseSession();
            }
            sRetainedSession = mGeckoSession;
        } else {
            if (mGeckoSession != null) {
                mGeckoSession.close();
            }
            if (androidMediaSession != null) {
                androidMediaSession.setActive(false);
                androidMediaSession.release();
                sAndroidMediaSession = null;
                MediaButtonEventReceiver.mediaSession = null;
                androidMediaSession = null;
            }
            sRetainedSession = null;
            sGeckoMediaSession = null;
            dismissMediaNotification();
            releaseMusicAudioFocus();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (isFinishing() && clearTabsOnExit()) {
            saveSingleHomeTab();
        } else if (!isPrivateMode()) {
            // Private tabs are never written to disk.
            saveTabs();
        }
        super.onPause();
    }

    private boolean clearTabsOnExit() {
        return getSharedPreferences("settingsPrefs", MODE_PRIVATE)
                .getBoolean("clearTabsOnExit", false);
    }

    private void saveTabs() {
        JSONArray savedTabs = new JSONArray();
        if (tabURLs != null) {
            for (String tabURL : tabURLs) {
                savedTabs.put(tabURL);
            }
        }
        getSharedPreferences("dataPrefs", MODE_PRIVATE).edit()
                .putString("tabURLs", savedTabs.toString())
                .putInt("activeTab", activeTab)
                .commit();
    }

    private void saveSingleHomeTab() {
        tabURLs = new String[]{homepageForNewTabs ? INITIAL_URL : "about:blank"};
        activeTab = 0;
        saveTabs();
    }

    private void applyPageProgress(int progress) {
        mProgressView.setProgress(progress);
        if (progress > 0 && progress < 100) {
            mProgressView.setVisibility(View.VISIBLE);
            if (hideWhiteSplash) {
                mGeckoView.setVisibility(View.INVISIBLE);
            }
        } else {
            mProgressView.setVisibility(View.GONE);
            mGeckoView.setVisibility(View.VISIBLE);
        }
    }

    private class createProgressDelegate implements GeckoSession.ProgressDelegate {

        @Override
        public void onPageStart(GeckoSession session, String url) {
            TabState state = tabState(session);
            state.url = url;
            state.title = null;
            state.loading = true;
            state.security = null;
            int index = tabIndexOf(session);
            if (index >= 0 && tabURLs != null && index < tabURLs.length) {
                tabURLs[index] = url;
            }
            if (session != mGeckoSession) {
                return;
            }
            showToolbarForAutoHide();
            if (!urlEditText.hasFocus()) {
                // Don't overwrite what the user is typing.
                urlEditText.setText(displayUrl(url));
            }
            currentPageUrl = url;
            pageLoading = true;
            updateBookmarkButton();
            currentPageTitle = null;
            updateSslIndicator(url, null);
            refreshTabCount();
        }

        @Override
        public void onPageStop(GeckoSession session, boolean success) {
            TabState state = tabState(session);
            state.loading = false;
            // Recorded here so intermediate redirect URLs don't end up in history.
            if (success) {
                recordHistory(state.url);
                if (state.title != null && !isPrivateMode()) {
                    HistoryStore.get(MainActivity.this).updateTitle(state.url, state.title);
                }
            }
            if (session != mGeckoSession) {
                return;
            }
            pageLoading = false;
            // Full-screen pixel readback is slow with software rendering; in lite mode
            // previews are only taken when the tab switcher opens.
            if (success && !isLiteMode(MainActivity.this)) {
                captureCurrentTabPreview();
            }
        }

        @Override
        public void onSecurityChange(GeckoSession session,
                GeckoSession.ProgressDelegate.SecurityInformation securityInfo) {
            tabState(session).security = securityInfo;
            if (session == mGeckoSession) {
                updateSslIndicator(currentPageUrl, securityInfo);
            }
        }

        @Override
        public void onProgressChange(GeckoSession session, int progress) {
            tabState(session).progress = progress;
            if (session == mGeckoSession) {
                applyPageProgress(progress);
            }
        }
    }

}


