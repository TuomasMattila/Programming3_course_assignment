package com.tuomasmattila.chatserver;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ChatMessage {

    private OffsetDateTime sent;
    private String nick;
    private String message;

    /**
     * 
     */
    public ChatMessage() {
        this.nick = "";
        this.message = "";
        this.sent = null;
    }

    /**
     * 
     * @param sent
     * @param nick
     * @param message
     */
    public ChatMessage(OffsetDateTime sent, String nick, String message) {
        this.nick = nick;
        this.message = message;
        this.sent = sent;
    }

    /**
     * This is used for storing timestamps to the database in unix format.
     * @return
     */
    public long dateAsInt() {  
        return sent.toInstant().toEpochMilli(); 
    }
    
    /**
     * This is used in handling GET requests. The method converts unix timestamps to UTC time.
     * @param epoch
     */
    public void setSent(long epoch) { 
        sent = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC); 
    }
    
    /**
     * 
     * @param sent
     */
    public void setSent(OffsetDateTime sent) {
        this.sent = sent;
    }

    /**
     * 
     * @param nick
     */
    public void setNick(String nick) {
        this.nick = nick;
    }

    /**
     * 
     * @param message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 
     * @return
     */
    public OffsetDateTime getSent() {
        return this.sent;
    }

    /**
     * 
     * @return
     */
    public String getNick() {
        return this.nick;
    }

    /**
     * 
     * @return
     */
    public String getMessage() {
        return this.message;
    }

}
