package net.m10e.vegamoodleandroid;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Base64;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class MainActivity extends Activity {
    private String hostName;
    private String url;
    private SpannableString errorMessage;

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

        WebSettings webSettings = mWebView.getSettings();
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
        mWebView.evaluateJavascript("collapse()", value -> {
            if (!value.equals("true")) {
                super.onBackPressed();
            }
        });
    }
}