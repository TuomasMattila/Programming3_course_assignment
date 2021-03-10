package com.tuomasmattila.chatserver;

public class User {

    private String username;
    private String password;
    private String email;

    /**
     * 
     */
    public User() {
        this.username = "";
        this.password = "";
        this.email = "";
    }

    /**
     * 
     * @param username
     * @param password
     * @param email
     */
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    /**
     * 
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 
     * @param email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 
     * @return
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * 
     * @return
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * 
     * @return
     */
    public String getEmail() {
        return this.email;
    }

}
