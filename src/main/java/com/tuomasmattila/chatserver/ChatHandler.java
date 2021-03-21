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

/**
 * A Class that handles POST and GET requests from clients. 
 * This class implements {@code HttpHandler}.
 */
public class ChatHandler implements HttpHandler {

    private static final DateTimeFormatter httpDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));

    /**
     * Handles POST and GET requests from clients.

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

        ChatServer.log("/chat: Request handled in thread " + Thread.currentThread().getId());
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                result = handleChatMessageFromClient(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                result = handleGetRequestFromClient(exchange);
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
     * Handles a POST requests from clients.
     * 
     * @param exchange the {@code HttpExchange} containing the request from the
     * client and used to send the response
     * @return a {@code Result} object that includes the HTTP status code and a response message.
     * @throws IOException if {@code InputStream} fails to close or if sending response headers fails
     */
    private Result handleChatMessageFromClient(HttpExchange exchange) throws IOException {
        Result result = new Result();
        result.setCode(200);
        Headers headers = exchange.getRequestHeaders();
        String contentType = "";

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
            stream.close();
            try {
                JSONObject obj = new JSONObject(text);
                if (!obj.has("channel")) {
                    obj.put("channel", "default");
                } else {
                    ArrayList<String> channels = ChatDatabase.getInstance().getChannels();
                    if (!channels.contains(obj.getString("channel"))) {
                        result.setCode(400);
                        result.setResponse("Error: channel name is not valid.");
                        return result;
                    }
                }
                String dateStr = obj.getString("sent");
                OffsetDateTime odt = OffsetDateTime.parse(dateStr);
                ChatMessage message = new ChatMessage(odt, obj.getString("user"), obj.getString("message"), obj.getString("channel"));
                ChatServer.log(message.getChatMessageAsString());
                if (message.getNick().length() > 0 && message.getMessage().length() > 0 && message.getSent().toString().length() > 0 && message.getChannel().length() > 0) {
                        ChatDatabase.getInstance().insertMessage(message);
                        ChatServer.log("New chat message saved.");
                        exchange.sendResponseHeaders(result.getCode(), -1); 
                } else {
                    result.setCode(400);
                    result.setResponse("No required content in request.");
                }
            } catch (JSONException e) {
                result.setCode(400);
                result.setResponse("JSONException. Message was not sent. " + e.getMessage());
                return result;
            } catch (DateTimeParseException e) {
                result.setCode(400);
                result.setResponse("DateTimeParseException. Could not parse date/time. Message was not sent. " + e.getMessage());
                return result;
            } catch (SQLException e) {
                // TODO: delete later?
                if (e.getMessage().contains("channels.name")) {
                    result.setResponse("Error: channel name is not valid. " + e.getMessage());
                    result.setCode(400);
                    exchange.sendResponseHeaders(result.getCode(), -1);
                    return result;
                }
                if (e.getMessage().contains("SQLITE_CONSTRAINT_PRIMARYKEY")) {
                    result.setResponse("Error: Sending messages too fast. " + e.getMessage());
                    result.setCode(429);
                    exchange.sendResponseHeaders(result.getCode(), -1);
                    return result;
                } else {
                    result.setResponse("Message could not be saved: Database error. " + e.getMessage());
                    result.setCode(500);
                    exchange.sendResponseHeaders(result.getCode(), -1);
                    return result;
                }
            }
        } else {
            result.setCode(411);
            result.setResponse("Content-Type must be application/json.");
        }
        return result;
    }

    /**
     * Handles GET requests from clients. If the channel is not specified in the
     * request (there is no "Channel" -header in the request), the user will be given
     * the messages from the default channel.
     * 
     * @param exchange the {@code HttpExchange} containing the request from the
     * client and used to send the response
     * @return a {@code Result} object that includes the HTTP status code and 
     * a response message
     * @throws IOException if sending response headers fails or if 
     * writing to the {@code OutputStream} or closing it fails
     */
    private Result handleGetRequestFromClient(HttpExchange exchange) throws IOException, SQLException {
        Result result = new Result();
        result.setCode(200);
        result.setResponse("");
        long messagesSince = -1;
        String channel = "default";
        Headers headers = exchange.getRequestHeaders();

        if (headers.containsKey("Channel")) {
            channel = headers.getFirst("Channel");
            ArrayList<String> channels = ChatDatabase.getInstance().getChannels();
            if (!channels.contains(channel)) {
                result.setCode(400);
                result.setResponse("Error: requested channel is not valid.");
                return result;
            }
        }
        if (headers.containsKey("If-Modified-Since")) {
            String ifModifiedSince = headers.getFirst("If-Modified-Since");
            ZonedDateTime zdt = ZonedDateTime.parse(ifModifiedSince, httpDateFormatter);
            OffsetDateTime fromWhichDate = zdt.toOffsetDateTime();
            messagesSince = fromWhichDate.toInstant().toEpochMilli();
        }
        if (ChatDatabase.getInstance().isEmpty()) {
            ChatServer.log("No new messages to deliver to client.");
            result.setCode(204);
            exchange.sendResponseHeaders(result.getCode(), -1);
            return result;
        }
        ArrayList<ChatMessage> messages = ChatDatabase.getInstance().getMessages(messagesSince, channel);
        if (messages == null) {
            result.setResponse("Database access error.");
            result.setCode(404);
            exchange.sendResponseHeaders(result.getCode(), -1);
            return result;
        } else if (messages.size() == 0) {
            result.setResponse("No new messages to deliver.");
            result.setCode(204);
            exchange.sendResponseHeaders(result.getCode(), -1);
            return result;
        } else {
            String responseBody = "";
            try {
                JSONArray jsonArray = new JSONArray();
                OffsetDateTime newest = null;
                for (ChatMessage message : messages) {
                    JSONObject obj = new JSONObject();
                    obj.put("sent", message.getSent());
                    obj.put("user", message.getNick());
                    obj.put("message", message.getMessage());
                    obj.put("channel", message.getChannel());
                    if (newest == null || newest.isBefore(message.getSent())) {
                        newest = message.getSent();
                    }
                    jsonArray.put(obj);
                }
                String datetime = newest.format(httpDateFormatter);
                Headers responseHeaders = exchange.getResponseHeaders();
                responseHeaders.add("Last-Modified", datetime);
                responseBody = jsonArray.toString();
            } catch (JSONException e) {
                result.setCode(400);
                result.setResponse("JSONException. Could not get messages.");
                return result;
            }
            ChatServer.log("Delivering " + messages.size() + " messages to client.");
            byte[] bytes = responseBody.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(result.getCode(), bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return result;
        }
    }

}