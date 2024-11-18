package pascal.taie.intellij.gui.webwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.CefClient;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefCallback;
import org.cef.CefApp;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;

import javax.swing.JComponent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class WebWindow {

    public static final Logger logger = Logger.getInstance(WebWindow.class);

    private final Project project;
    private final WebWindowService service;
    private final JBCefBrowser browser;

    public WebWindow(Project project, WebWindowService service) {
        this.project = project;
        this.service = service;
        browser = new JBCefBrowser();
        registerMessageRouterHandler(browser.getJBCefClient().getCefClient());
        registerAppSchemeHandler();
        browser.loadURL("http://inner/" + service.getUrl());
        Disposer.register(project, browser);
    }

    public void reload() {
        browser.loadURL("http://inner/" + service.getUrl());
    }

    public JComponent getComponent() {
        return browser.getComponent();
    }

    private void registerAppSchemeHandler() {
        class CustomResourceHandler implements CefResourceHandler {
            private ResourceHandlerState state = ClosedConnection.INSTANCE;

            interface ResourceHandlerState {
                void getResponseHeaders(CefResponse response,
                                        IntRef responseLength,
                                        StringRef redirectUrl);

                boolean readResponse(byte[] dataOut,
                                     int designedBytesToRead,
                                     IntRef bytesRead,
                                     CefCallback callback);

                default void close() { }
            }

            static class OpenedConnection implements ResourceHandlerState {

                private final URLConnection connection;
                private final InputStream inputStream;

                public OpenedConnection(URLConnection connection) throws IOException {
                    this.connection = connection;
                    this.inputStream = connection.getInputStream();
                }

                @Override
                public void getResponseHeaders(CefResponse cefResponse, IntRef responseLength, StringRef redirectUrl) {
                    try {
                        String url = connection.getURL().toString();
                        if (url.contains("css")) {
                            cefResponse.setMimeType("text/css");
                        } else if (url.contains("js")) {
                            cefResponse.setMimeType("text/javascript");
                        } else if (url.contains("html")) {
                            cefResponse.setMimeType("text/html");
                        } else {
                            cefResponse.setMimeType(connection.getContentType());
                        }
                        responseLength.set(inputStream.available());
                        cefResponse.setStatus(200);
                    } catch (IOException e) {
                        cefResponse.setError(CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND);
                        cefResponse.setStatusText(e.getLocalizedMessage());
                        cefResponse.setStatus(404);
                    }
                }

                @Override
                public boolean readResponse(byte[] dataOut, int designedBytesToRead, IntRef bytesRead, CefCallback callback) {
                    try {
                        int availableSize = inputStream.available();
                        if (availableSize > 0) {
                            int maxBytesToRead = Math.min(availableSize, designedBytesToRead);
                            int realNumberOfReadBytes = inputStream.read(dataOut, 0, maxBytesToRead);
                            bytesRead.set(realNumberOfReadBytes);
                            return true;
                        } else {
                            inputStream.close();
                            return false;
                        }
                    } catch (IOException e) {
                        logger.warn(e);
                        return false;
                    }
                }

                @Override
                public void close() {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                }
            }

            // ClosedConnection as a singleton
            static class ClosedConnection implements ResourceHandlerState {

                // Singleton instance
                public static final ClosedConnection INSTANCE = new ClosedConnection();

                private ClosedConnection() {
                    // private constructor to prevent instantiation
                }

                @Override
                public void getResponseHeaders(CefResponse cefResponse, IntRef responseLength, StringRef redirectUrl) {
                    cefResponse.setStatus(404);
                }

                @Override
                public boolean readResponse(byte[] dataOut, int designedBytesToRead, IntRef bytesRead, CefCallback callback) {
                    return false;
                }

                @Override
                public void close() {
                    // No action needed for closed connection
                }
            }

            @Override
            public boolean processRequest(CefRequest cefRequest, CefCallback cefCallback) {
                String urlOption = cefRequest.getURL();
                if (urlOption != null) {
                    try {

                        String pathToResource = urlOption.replace("http://inner", "webview/");
                        URL newUrl = getClass().getClassLoader().getResource(pathToResource);

                        if (newUrl != null) {
                            state = new OpenedConnection(newUrl.openConnection());
                            cefCallback.Continue(); // Java中Continue是小写开头
                            return true;
                        }
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                }
                return false;
            }

            @Override
            public void getResponseHeaders(CefResponse cefResponse, IntRef responseLength, StringRef redirectUrl) {
                state.getResponseHeaders(cefResponse, responseLength, redirectUrl);
            }

            @Override
            public boolean readResponse(byte[] dataOut, int designedBytesToRead, IntRef bytesRead, CefCallback callback) {
                return state.readResponse(dataOut, designedBytesToRead, bytesRead, callback);
            }

            @Override
            public void cancel() {
                state.close();
                state = ClosedConnection.INSTANCE;
            }
        }
        CefApp
            .getInstance()
            .registerSchemeHandlerFactory(
                "http",
                "inner",
                    (browser, frame, schemeName, request) -> new CustomResourceHandler()
            );
    }

    private void registerMessageRouterHandler(CefClient cefClient) {
        // 创建CefMessageRouter的配置
        CefMessageRouter.CefMessageRouterConfig routerConfig =
                new CefMessageRouter.CefMessageRouterConfig("javaQuery", "javaQueryCancel");

        // 创建MessageRouter
        CefMessageRouter messageRouter = CefMessageRouter.create(routerConfig);

        // 添加MessageRouter的处理器
        messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                                   String request, boolean persistent, CefQueryCallback callback) {
                if (callback != null) {
                    callback.success(service.getJavaQueryResult(request));
                }
                return true; // 表示已处理请求
            }
        }, false);

        // 将MessageRouter添加到CefClient中
        cefClient.addMessageRouter(messageRouter);
    }

}

