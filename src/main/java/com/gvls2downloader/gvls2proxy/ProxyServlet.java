package com.gvls2downloader.gvls2proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 */
@WebServlet(name = "ProxyServlet", urlPatterns = {"/ProxyServlet"})
public class ProxyServlet extends HttpServlet {
    private static final String KEY_IP = "ip";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";

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
                ipParam = "192.168.8.156";
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

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
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

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
