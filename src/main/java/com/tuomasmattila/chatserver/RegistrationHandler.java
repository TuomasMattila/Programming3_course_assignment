package com.tuomasmattila.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONException;
import org.json.JSONObject;

public class RegistrationHandler implements HttpHandler {

    ChatAuthenticator auth = null;

    /**
     * 
     * @param authenticator
     */
    RegistrationHandler(ChatAuthenticator authenticator) {
        auth = authenticator;
    }

    /**
     * 
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String responseBody = "";
        int code = 200;

        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Headers headers = exchange.getRequestHeaders();
                String contentType = "";

                if (!headers.containsKey("Content-Length")) {
                    code = 411;
                }
                if (headers.containsKey("Content-Type")) {
                    contentType = headers.get("Content-Type").get(0);
                } else {
                    code = 400;
                    responseBody = "No content type in request.";
                }
                if (contentType.equalsIgnoreCase("application/json")) {
                    InputStream stream = exchange.getRequestBody();
                    String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                            .collect(Collectors.joining("\n"));
                    ChatServer.log(text);
                    stream.close();
                    try {
                        JSONObject obj = new JSONObject(text);
                        User user = new User(obj.getString("username"), obj.getString("password"), obj.getString("email"));
                        if (user.getUsername().trim().length() > 0 && user.getPassword().trim().length() > 0 && user.getEmail().trim().length() > 0) {
                            // Create user credentials.
                            if (auth.addUser(user)) {
                                exchange.sendResponseHeaders(code, -1);
                                ChatServer.log("Added a user.");
                            } else {
                                code = 400;
                                responseBody = "Invalid user credentials.";
                            }
                        } else {
                            code = 400;
                            responseBody = "No content in request.";
                        }
                    } catch (JSONException e) {
                        code = 400;
                        responseBody = "JSONException. Registration failed.";
                    }
                } else {
                    code = 411;
                    responseBody = "Content-Type must be application/json.";
                }
            } else {
                code = 400;
                responseBody = "Not supported.";
            }
        } catch (IOException e) {
            code = 500;
            responseBody = "Error in handling the request: " + e.getMessage();
        } catch (Exception e) {
            code = 500;
            responseBody = "Server error: " + e.getMessage();
        }
        if (code < 200 || code > 299) {
            ChatServer.log("Error in /registration: " + code + " " + responseBody);
            byte[] bytes = responseBody.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

    }

}
