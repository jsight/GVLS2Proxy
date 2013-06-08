package com.gvls2downloader.gvls2proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;

/**
 *
 * @author jsightler
 */
public class DataLoader {

    private static final DataLoader instance = new DataLoader();
    private DataLoaderThread dataLoaderThread;
    private boolean started;
    private String ip;
    private String user;
    private String password;
    private List<IDataLoaderCallback> dataLoaderCallbacks = new ArrayList<IDataLoaderCallback>();
    private File streamingOutputFile;
    
    public static DataLoader getInstance() {
        return instance;
    }

    private DataLoader() {
        dataLoaderThread = new DataLoaderThread();
    }

    public boolean isStarted() {
        return started;
    }

    public void init(String ip, String user, String password) {
        this.ip = ip;
        this.user = user;
        this.password = password;
        started = true;
        initStreamingFilename();
        dataLoaderThread.start();
    }

    private void initStreamingFilename() {
        String catalinaBaseStr = System.getProperty("catalina.base");
        File catalinaBase = new File(catalinaBaseStr);
        File streamingFolder = new File(catalinaBase, "streaming");
        if (!streamingFolder.exists()) {
            streamingFolder.mkdirs();
        }
        
        final DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String dateStr = df.format(new Date());
        this.streamingOutputFile = new File(streamingFolder, "mpegstream_" + dateStr + ".ts");
        System.out.println("Filename is: " + streamingOutputFile.getAbsolutePath());
    }
    
    public synchronized void addCallback(IDataLoaderCallback callback) {
        this.dataLoaderCallbacks.add(callback);
    }
    
    public synchronized void removeCallback(IDataLoaderCallback callback) {
        this.dataLoaderCallbacks.remove(callback);
    }
    
    private void executeRequest() throws IOException {
        final Boolean[] isRunning = new Boolean[] { true };
        try {
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
            ClientConnectionManager cm = new PoolingClientConnectionManager(schemeRegistry);

            final DefaultHttpClient httpClient = new DefaultHttpClient(cm);
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(this.user, this.password);
            httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

            // 1. Authenticate
            HttpGet httpGet = new HttpGet("http://" + this.ip + "/php/session_start_user.php");
            HttpResponse response = httpClient.execute(httpGet);
            //System.out.println("Headers from auth response:");
            //printHeaders(response);
            InputStream is = response.getEntity().getContent();
            writeStreamToFile(is, "authentication.txt");

            // 2. Send Hello
            httpGet = new HttpGet("http://" + ip + "/cgi-bin/hello.cgi");
            response = httpClient.execute(httpGet);
            //System.out.println("Headers from hello response:");
            //printHeaders(response);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "hello.txt");

            // 3. Send first command
            HttpPost httpPost = new HttpPost("http://" + ip + "/cgi-bin/cmd.cgi");
            StringEntity stringEntity = new StringEntity("\"Command\":\"SetCamCtrl\",\"Params\":{\"Ctrl\":\"ModeMonitor\"}}");
            httpPost.setEntity(null);
            response = httpClient.execute(httpPost);
            //System.out.println("Headers from cmd response:");
            //printHeaders(response);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "cmd.txt");

            Thread newThread = new Thread() {
                @Override
                public void run() {
                    while (isRunning[0]) {
                        try {
                            HttpGet httpGet = new HttpGet("http://" + ip + "/php/session_continue.php");
                            HttpResponse response = httpClient.execute(httpGet);
                            System.out.println("Headers from session continuation:");
                            printHeaders(response);
                            InputStream is = response.getEntity().getContent();
                            writeStreamToFile(is, "session_continue.txt");
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                        try {
                            Thread.sleep(10000);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            };
            newThread.start();

            // 4. Grab MPEG-TS
            // /cgi-bin/movie_sp.cgi
            try {
                httpGet = new HttpGet("http://" + ip + "/cgi-bin/movie_sp.cgi");
                response = httpClient.execute(httpGet);
                System.out.println("Headers from video stream response:");
                printHeaders(response);
                is = response.getEntity().getContent();
                writeStreamToOutput(is);
                System.out.println("MPEG-TS stream complete!");
            } catch (Exception e) {
                System.out.println("Error transferring stream... shutting down thread!");
            }

            // 5. Disconnect session
            httpGet = new HttpGet("http://" + ip + "/php/session_finish.php");
            response = httpClient.execute(httpGet);
            System.out.println("Headers from Session completion:");
            printHeaders(response);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "session_complete.txt");
        } finally {
            isRunning[0] = false;
        }
    }

    private class DataLoaderThread extends Thread {
        public void run() {
            while (true) {
                try {
                    executeRequest();
                    System.out.println("Request Complete... restarting in 5 seconds (" + new Date() + ")");
                    try {
                        Thread.sleep(5000);
                    } catch (Throwable e) {
                        // ignore for now
                    }
                } catch (Throwable t) {
                    System.out.println("Request failed due to: " + t.getMessage());
                    System.out.println("Pausing for 30 seconds, and then will retry (" + new Date() + ")");
                    try {
                        Thread.sleep(31000);
                    } catch (Throwable e) {
                        // ignore for now
                    }
                }
            }
        }
    }

    private static void printHeaders(HttpResponse response) {
        for (Header header : response.getAllHeaders()) {
            System.out.println("Header: " + header.getName() + ": " + header.getValue());
        }
        System.out.println();
        System.out.println();
    }
    
    private void writeStreamToOutput(InputStream is) throws IOException {
        FileOutputStream fos = new FileOutputStream(this.streamingOutputFile, true);
        try {
            byte[] buffer = new byte[32768];

            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                byte[] dataReceivedArray = new byte[bytesRead];
                System.arraycopy(buffer, 0, dataReceivedArray, 0, bytesRead);
                fos.write(dataReceivedArray);
                for (IDataLoaderCallback cb : this.dataLoaderCallbacks) {
                    cb.dataReceived(dataReceivedArray);
                }
            }
            is.close();
        } finally {
            fos.close();
        }
    }
    
    private static void writeStreamToFile(InputStream is, String filename) throws IOException {
        File file = new File(filename);
        System.out.println("Writing to: " + file.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[32768];

        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.close();
        is.close();
    }
}
