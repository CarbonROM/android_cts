/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit.cts;

import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewRenderer;
import android.webkit.WebViewRendererClient;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class WebViewRendererClientTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {
    private WebViewOnUiThread mOnUiThread;

    public WebViewRendererClientTest() {
        super("com.android.cts.webkit", WebViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final WebViewCtsActivity activity = getActivity();
        WebView webView = activity.getWebView();
        mOnUiThread = new WebViewOnUiThread(webView);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

    private static class JSBlocker {
        // A CoundDownLatch is used here, instead of a Future, because that makes it
        // easier to support requiring variable numbers of releaseBlock() calls
        // to unblock.
        private CountDownLatch mLatch;
        private SettableFuture<Void> mIsBlocked;

        JSBlocker(int requiredReleaseCount) {
            mLatch = new CountDownLatch(requiredReleaseCount);
            mIsBlocked = SettableFuture.create();
        }

        JSBlocker() {
            this(1);
        }

        public void releaseBlock() {
            mLatch.countDown();
        }

        @JavascriptInterface
        public void block() throws Exception {
            // This blocks indefinitely (until signalled) on a background thread.
            // The actual test timeout is not determined by this wait, but by other
            // code waiting for the onRendererUnresponsive() call.
            mIsBlocked.set(null);
            mLatch.await();
        }

        public void waitForBlocked() {
            WebkitUtils.waitForFuture(mIsBlocked);
        }
    }

    private void blockRenderer(final JSBlocker blocker) {
        WebkitUtils.onMainThreadSync(new Runnable() {
            @Override
            public void run() {
                WebView webView = mOnUiThread.getWebView();
                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(blocker, "blocker");
                webView.evaluateJavascript("blocker.block();", null);
                blocker.waitForBlocked();
                // Sending an input event that does not get acknowledged will cause
                // the unresponsive renderer event to fire.
                webView.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
        });
    }

    private void testWebViewRendererClientOnExecutor(Executor executor) throws Throwable {
        final JSBlocker blocker = new JSBlocker();
        final SettableFuture<Void> rendererUnblocked = SettableFuture.create();

        WebViewRendererClient client = new WebViewRendererClient() {
            @Override
            public void onRendererUnresponsive(WebView view, WebViewRenderer renderer) {
                // Let the renderer unblock.
                blocker.releaseBlock();
            }

            @Override
            public void onRendererResponsive(WebView view, WebViewRenderer renderer) {
                // Notify that the renderer has been unblocked.
                rendererUnblocked.set(null);
            }
        };
        if (executor == null) {
            mOnUiThread.setWebViewRendererClient(client);
        } else {
            mOnUiThread.setWebViewRendererClient(executor, client);
        }

        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
        blockRenderer(blocker);
        WebkitUtils.waitForFuture(rendererUnblocked);
    }

    public void testWebViewRendererClientWithoutExecutor() throws Throwable {
        testWebViewRendererClientOnExecutor(null);
    }

    public void testWebViewRendererClientWithExecutor() throws Throwable {
        final AtomicInteger executorCount = new AtomicInteger();
        testWebViewRendererClientOnExecutor(new Executor() {
            @Override
            public void execute(Runnable r) {
                executorCount.incrementAndGet();
                r.run();
            }
        });
        assertEquals(2, executorCount.get());
    }

    public void testSetWebViewRendererClient() throws Throwable {
        assertNull("Initially the renderer client should be null",
                mOnUiThread.getWebViewRendererClient());

        final WebViewRendererClient webViewRendererClient = new WebViewRendererClient() {
            @Override
            public void onRendererUnresponsive(WebView view, WebViewRenderer renderer) {}

            @Override
            public void onRendererResponsive(WebView view, WebViewRenderer renderer) {}
        };
        mOnUiThread.setWebViewRendererClient(webViewRendererClient);

        assertSame(
                "After the renderer client is set, getting it should return the same object",
                webViewRendererClient, mOnUiThread.getWebViewRendererClient());
    }
}
