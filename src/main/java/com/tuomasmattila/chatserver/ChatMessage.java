package com.tuomasmattila.chatserver;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A class that represents a chat message. The class can hold
 * the time the message was sent as an {@code OffsetDateTime}, 
 * the name of the message's sender as {@code String} and the 
 * message itself as a {@code String}
 */
public class ChatMessage {

    private OffsetDateTime sent;
    private String nick;
    private String message;
    private String channel;

    public ChatMessage() {
        this.nick = "";
        this.message = "";
        this.sent = null;
        this.channel = "";
    }

    public ChatMessage(OffsetDateTime sent, String nick, String message, String channel) {
        this.nick = nick;
        this.message = message;
        this.sent = sent;
        this.channel = channel;
    }

    /**
     * Converts {@code OffsetDateTime} to {@code long} integer, making it
     * an unix timestamp with milliseconds.
     * 
     * @return the timestamp in Unix time with milliseconds
     */
    public long dateAsInt() {  
        return sent.toInstant().toEpochMilli(); 
    }
    
    /**
     * Converts {@code long} integers (unix timestamps) to {@code OffsetDateTime}.
     * @param epoch the timestamp in unix format ({@code long})
     */
    public void setSent(long epoch) { 
        sent = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC); 
    }
    
    public void setSent(OffsetDateTime sent) {
        this.sent = sent;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getSent() {
        return this.sent;
    }

    public String getNick() {
        return this.nick;
    }

    public String getMessage() {
        return this.message;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChatMessageAsString() {
        return "Nick: " + this.nick + " Message: " + this.message + " Sent: " + this.sent.toString() + " Channel: " + this.channel;
    }

}
