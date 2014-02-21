package com.gvls2downloader.gvls2proxy;

import java.io.IOException;

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
        
        final Boolean[] isRunning = new Boolean[] { true };
        
        final DataLoader dataLoader = DataLoader.getInstance();
        
        resp.setContentType("video/mpeg");
        final IDataLoaderCallback callback = new IDataLoaderCallback() {
            @Override
            public void dataReceived(byte[] bytes) {
                try {
                    resp.getOutputStream().write(bytes);
                } catch (Throwable e) {
                    isRunning[0] = false;
                } finally {
                    dataLoader.removeCallback(this);
                }
            }
        };
        dataLoader.addCallback(callback);
        
        synchronized (dataLoader) {
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
            dataLoader.init(ip, user, pw);
        }
        
        while (isRunning[0]) {
            try {
                Thread.sleep(1000L);
            } catch (Exception e) {
                isRunning[0] = false;
                dataLoader.removeCallback(callback);
                break;
            }
        }
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
