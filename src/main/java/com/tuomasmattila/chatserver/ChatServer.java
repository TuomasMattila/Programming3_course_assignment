package com.tuomasmattila.chatserver;

import java.io.Console;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.json.JSONException;

public class ChatServer {

    /**
     * A basic https ChatServer for handling POST and GET requests. 
     * Uses a SQLite database for storing user and message information, 
     * and {@code BasicAuthenticator} for authenticating users.
     * 
     * 
     * @param args start-up parameters for the server: 
     * [0]=database file including the full path to it, 
     * [1]=certificate including the full path to it and
     * [2]=certificate's password
     * @throws Exception if creating {@code SSLContext} fails
     */
    public static void main(String[] args) throws Exception {
        try {
            if (args.length != 3) {
                log("Missing startup parameters. Server's launch command should be of form: java -jar jar-file.jar dbname.db cert.jks cert-password");
                return;
            }
            ChatDatabase database = ChatDatabase.getInstance();
            database.open(args[0]);
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
            SSLContext sslContext = chatServerSSLContext(args[1], args[2]);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    //InetSocketAddress remote = params.getClientAddress();
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });
            ChatAuthenticator auth = new ChatAuthenticator();
            HttpContext chatContext = server.createContext("/chat", new ChatHandler());
            chatContext.setAuthenticator(auth);
            server.createContext("/registration", new RegistrationHandler(auth));
            ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
            server.setExecutor(cachedThreadPool);
            server.start();
            log("Server is running...");
            Console console = System.console();
            boolean running = true;
            while (running) {
                if (console.readLine().equals("/quit")) {
                    running = false;
                    server.stop(3);
                    database.close();
                    log("Server closed.");
                }
            }
        } catch (FileNotFoundException e) {
            log("Certificate not found! " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            log("Database error: " + e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates SSLContext.
     * 
     * @param keystore certificate file with the full path to it
     * @param pass password for the certificate
     * @return SSLContext object
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws FileNotFoundException
     * @throws IOException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    private static SSLContext chatServerSSLContext(String keystore, String pass)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException,
            IOException, UnrecoverableKeyException, KeyManagementException {
        char[] passphrase = pass.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keystore), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ssl;
    }

    /**
     * Used for printing messages to the server's log.
     * 
     * @param message
     */
    public static void log(String message) {
        System.out.println(LocalDateTime.now() + " " + message);
    }

}