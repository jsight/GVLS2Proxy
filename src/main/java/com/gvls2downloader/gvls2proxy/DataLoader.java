package com.gvls2downloader.gvls2proxy;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

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
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
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
	
    private static final Map<CameraConfiguration, DataLoader> dataLoaders = new HashMap<>();
    
    private DataLoaderThread dataLoaderThread;
    /**
     * Used to store details for connecting to the camera (ip, user, pw, etc)
     */
    private CameraConfiguration requestInfo;
    /**
     * List of callbacks to notify on each received batch of data
     */
    private final List<IDataLoaderCallback> dataLoaderCallbacks = new ArrayList<>();
    /**
     * File used for local storage of streaming data
     */
    private File streamingOutputFile;
    
    public static synchronized DataLoader getInstance(CameraConfiguration requestInfo, boolean autoStart) {
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

    public void init(CameraConfiguration requestInfo) {
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
    
    public void addCallback(IDataLoaderCallback callback) {
        synchronized (this.dataLoaderCallbacks) {
            this.dataLoaderCallbacks.add(callback);
        }
    }
    
    public void removeCallback(IDataLoaderCallback callback) {
        synchronized (this.dataLoaderCallbacks) {
            this.dataLoaderCallbacks.remove(callback);
        }
    }
    
    private void executeRequest() throws IOException {
        final Boolean[] isRunning = new Boolean[] { true };
        final IDataLoaderCallback writeToFileCallback = new TSFileDiskCallback();
        addCallback(writeToFileCallback);
        try {
            log.info("Initiating request to: " + requestInfo);
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
            ClientConnectionManager cm = new PoolingClientConnectionManager(schemeRegistry);

            final DefaultHttpClient httpClient = new DefaultHttpClient(cm);
            HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 60000);
            HttpConnectionParams.setSoTimeout(httpClient.getParams(), 10000);
            
            final String user = this.requestInfo.getUserID();
            final String password = this.requestInfo.getPassword();
            final String hostAndPort = requestInfo.getIp() + ":" + requestInfo.getPort();
            final String basePath = requestInfo.getBasePath();
            
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, password);
            httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

            // 1. Authenticate
            HttpGet httpGet = new HttpGet("http://" + hostAndPort + basePath + "php/session_start_user.php");
            HttpResponse response = httpClient.execute(httpGet);
            InputStream is = response.getEntity().getContent();
            writeStreamToFile(is, "authentication.txt");

            log.info("Authenticated (" + requestInfo + ")");
            
            // 2. Send Hello
            httpGet = new HttpGet("http://" + hostAndPort + basePath + "cgi-bin/hello.cgi");
            response = httpClient.execute(httpGet);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "hello.txt");

            // 3. Send first command
            HttpPost httpPost = new HttpPost("http://" + hostAndPort + basePath + "cgi-bin/cmd.cgi");
            StringEntity stringEntity = new StringEntity("\"Command\":\"SetCamCtrl\",\"Params\":{\"Ctrl\":\"ModeMonitor\"}}");
            httpPost.setEntity(null);
            response = httpClient.execute(httpPost);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "cmd.txt");

            Thread newThread = new Thread() {
                @Override
                public void run() {
                    while (isRunning[0]) {
                        try {
                            HttpGet httpGet = new HttpGet("http://" + hostAndPort + basePath + "php/session_continue.php");
                            HttpResponse response = httpClient.execute(httpGet);
                            log.info("Continuing session for server: " + hostAndPort);
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
                httpGet = new HttpGet("http://" + hostAndPort + basePath + "cgi-bin/movie_sp.cgi");
                response = httpClient.execute(httpGet);
                is = response.getEntity().getContent();
                log.info("Stream download in progress... (" + requestInfo + ")");
                writeStreamToOutput(is);
            } catch (Exception e) {
                log.info("Error transferring stream... shutting down thread! (" + requestInfo + ")");
            }

            // 5. Disconnect session
            httpGet = new HttpGet("http://" + hostAndPort + basePath + "php/session_finish.php");
            response = httpClient.execute(httpGet);
            is = response.getEntity().getContent();
            writeStreamToFile(is, "session_complete.txt");
        } finally {
            isRunning[0] = false;
            removeCallback(writeToFileCallback);
        }
    }

    private class DataLoaderThread extends Thread {
        public void run() {
            while (true) {
            	// first, attempt to make the request
                try {
                    executeRequest();
                } catch (Throwable t) {
                	log.debug("Camera connection failure (" + requestInfo + ")", t);
                }
                
                // If we have reached here, the connection has failed.
                //
                // This could be due to a network error or an authentication failure.
                //
                // In either case, the camera frequently will prevent connections in
                // less than 30 seconds (to prevent two connections from occurring at
                // the same time), so wait 31 seconds and try again.
                Date retryTime = new Date(System.currentTimeMillis() + (1000L * 31L));
                log.error("(" + requestInfo + ") Request failed. Waiting for 31 seconds, and then will retry at: " + retryTime);
                try {
                    Thread.sleep(31000);
                } catch (Exception e) {
                    // ignore for now
                }
            }
        }
    }

    private class TSFileDiskCallback implements IDataLoaderCallback {
        private FileOutputStream outputStream;
        final BlockingQueue<byte[]> dataQueue = new ArrayBlockingQueue<>(5000);

        public TSFileDiskCallback() throws IOException {
            outputStream = new FileOutputStream(DataLoader.this.streamingOutputFile, true);
            Thread writerThread = new Thread() {
                @Override
                public void run() {
                    while (outputStream != null) {
                        try {
                            byte[] data = dataQueue.poll(1000, TimeUnit.DAYS);
                            outputStream.write(data);
                        } catch (Throwable t) {
                            System.err.println("Failed to write data to: " + DataLoader.this.streamingOutputFile + " due to: " + t.getMessage());
                        }
                    }
                }
            };
            writerThread.setName("TSWriter");
            writerThread.start();
        }

        @Override
        public void dataReceived(byte[] data) {
            dataQueue.offer(data);
        }

        public void close() throws IOException {
            OutputStream os = outputStream;
            outputStream = null;
            os.close();
        }
    }

    private void writeStreamToOutput(InputStream is) throws IOException {
        byte[] buffer = new byte[32768];

        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            byte[] dataReceivedArray = new byte[bytesRead];
            System.arraycopy(buffer, 0, dataReceivedArray, 0, bytesRead);


            ArrayList<IDataLoaderCallback> loaderCallbacks;
            synchronized (this.dataLoaderCallbacks) {
                loaderCallbacks = new ArrayList<>(this.dataLoaderCallbacks);
            }
            for (IDataLoaderCallback cb : loaderCallbacks) {
                try {
                    cb.dataReceived(dataReceivedArray);
                } catch (Throwable t) {
                    System.err.println("Error sending data to: " + cb + " due to: " + t.getMessage() + "; Ignoring...");
                }
            }

        }
        is.close();
    }
    
    private static void writeStreamToFile(InputStream is, String filename) throws IOException {
        File file = new File(filename);
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
