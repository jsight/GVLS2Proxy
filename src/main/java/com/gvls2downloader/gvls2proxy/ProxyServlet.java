package com.gvls2downloader.gvls2proxy;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static final String KEY_IP = "ip";
	private static final String KEY_PORT = "port";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_BASE_PATH = "basePath";

    private static final Logger log = LoggerFactory.getLogger(ProxyServlet.class);
    
    @Override
    public void init() throws ServletException {
        log.info("-----------------------------------------------");
        log.info("-----------------------------------------------");
        log.info("---                                         ---");
        log.info("---       GV LS2 Proxy Started!!!!          ---");
        log.info("---                                         ---");
        log.info("-----------------------------------------------");
        log.info("-----------------------------------------------");
    }
    
    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        
    	final CameraConfiguration requestInfo = getRequestInfo(req);
    	log.info("Received request for the following stream: " + requestInfo + " from: " + req.getRemoteAddr());
        final BlockingQueue<byte[]> dataQueue = new ArrayBlockingQueue<>(1000);
    	
        final Boolean[] isRunning = new Boolean[] { true };
        final DataLoader dataLoader = DataLoader.getInstance(requestInfo, true);
        
        resp.setContentType("video/mpeg");
        final IDataLoaderCallback callback = (data) -> {
            try {
                dataQueue.offer(data);
            } catch (Throwable e) {
                isRunning[0] = false;
            }
        };
        dataLoader.addCallback(callback);

        //DataMonitor dataMonitor = new DataMonitor();

        String remoteIP = req.getRemoteAddr();
        //long lastReportTime = System.currentTimeMillis();
        while (isRunning[0]) {
            try {
                byte[] data = dataQueue.poll(1000, TimeUnit.DAYS);

                //long start = System.currentTimeMillis();
                resp.getOutputStream().write(data);
                //long end = System.currentTimeMillis();
                //dataMonitor.addSample(data.length, start, end);

                //if ((System.currentTimeMillis() - lastReportTime) > (1000L * 30L)) {
                    //lastReportTime = System.currentTimeMillis();
                    //System.out.println("Speed for " + remoteIP + ": " + dataMonitor.getAverageRate());
                //}
            } catch (Exception e) {
                isRunning[0] = false;
                break;
            }
        }
        dataLoader.removeCallback(callback);
    }

    private CameraConfiguration getRequestInfo(HttpServletRequest req) {
        String ipParam = req.getParameter(KEY_IP);
        if (ipParam == null || ipParam.trim().equals("")) {
            ipParam = "localhost";
        }
        final String ip = ipParam;

        String user = req.getParameter(KEY_USER);
        String pw = req.getParameter(KEY_PASSWORD);

        if (user == null || user.trim().equals("")) {
            user = "camuser";
        }
        if (pw == null || pw.trim().equals("")) {
            pw = "password";
        }
        
        String portStr = req.getParameter(KEY_PORT);
        int port;
        if (portStr != null && !portStr.trim().equals("")) {
        	port = Integer.parseInt(portStr);
        } else {
        	port = 80;
        }
        
        String basePath = req.getParameter(KEY_BASE_PATH);
        if (basePath == null || basePath.trim().equals("")) {
        	basePath = "/";
        } else if (!basePath.startsWith("/")) {
        	basePath = "/" + basePath;
        } else if (!basePath.endsWith("/")) {
        	basePath = basePath + "/";
        }
        
        CameraConfiguration requestInfo = new CameraConfiguration();
        requestInfo.setIp(ip);
        requestInfo.setPort(port);
        requestInfo.setUserID(user);
        requestInfo.setPassword(pw);
        return requestInfo;
    }
    
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
