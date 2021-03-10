package com.tuomasmattila.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatHandler implements HttpHandler {

    private static final DateTimeFormatter httpDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));

    /**
     * 
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int code = 200;
        String responseBody = "";

        ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                code = handleChatMessageFromClient(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                code = handleGetRequestFromClient(exchange);
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
        //TODO: Print all errors here.
        if (code < 200 || code > 299) {
            ChatServer.log("Error in /chat: " + code + " " + responseBody);
            byte[] bytes = responseBody.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    /**
     * 
     * @param exchange
     * @return
     * @throws Exception
     */
    private int handleChatMessageFromClient(HttpExchange exchange) throws Exception {
        int code = 200;
        Headers headers = exchange.getRequestHeaders();
        String contentType = "";
        String responseBody = "";

        if (!headers.containsKey("Content-Length")) {
            code = 411;
            return code;
        }
        if (headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type").get(0);
        } else {
            code = 400;
            responseBody = "No content type in request.";
            return code;
        }
        if (contentType.equalsIgnoreCase("application/json")) {
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            ChatServer.log(text);
            stream.close();
            try {
                JSONObject obj = new JSONObject(text);
                String dateStr = obj.getString("sent");
                OffsetDateTime odt = OffsetDateTime.parse(dateStr);
                ChatMessage message = new ChatMessage(odt, obj.getString("user"), obj.getString("message"));
                if (message.getNick().length() > 0 && message.getMessage().length() > 0 && message.getSent().toString().length() > 0) {
                    try { 
                        ChatDatabase.getInstance().insertMessage(message);
                        ChatServer.log("New chat message saved.");
                        exchange.sendResponseHeaders(code, -1);
                    } catch (SQLException e) {
                        if (e.getMessage().contains("SQLITE_CONSTRAINT_PRIMARYKEY")){
                            ChatServer.log("Error: Sending messages too fast.");
                            exchange.sendResponseHeaders(429, -1);
                        } else {
                            ChatServer.log("Message could not be saved: Database error.");
                            exchange.sendResponseHeaders(500, -1);
                        }
                    }
                } else {
                    code = 400;
                    responseBody = "No required content in request.";
                    ChatServer.log(responseBody);
                }
            } catch (JSONException e) {
                code = 400;
                responseBody = "JSONException. Message was not sent.";
                ChatServer.log(e.getMessage());
            } catch (DateTimeParseException e) {
                code = 400;
                responseBody = "DateTimeParseException. Could not parse date/time. Message was not sent.";
                ChatServer.log(e.getMessage());
            }
        } else {
            code = 411;
            responseBody = "Content-Type must be application/json.";
            ChatServer.log(responseBody);
        }
        return code;
    }

    /**
     * 
     * @param exchange
     * @return
     * @throws IOException
     * @throws SQLException
     */
    private int handleGetRequestFromClient(HttpExchange exchange) throws IOException, SQLException {
        int code = 200;
        long messagesSince = -1;
        Headers requestHeaders = exchange.getRequestHeaders();
        String responseBody = "";

        if (requestHeaders.containsKey("If-Modified-Since")) {
            String ifModifiedSince = requestHeaders.getFirst("If-Modified-Since");
            ZonedDateTime zdt = ZonedDateTime.parse(ifModifiedSince, httpDateFormatter);
            OffsetDateTime fromWhichDate = zdt.toOffsetDateTime();
            messagesSince = fromWhichDate.toInstant().toEpochMilli();
        }
        if (ChatDatabase.getInstance().isEmpty()) {
            ChatServer.log("No new messages to deliver to client.");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        }
        ArrayList<ChatMessage> messages = ChatDatabase.getInstance().getMessages(messagesSince);
        if (messages == null) {
            responseBody = "Database access error.";
            code = 404;
            exchange.sendResponseHeaders(code, -1);
            return code;
        } else if (messages.size() == 0) {
            responseBody = "No new messages to deliver.";
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        } else {
            try {
                JSONArray jsonArray = new JSONArray();
                responseBody = "";
                OffsetDateTime newest = null;
                for (ChatMessage message : messages) {
                    JSONObject obj = new JSONObject();
                    obj.put("sent", message.getSent());
                    obj.put("user", message.getNick());
                    obj.put("message", message.getMessage());
                    if (newest == null || newest.isBefore(message.getSent())) {
                        newest = message.getSent();
                    }
                    jsonArray.put(obj);
                }
                String datetime = newest.format(httpDateFormatter);
                Headers headers = exchange.getResponseHeaders();
                headers.add("Last-Modified", datetime);
                responseBody = jsonArray.toString();
            } catch (JSONException e) {
                code = 400;
                responseBody = "JSONException. Could not get messages.";
            }
            ChatServer.log("Delivering " + messages.size() + " messages to client.");
            byte[] bytes = responseBody.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return code;
        }
    }

}