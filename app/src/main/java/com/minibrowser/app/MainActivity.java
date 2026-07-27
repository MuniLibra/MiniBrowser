package com.minibrowser.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "MiniBrowserPrefs";
    public static final String KEY_URL = "saved_url";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_FULLSCREEN = "fullscreen";
    public static final String KEY_URL_BAR_VISIBLE = "url_bar_visible";
    public static final String KEY_DOWNLOADS = "downloads";
    public static final String KEY_HISTORY = "history";
    public static final String KEY_PANEL_COLOR = "panel_color";
    public static final String KEY_SEARCH_ENGINE = "search_engine";
    public static final String KEY_ALLOW_COOKIES = "allow_cookies";
    public static final String KEY_ALLOW_THIRD_PARTY_COOKIES = "allow_tp_cookies";
    public static final String DEFAULT_URL = "https://www.google.com";
    public static final String DEFAULT_PANEL_COLOR = "#0d1117";
    public static final String HISTORY_PREVIEW_URL = "https://myactivity.google.com/product/search?utm_source=google&hl=uk&fg=1";
    public static final String CHANNEL_ID = "mini_browser_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_KILL = "com.minibrowser.app.ACTION_KILL";
    public static final String ACTION_CHANGE_URL = "com.minibrowser.app.ACTION_CHANGE_URL";
    private static final int MAX_HISTORY = 50;

    private static final int REQUEST_CODE_FILE_CHOOSER = 2001;
    private static final int REQUEST_CODE_PERMISSIONS = 2002;

    public static MainActivity instance;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private SharedPreferences prefs;
    private NotificationManager notificationManager;
    private DrawerLayout drawerLayout;
    private View navView;
    private View themeColorPreview;
    private TextView searchEngineText;
    private boolean isFullscreen = true;

    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;

    private final BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ACTION_KILL.equals(action)) exitApp();
            else if (ACTION_CHANGE_URL.equals(action)) showUrlDialog(false);
        }
    };

    public static class DownloadItem {
        public String title;
        public String statusStr;
        public String localUri;
        public String mediaType;
        public long sizeBytes;

        public DownloadItem(String title, String statusStr, String localUri, String mediaType, long sizeBytes) {
            this.title = title;
            this.statusStr = statusStr;
            this.localUri = localUri;
            this.mediaType = mediaType;
            this.sizeBytes = sizeBytes;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        themeColorPreview = findViewById(R.id.theme_color_preview);
        searchEngineText = findViewById(R.id.search_engine_text);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        isFullscreen = prefs.getBoolean(KEY_FULLSCREEN, true);
        boolean urlBarVisible = prefs.getBoolean(KEY_URL_BAR_VISIBLE, false);
        String panelColorHex = prefs.getString(KEY_PANEL_COLOR, DEFAULT_PANEL_COLOR);
        applyPanelColor(panelColorHex);

        updateSearchEngineUI();

        LinearLayout urlBarContainer = findViewById(R.id.url_bar_container);
        CheckBox urlBarCheck = findViewById(R.id.check_url_bar);
        urlBarCheck.setChecked(urlBarVisible);
        urlBarContainer.setVisibility(urlBarVisible ? View.VISIBLE : View.GONE);

        CheckBox fullScreenCheck = findViewById(R.id.check_fullscreen);
        fullScreenCheck.setChecked(isFullscreen);

        findViewById(R.id.btn_back).setOnClickListener(v -> { if (webView != null && webView.canGoBack()) webView.goBack(); drawerLayout.closeDrawers(); });
        findViewById(R.id.btn_back_nav).setOnClickListener(v -> { if (webView != null && webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.btn_forward_nav).setOnClickListener(v -> { if (webView != null && webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.btn_lens).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://lens.google.com"));
            startActivity(intent);
        });
        findViewById(R.id.btn_translate).setOnClickListener(v -> {
            if (webView != null) {
                String currentUrl = webView.getUrl();
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    String translateUrl = "https://translate.google.com/?sl=auto&tl=ru&u=" + Uri.encode(currentUrl);
                    loadUrl(translateUrl);
                }
            }
        });
        findViewById(R.id.btn_history).setOnClickListener(v -> { 
            drawerLayout.closeDrawers();
            showHistoryOptionsDialog();
        });
        findViewById(R.id.btn_downloads).setOnClickListener(v -> { drawerLayout.closeDrawers(); showDownloadsDialog(); });
        findViewById(R.id.btn_exit).setOnClickListener(v -> exitApp());
        findViewById(R.id.btn_theme_color).setOnClickListener(v -> showThemeColorPicker());
        findViewById(R.id.btn_search_engine).setOnClickListener(v -> showSearchEnginePicker());
        findViewById(R.id.btn_web_settings).setOnClickListener(v -> showWebSettingsDialog());

        EditText urlBar = findViewById(R.id.url_bar);
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateFromUrlBar();
                return true;
            }
            return false;
        });

        urlBarCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            urlBarContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            prefs.edit().putBoolean(KEY_URL_BAR_VISIBLE, isChecked).apply();
            drawerLayout.closeDrawers();
        });

        fullScreenCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isFullscreen = isChecked;
            prefs.edit().putBoolean(KEY_FULLSCREEN, isChecked).apply();
            if (isChecked) applyImmersiveMode(); else exitFullscreen();
            drawerLayout.closeDrawers();
        });

        TextView infoText = findViewById(R.id.device_info);
        infoText.setText("Model: " + android.os.Build.MODEL + "\nOS: " + android.os.Build.VERSION.RELEASE);

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && diffX < -100 && Math.abs(velocityX) > 100) {
                    drawerLayout.openDrawer(android.view.Gravity.END);
                    return true;
                }
                return false;
            }
        });
        webView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            if (result.getType() == WebView.HitTestResult.IMAGE_TYPE ||
                result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                String imageUrl = result.getExtra();
                showImageOptionsDialog(imageUrl);
                return true;
            }
            return false;
        });

        setupWebView();
        createNotificationChannel();
        registerLocalReceiver();
        requestNotificationPermission();

        String savedUrl = prefs.getString(KEY_URL, DEFAULT_URL);
        loadUrl(savedUrl);
        postNotification(savedUrl);
        
        if (isFullscreen) applyImmersiveMode(); else exitFullscreen();
    }

    private void updateSearchEngineUI() {
        String engine = prefs.getString(KEY_SEARCH_ENGINE, "Google");
        if (searchEngineText != null) {
            searchEngineText.setText(engine);
        }
    }

    private void showSearchEnginePicker() {
        String[] engines = new String[]{"Google", "Bing", "Яндекс", "Tor (DuckDuckGo)"};
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Выберите поисковую систему");
        builder.setItems(engines, (dialog, which) -> {
            String selected = engines[which];
            prefs.edit().putString(KEY_SEARCH_ENGINE, selected).apply();
            updateSearchEngineUI();
            Toast.makeText(this, "Поисковая система: " + selected, Toast.LENGTH_SHORT).show();
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void showWebSettingsDialog() {
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Настройки веб-протоколов");

        boolean allowCookies = prefs.getBoolean(KEY_ALLOW_COOKIES, true);
        boolean allowTpCookies = prefs.getBoolean(KEY_ALLOW_THIRD_PARTY_COOKIES, true);

        String statusMsg = "• WebSockets (WS/WSS): Включено по умолчанию\n" +
                "• Cookies: " + (allowCookies ? "Включено" : "Отключено") + "\n" +
                "• Third-Party Cookies: " + (allowTpCookies ? "Включено" : "Отключено") + "\n" +
                "• Localhost (127.0.0.1): Поддерживается";

        builder.setMessage(statusMsg);
        builder.setPositiveButton("Очистить Cookies", (dialog, which) -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Cookies очищены", Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("Переключить Cookies", (dialog, which) -> {
            boolean nextState = !allowCookies;
            prefs.edit().putBoolean(KEY_ALLOW_COOKIES, nextState).putBoolean(KEY_ALLOW_THIRD_PARTY_COOKIES, nextState).apply();
            CookieManager.getInstance().setAcceptCookie(nextState);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, nextState);
            Toast.makeText(this, "Cookies " + (nextState ? "включены" : "отключены"), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Закрыть", null);

        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void applyPanelColor(String colorHex) {
        try {
            int parsedColor = Color.parseColor(colorHex);
            if (navView != null) {
                navView.setBackgroundColor(parsedColor);
            }
            if (themeColorPreview != null) {
                themeColorPreview.setBackgroundColor(parsedColor);
            }
            prefs.edit().putString(KEY_PANEL_COLOR, colorHex).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showThemeColorPicker() {
        String[] colors = new String[]{"Dark (#0d1117)", "Midnight (#161b22)", "Navy (#0f172a)", "Purple (#1e1b4b)", "Dark Slate (#18181b)", "Deep Red (#2a0808)", "Forest Green (#062e1c)"};
        String[] colorHexes = new String[]{"#0d1117", "#161b22", "#0f172a", "#1e1b4b", "#18181b", "#2a0808", "#062e1c"};

        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Выберите цвет Mini Panel");
        builder.setItems(colors, (dialog, which) -> {
            applyPanelColor(colorHexes[which]);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void navigateFromUrlBar() {
        EditText urlBar = findViewById(R.id.url_bar);
        if (urlBar == null || webView == null) return;
        String query = urlBar.getText().toString().trim();
        if (query.isEmpty()) return;
        
        String url;
        if (query.startsWith("http://") || query.startsWith("https://") || query.startsWith("ws://") || query.startsWith("wss://")) {
            url = query;
        } else if (query.startsWith("localhost") || query.startsWith("127.0.0.1")) {
            url = "http://" + query;
        } else if (query.contains(".") && !query.contains(" ")) {
            url = "https://" + query;
        } else {
            String engine = prefs.getString(KEY_SEARCH_ENGINE, "Google");
            String searchBase;
            switch (engine) {
                case "Bing":
                    searchBase = "https://www.bing.com/search?q=";
                    break;
                case "Яндекс":
                    searchBase = "https://yandex.ru/search/?text=";
                    break;
                case "Tor (DuckDuckGo)":
                    searchBase = "https://duckduckgo.com/?q=";
                    break;
                case "Google":
                default:
                    searchBase = "https://www.google.com/search?q=";
                    break;
            }
            url = searchBase + Uri.encode(query);
        }
        loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else if (drawerLayout != null && drawerLayout.isDrawerOpen(android.view.Gravity.END)) drawerLayout.closeDrawers();
        else super.onBackPressed();
    }

    private void applyImmersiveMode() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController ctrl = getWindow().getInsetsController();
                if (ctrl != null) {
                    ctrl.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    ctrl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
        });
    }

    private void exitFullscreen() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(true);
                WindowInsetsController ctrl = getWindow().getInsetsController();
                if (ctrl != null) ctrl.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        });
    }

    private void showImageOptionsDialog(String imageUrl) {
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Изображение");
        builder.setItems(new CharSequence[]{"Скачать", "Копировать ссылку"}, (dialog, which) -> {
            if (which == 0) {
                promptUserAndDownload(imageUrl, null, null, "image/*");
            } else {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Image URL", imageUrl);
                clipboard.setPrimaryClip(clip);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void promptUserAndDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Скачать файл?");
        builder.setMessage("Файл: " + filename + "\nURL: " + url);
        builder.setPositiveButton("Скачать", (dialog, which) -> {
            executeDownload(url, userAgent, contentDisposition, mimeType);
        });
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void executeDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("cookie", cookies);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            request.setDescription("Скачивание файла...");
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Загрузка началась: " + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка при загрузке: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showHistoryOptionsDialog() {
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("История поиска");
        builder.setItems(new String[]{"Просмотр истории (Google Activity)", "Локальная история просмотров"}, (dialog, which) -> {
            if (which == 0) {
                loadUrl(HISTORY_PREVIEW_URL);
            } else {
                showLocalHistoryDialog();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void showLocalHistoryDialog() {
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Локальная история");
        String json = prefs.getString(KEY_HISTORY, "[]");
        ArrayList<String> urls = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = arr.length() - 1; i >= 0; i--) urls.add(arr.getString(i));
        } catch (JSONException ignored) {}
        if (urls.isEmpty()) builder.setMessage("Пусто");
        else {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, urls);
            builder.setAdapter(adapter, (d, w) -> loadUrl(urls.get(w)));
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private void showDownloadsDialog() {
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        android.database.Cursor cursor = manager != null ? manager.query(query) : null;

        List<DownloadItem> downloadItems = new ArrayList<>();
        if (cursor != null) {
            int titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            int mediaTypeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
            int sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

            while (cursor.moveToNext()) {
                String title = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                int status = statusIndex != -1 ? cursor.getInt(statusIndex) : -1;
                String localUri = localUriIndex != -1 ? cursor.getString(localUriIndex) : null;
                String mediaType = mediaTypeIndex != -1 ? cursor.getString(mediaTypeIndex) : "";
                long sizeBytes = sizeIndex != -1 ? cursor.getLong(sizeIndex) : 0;

                String statusStr = "";
                switch (status) {
                    case DownloadManager.STATUS_PENDING: statusStr = "Ожидание"; break;
                    case DownloadManager.STATUS_RUNNING: statusStr = "Загрузка..."; break;
                    case DownloadManager.STATUS_FAILED: statusStr = "Ошибка"; break;
                    case DownloadManager.STATUS_PAUSED: statusStr = "Приостановлено"; break;
                    case DownloadManager.STATUS_SUCCESSFUL: statusStr = "Завершено"; break;
                }
                downloadItems.add(new DownloadItem(title, statusStr, localUri, mediaType, sizeBytes));
            }
            cursor.close();
        }

        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Загрузки (Microsoft Edge style)");

        if (downloadItems.isEmpty()) {
            builder.setMessage("Нет загруженных файлов");
        } else {
            ArrayAdapter<DownloadItem> adapter = new ArrayAdapter<DownloadItem>(this, R.layout.item_download, downloadItems) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    if (convertView == null) {
                        convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_download, parent, false);
                    }
                    DownloadItem item = getItem(position);
                    ImageView iconView = convertView.findViewById(R.id.download_icon);
                    TextView titleView = convertView.findViewById(R.id.download_title);
                    TextView subtitleView = convertView.findViewById(R.id.download_subtitle);

                    if (item != null) {
                        titleView.setText(item.title);
                        String formatSize = formatFileSize(item.sizeBytes);
                        subtitleView.setText(item.statusStr + (formatSize.isEmpty() ? "" : " • " + formatSize));

                        boolean thumbnailLoaded = false;
                        if (item.localUri != null) {
                            try {
                                Uri uri = Uri.parse(item.localUri);
                                String path = uri.getPath();
                                String lowerName = item.title.toLowerCase();
                                if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")) {
                                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                                    if (bitmap != null) {
                                        iconView.setImageBitmap(bitmap);
                                        thumbnailLoaded = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        if (!thumbnailLoaded) {
                            String lowerName = item.title.toLowerCase();
                            if (lowerName.endsWith(".apk")) {
                                iconView.setImageResource(android.R.drawable.ic_menu_agenda);
                            } else if (lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar")) {
                                iconView.setImageResource(android.R.drawable.ic_menu_save);
                            } else if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi")) {
                                iconView.setImageResource(android.R.drawable.ic_media_play);
                            } else if (lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg")) {
                                iconView.setImageResource(android.R.drawable.ic_btn_speak_now);
                            } else {
                                iconView.setImageResource(android.R.drawable.ic_menu_save);
                            }
                        }
                    }
                    return convertView;
                }
            };
            builder.setAdapter(adapter, (dialog, which) -> {
                DownloadItem item = downloadItems.get(which);
                if (item.localUri != null) {
                    try {
                        Intent openIntent = new Intent(Intent.ACTION_VIEW);
                        openIntent.setDataAndType(Uri.parse(item.localUri), item.mediaType != null && !item.mediaType.isEmpty() ? item.mediaType : "*/*");
                        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(openIntent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        builder.setPositiveButton("Закрыть", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private AlertDialog.Builder createDarkDialogBuilder() {
        return new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    }

    private void applyDarkTheme(AlertDialog dialog) {
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0d1117")));
        }
        dialog.setOnShowListener(d -> {
            try {
                if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#58a6ff"));
                if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#8b949e"));
                if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null)
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#8b949e"));
            } catch (Exception ignored) {}
        });
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        // Enable Cookies & Third Party Cookies
        boolean allowCookies = prefs.getBoolean(KEY_ALLOW_COOKIES, true);
        boolean allowTpCookies = prefs.getBoolean(KEY_ALLOW_THIRD_PARTY_COOKIES, true);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(allowCookies);
        cookieManager.setAcceptThirdPartyCookies(webView, allowTpCookies);

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            promptUserAndDownload(url, userAgent, contentDisposition, mimeType);
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("intent://") || url.startsWith("market://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        if (intent != null) {
                            startActivity(intent);
                            return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                EditText urlBar = findViewById(R.id.url_bar);
                if (urlBar != null) {
                    urlBar.setText(url);
                }
                addToHistory(url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> requestedResources = Arrays.asList(request.getResources());
                    List<String> androidPermissionsNeeded = new ArrayList<>();
                    if (requestedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        androidPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
                    }
                    if (requestedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        androidPermissionsNeeded.add(Manifest.permission.CAMERA);
                    }

                    boolean allGranted = true;
                    for (String perm : androidPermissionsNeeded) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, perm) != PackageManager.PERMISSION_GRANTED) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermissionRequest = request;
                        AlertDialog.Builder builder = createDarkDialogBuilder();
                        builder.setTitle("Запрос разрешения сайта");
                        builder.setMessage("Веб-сайт просит доступ к камере / микрофону. Разрешить?");
                        builder.setPositiveButton("Разрешить", (dialog, which) -> {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    androidPermissionsNeeded.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
                        });
                        builder.setNegativeButton("Отклонить", (dialog, which) -> {
                            request.deny();
                            pendingPermissionRequest = null;
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                        applyDarkTheme(dialog);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER);
                } catch (Exception e) {
                    Intent chooserIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    chooserIntent.setType("*/*");
                    startActivityForResult(Intent.createChooser(chooserIntent, "Выберите файл (APK, ZIP, Изображения...)"), REQUEST_CODE_FILE_CHOOSER);
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FILE_CHOOSER) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (pendingPermissionRequest != null) {
                if (allGranted) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                } else {
                    pendingPermissionRequest.deny();
                }
                pendingPermissionRequest = null;
            }
        }
    }

    private void addToHistory(String url) {
        String json = prefs.getString(KEY_HISTORY, "[]");
        JSONArray arr;
        try { arr = new JSONArray(json); } catch (JSONException e) { arr = new JSONArray(); }
        arr.put(url);
        while (arr.length() > MAX_HISTORY) arr.remove(0);
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void loadUrl(String url) { if (webView != null) webView.loadUrl(url); }
    public void exitApp() { finishAndRemoveTask(); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MiniBrowser Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления браузера");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public void postNotification(String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("MiniBrowser")
                .setContentText(url)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void registerLocalReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_KILL);
        filter.addAction(ACTION_CHANGE_URL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(localReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(localReceiver, filter);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    public void showUrlDialog(boolean isFirstLaunch) {
        AlertDialog.Builder builder = createDarkDialogBuilder();
        builder.setTitle("Введите URL");
        final EditText input = new EditText(this);
        input.setText(prefs.getString(KEY_URL, DEFAULT_URL));
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                prefs.edit().putString(KEY_URL, url).apply();
                loadUrl(url);
            }
        });
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        applyDarkTheme(dialog);
    }
}