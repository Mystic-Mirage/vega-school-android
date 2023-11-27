package net.m10e.vegaplatform;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends Activity {
    private String hostName;
    private String url;
    private SpannableString errorMessage;

    public static final int INPUT_FILE_REQUEST_CODE = 1;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;

    private static final String ANY_TYPES = "*/*";
    private static final String CAPTURE_IMAGE_DIRECTORY = "vega-platform";
    private static final String CAPTURE_IMAGE_PREFIX = "vp_";

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (hostName.equals(request.getUrl().getHost())) {
                return false;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
            startActivity(intent);
            return true;
        }
        public void onPageFinished(WebView view, String url) {
            SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe);
            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                view.loadUrl("about:blank");

                AlertDialog alert = new AlertDialog.Builder(view.getContext())
                    .setTitle(R.string.error_title)
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.error_button, (dialog, whichButton) -> view.loadUrl(url))
                    .setCancelable(false)
                    .create();

                alert.show();
                ((TextView) alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        private File getFileForImageCapture() {
            File externalDataDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File cameraDataDir = new File(externalDataDir.getAbsolutePath() + File.separator + CAPTURE_IMAGE_DIRECTORY);
            if (!cameraDataDir.exists() && !cameraDataDir.mkdirs()) {
                cameraDataDir = externalDataDir;
            }
            return new File(cameraDataDir.getAbsolutePath() + File.separator + CAPTURE_IMAGE_PREFIX + System.currentTimeMillis() + ".jpg");
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePathCallback;

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = getFileForImageCapture();
            mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));

            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType(ANY_TYPES);

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

            return true;
        }
    }

    private class MyDownloadListener implements DownloadListener {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            final String cookies = CookieManager.getInstance().getCookie(url);
            final String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("Cookie", cookies)
                .addRequestHeader("User-Agent", userAgent)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    if (downloadId == 0) return;

                    final Uri downloadedUri = downloadManager.getUriForDownloadedFile(downloadId);
                    startActivity(
                        new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(downloadedUri, mimeType)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    );
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            downloadManager.enqueue(request);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hostName = getString(R.string.url_hostname);
        String hostNameWithPort = hostName;
        String port = getString(R.string.url_port);
        if (!port.equals("")) {
            hostNameWithPort += ":" + port;
        }
        url = getString(R.string.url_protocol) + "://" + hostNameWithPort;
        errorMessage = new SpannableString(String.format(getString(R.string.error_message_template), getString(R.string.error_button), url));
        Linkify.addLinks(errorMessage, Linkify.WEB_URLS);

        WebView mWebView = findViewById(R.id.webview);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe);
        swipeRefreshLayout.setOnRefreshListener(mWebView::reload);

        mWebView.setOnLongClickListener(v -> true);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.setWebChromeClient(new MyWebChromeClient());
        mWebView.setDownloadListener(new MyDownloadListener());

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setAllowFileAccess(true);
        webSettings.setJavaScriptEnabled(true);

        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        String userAgentTemplate = getString(R.string.user_agent_template);
        int versionCode = 0;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                versionCode = (int) getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).getLongVersionCode();
            } else {
                versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        String appName = getString(R.string.app_name);
        String userAgent = Base64.encodeToString(webSettings.getUserAgentString().getBytes(), Base64.NO_PADDING | Base64.NO_WRAP);
        webSettings.setUserAgentString(String.format(userAgentTemplate, versionCode, appName, hostNameWithPort, userAgent));

        mWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        WebView mWebView = findViewById(R.id.webview);
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                if (mCameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }

        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
    }
}