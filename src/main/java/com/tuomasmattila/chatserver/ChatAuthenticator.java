package com.tuomasmattila.chatserver;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

    /**
     * 
     */
    public ChatAuthenticator() {
        super("chat");
    }

    /**
     * 
     */
    @Override
    public boolean checkCredentials(String username, String password) {
        return ChatDatabase.getInstance().validateUser(username, password);
    }

    /**
     * 
     * @param user
     * @return
     */
    public boolean addUser(User user) {
        return ChatDatabase.getInstance().insertUser(user);
    }

}
