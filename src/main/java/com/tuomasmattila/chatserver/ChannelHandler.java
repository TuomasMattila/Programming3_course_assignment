package com.tuomasmattila.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONException;
import org.json.JSONObject;

public class ChannelHandler implements HttpHandler {

    /**
     * Handles POST and GET requests from clients.
     *
     * @param exchange the {@code HttpExchange} containing the request from the
     * client and used to send the response
     * @throws IOException if sending response headers fails or if 
     * writing to the {@code OutputStream} or closing it fails.
    */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Result result = new Result();
        result.setCode(200);
        result.setResponse("");

        ChatServer.log("/channels: Request handled in thread " + Thread.currentThread().getId());
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                result = handleCreateChannelRequest(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                result = handleGetChannelRequest(exchange);
            } else {
                result.setCode(400);
                result.setResponse("Not supported.");
            }
        } catch (IOException e) {
            result.setCode(500);
            result.setResponse("Error in handling the request: " + e.getMessage());
        } catch (SQLException e) {
            result.setCode(500);
            result.setResponse("Error in handling the request: " + e.getMessage());
        }
        if (result.getCode() < 200 || result.getCode() > 299) {
            ChatServer.log("---------- Error in /chat: " + result.getCode() + " " + result.getResponse());
            byte[] bytes = result.getResponse().getBytes("UTF-8");
            exchange.sendResponseHeaders(result.getCode(), bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    /**
     * Handles POST requests from clients. Allows clients to create new channels to the database.
     * 
     * @param exchange
     * @return a {@code Result} object that includes the HTTP status code and 
     * a response message
     * @throws IOException if {@code InputStream} fails to close or if sending response headers fails
     * @throws SQLException if a database access error occurs
     */
    private Result handleCreateChannelRequest(HttpExchange exchange) throws IOException, SQLException {
        Headers headers = exchange.getRequestHeaders();
        Result result = new Result();
        result.setCode(200);
        result.setResponse("");
        String contentType = "";
        JSONObject obj = null;
        String channel = "";

        if (!headers.containsKey("Content-Length")) {
            result.setCode(411);
            return result;
        }
        if (headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type").get(0);
        } else {
            result.setCode(400);
            result.setResponse("No content type in request.");
            return result;
        }
        if (contentType.equalsIgnoreCase("application/json")) {
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            ChatServer.log(text);
            stream.close();
            try {
                obj = new JSONObject(text);
                channel = obj.getString("channel name");
            } catch (JSONException e) {
                result.setCode(400);
                result.setResponse("JSONException. Channel was not created. " + e.getMessage());
                return result;
            }
            ArrayList<String> channels = ChatDatabase.getInstance().getChannels();
            if (channels.contains(channel)) {
                result.setCode(400);
                result.setResponse("Error: invalid channel name.");
                return result;
            }
            ChatDatabase.getInstance().createChannel(channel);
            result.setResponse("New channel called \"" + channel + "\" created.");
            ChatServer.log(result.getResponse());
            byte[] bytes = result.getResponse().getBytes("UTF-8");
            exchange.sendResponseHeaders(result.getCode(), bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            result.setCode(411);
            result.setResponse("Content-Type must be application/json.");
        }
        return result;
    }
    /**
     * Handles GET requests from clients. Returns a list of existing channels to
     * the client.
     * 
     * @param exchange the {@code HttpExchange} containing the request from the
     * client and used to send the response
     * @return a {@code Result} object that includes the HTTP status code and 
     * a response message
     * @throws SQLException if a database access error occurs
     * @throws IOException if sending response headers fails or if 
     * writing to the {@code OutputStream} or closing it fails
     */
    private Result handleGetChannelRequest(HttpExchange exchange) throws SQLException, IOException {
        Result result = new Result();
        result.setCode(200);
        result.setResponse("");

        String response = "List of existing channels: \n";
        ArrayList<String> channels = ChatDatabase.getInstance().getChannels();
        for (String channel : channels) {
            response = response.concat(channel + "\n");
        }
        byte[] bytes = response.toString().getBytes("UTF-8");
        exchange.sendResponseHeaders(result.getCode(), bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
        return result;       
    }
       
}
