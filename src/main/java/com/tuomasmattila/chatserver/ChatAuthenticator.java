package com.tuomasmattila.chatserver;

import com.sun.net.httpserver.BasicAuthenticator;

/**
 * {@code ChatAuthenticator} extends the {@code BasicAuthenticator} class. It is used
 * for cheking the credentials of the users trying to interact with the
 * server and adding users to the database.
 */
public class ChatAuthenticator extends BasicAuthenticator {

    /**
     * Used for authenticating users that try to login or register.
     * Extends {@code BasicAuthenticator}.
     */
    public ChatAuthenticator() {
        super("chat");
    }

    /**
     * Checks whether the user trying to make requests is a valid user in the database.
     * 
     * @param username the username provided by the client in the request
     * @param password the password provided by the client in the request
     * @return {@code true} if validating user was successful, otherwise {@code false}
     */
    @Override
    public boolean checkCredentials(String username, String password) {
        return ChatDatabase.getInstance().validateUser(username, password);
    }

    /**
     * Inserts user information to the database.
     * Passwords are hashed before storing them top the database.
     * 
     * @param user An User object containing username, password and email
     * @return {@code true} if registration was successful, otherwise {@code false}
     */
    public boolean addUser(User user) {
        return ChatDatabase.getInstance().insertUser(user);
    }

}
