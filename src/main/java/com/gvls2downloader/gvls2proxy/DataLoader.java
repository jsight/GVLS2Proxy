package com.gvls2downloader.gvls2proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class responsible for connecting to the camera, as well as
 * maintaining the persistent connection (including retries
 * on connection failure).
 *
 * @author jsightler
 */
public class DataLoader {
	private static Logger log = LoggerFactory.getLogger(DataLoader.class);
	
    private static Map<RequestInfo, DataLoader> dataLoaders = new HashMap<>();
    
    private DataLoaderThread dataLoaderThread;
    /**
     * Used to store details for connecting to the camera (ip, user, pw, etc)
     */
    private RequestInfo requestInfo;
    /**
     * List of callbacks to notify on each received batch of data
     */
    private List<IDataLoaderCallback> dataLoaderCallbacks = new ArrayList<>();
    /**
     * File used for local storage of streaming data
     */
    private File streamingOutputFile;
    
    public static synchronized DataLoader getInstance(RequestInfo requestInfo, boolean autoStart) {
        DataLoader loader = dataLoaders.get(requestInfo);
        if (loader == null && autoStart) {
        	loader = new DataLoader();
        	loader.init(requestInfo);
        	dataLoaders.put(requestInfo, loader);
        }
        return loader;
    }

    private DataLoader() {
    }

    public boolean isStarted() {
        return dataLoaderThread != null;
    }

    public void init(RequestInfo requestInfo) {
    	dataLoaderThread = new DataLoaderThread();
        this.requestInfo = requestInfo;
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
        
        final DateFormat df = new SimpleDateFormat("yyyy_MM_dd_HHmmss");
        String dateStr = df.format(new Date());
        this.streamingOutputFile = new File(streamingFolder, "mpegstream_" + dateStr + ".ts");
        log.info("Filename is: " + streamingOutputFile.getAbsolutePath() + " (for stream request: " + requestInfo + ")");
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
            is = response.getEntity().getContent();
            writeStreamToFile(is, "session_complete.txt");
        } finally {
            isRunning[0] = false;
        }
    }

    private class DataLoaderThread extends Thread {
        public void run() {
            while (true) {
            	// first, attempt to make the request
                try {
                    executeRequest();
                } catch (Throwable t) {
                	log.debug("Camera connection failure", t);
                }
                
                // If we have reached here, the connection has failed.
                //
                // This could be due to a network error or an authentication failure.
                //
                // In either case, the camera frequently will prevent connections in
                // less than 30 seconds (to prevent two connections from occurring at
                // the same time), so wait 31 seconds and try again.
                Date retryTime = new Date(System.currentTimeMillis() + (1000L * 31L));
                log.error("Request failed. Waiting for 31 seconds, and then will retry at: " + retryTime);
                try {
                    Thread.sleep(31000);
                } catch (Exception e) {
                    // ignore for now
                }
            }
        }
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
        log.info("Writing data to: " + file.getAbsolutePath());
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
