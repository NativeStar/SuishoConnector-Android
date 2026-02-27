package com.suisho.linktocomputer.network.udp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.interfaces.IConnectedActivityMethods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
//核心和互传下载文件基本一致 但砍掉ui操作
public class FileSyncDownloader {
    int socketPort;
    FileOutputStream fileOutputStream;
    WebSocket ws;
    IConnectedActivityMethods activityMethods;
    final String fileName;
    final long fileSize;
    final String filePath;
    SSLContext sslContext;
    TrustManager trustManager;
    private final Logger logger = LoggerFactory.getLogger(FileSyncDownloader.class);

    public FileSyncDownloader(int port, String path, String name, long size, IConnectedActivityMethods am) {
        activityMethods = am;
        socketPort = port;
        filePath = path;
        fileName = name;
        fileSize = size;
        start();
    }

    private void start() {
        logger.info("Start download transmit file");
        Thread thread = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    fileOutputStream = new FileOutputStream(filePath);
                } catch (IOException e) {
                    logger.error("Create FileOutputStream error", e);
                    onError(e);
                    return;
                }
                File certFile = new File(activityMethods.getActivity().getDataDir().getAbsolutePath() + "/files/cert/" + GlobalVariables.computerConfigManager.getId() + ".crt");
                try (InputStream certFileInputStream = Files.newInputStream(certFile.toPath())) {
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null);
                    String certificateAlias = Integer.toString(0);
                    keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(certFileInputStream));
                    sslContext = SSLContext.getInstance("TLS");
                    final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(keyStore);
                    trustManager = trustManagerFactory.getTrustManagers()[0];
                    sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                } catch (IOException | CertificateException | KeyStoreException |
                         NoSuchAlgorithmException | KeyManagementException e) {
                    logger.error("Initialize SSLContext error", e);
                    onError(e);
                }
                logger.debug("Create file data download socket");
                Request wsReq = new Request.Builder()
                        .url("wss://" + GlobalVariables.serverAddress + ":" + socketPort)
                        .build();
                ws = new OkHttpClient()
                        .newBuilder()
                        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManager)
                        .hostnameVerifier((hostname, session) -> hostname.equals(GlobalVariables.serverAddress))
                        .writeTimeout(Duration.ofSeconds(10))
                        .callTimeout(Duration.ofSeconds(10))
                        .build()
                        .newWebSocket(wsReq, new WebSocketListener() {
                            @Override
                            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                                super.onOpen(webSocket, response);
                                logger.debug("File data download socket open");
                                //验证 同时表示已经准备好了
                                ws.send(GlobalVariables.computerConfigManager.getSessionId());
                                logger.debug("Send verify session id");
                            }

                            @Override
                            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
                                super.onMessage(webSocket, bytes);
                                try {
                                    //写入文件
                                    fileOutputStream.write(bytes.toByteArray());
                                } catch (IOException e) {
                                    logger.error("Write transmit file error", e);
                                    onError(e);
                                    try {
                                        fileOutputStream.close();
                                    } catch (IOException ignored) {
                                    } finally {
                                        ws.close(5001, "File written error");
                                    }
                                }
                            }

                            @Override
                            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                                //1000正常 4000验证失败 4001PC端被关闭
                                super.onClosing(webSocket, code, reason);
                                try {
                                    fileOutputStream.flush();
                                    fileOutputStream.close();
                                    //失败 删除文件
                                    if(code != 1000) {
                                        logger.warn("Download transmit file failed with code:{}",code);
                                        new File(filePath).delete();
                                    }
                                } catch (IOException e) {
                                    logger.error("Close transmit file error", e);
                                    onError(e);
                                }
                                ws.close(1000, null);
                            }

                            @Override
                            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                                super.onFailure(webSocket, t, response);
                                logger.error("Transmit file download socket error", t);
                            }
                        });
            }
        };
        thread.start();
    }

    private void onError(Exception e) {
        File file = new File(filePath);
        file.delete();
    }
}
