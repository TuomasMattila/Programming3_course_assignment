package com.tuomasmattila.chatserver;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;

import org.apache.commons.codec.digest.Crypt;

import java.sql.ResultSet;

/**
 * A class for managing a SQLite database. This database is used for storing
 * users' usernames, passwords and emails and information about messages sent
 * to the database. Message information contains the name of the sender, the
 * message itself and the time it was sent.
 */
public class ChatDatabase {

    private static ChatDatabase singleton = null;
    private static Connection dbConnection = null;
    private static SecureRandom secureRandom = new SecureRandom();

    /**
     * Used for getting an instance of the database. For example, if we want 
     * to insert a message to the database, we have to call 
     * {@code ChatDatabase.getInstance().insertMessage(ChatMessage)}.
     * 
     * @return the singleton of the database
     */
    public static synchronized ChatDatabase getInstance() {
        if (null == singleton) {
            singleton = new ChatDatabase();
        }
        return singleton;
    }

    private ChatDatabase() {
    }

    /**
     * Initializes the connection to the database. 
     * If the database file did not exist yet, the database will be initialized too
     * using the {@code initializeDatabase(Connection dbConnection)} method.
     * 
     * @param dbName the name of the database file including the full path to it
     * @throws SQLException if the attempt to establish a connection to the given 
     * database URL fails or if a database access error occurs
     */
    public void open(String dbName) throws SQLException {
        File tempfile = new File(dbName);
        boolean isFileOrDir = false;

        if (tempfile.isDirectory() || tempfile.isFile()) {
            isFileOrDir = true;
        }
        String JDBCConnectionAddress = "jdbc:sqlite:" + dbName;
        dbConnection = DriverManager.getConnection(JDBCConnectionAddress);
        ChatServer.log("Connected to the database.");
        if (isFileOrDir == false) {
            ChatServer.log("Initializing database...");
            initializeDatabase(dbConnection);
        }
    }

    /**
     * Initializes the database. Creates two tables: users and messages.
     * The users table can hold information about users' usernames, passwords
     * and emails (all have to be {@code String} type). The messages table can 
     * hold information about messages, including the senders, messages and 
     * timestamps ({@code long}) of the messages. Timestamps must be stored 
     * in an unix format that includes milliseconds.
     * 
     * @param dbConnection A {@code Connection} object; A connection (session) with a 
     * specific database. SQL statements are executed and results are returned 
     * within the context of a connection
     * @throws SQLException if a database access error occurs
     */
    public void initializeDatabase(Connection dbConnection) throws SQLException {
            Statement createStatement = dbConnection.createStatement();
            createStatement.execute("create table users (username varchar(50) PRIMARY KEY, password varchar(100) NOT NULL, salt varchar(100) NOT NULL, email varchar(100) NOT NULL)");
            createStatement.execute("create table messages (user varchar(50) NOT NULL, message varchar(255) NOT NULL, sent integer NOT NULL, PRIMARY KEY(user, sent), FOREIGN KEY(user) REFERENCES users(username))");
            createStatement.close();
            ChatServer.log("Database successfully initialized.");
    }

    /**
     * Inserts user information to the database.
     * Passwords are hashed before storing them top the database.
     * 
     * @param user An {@code User} object containing username, password and email
     * @return {@code true} if registration was successful, otherwise {@code false}
     */
    public boolean insertUser(User user) {
        String query = "select count(*) from users where username='" + user.getUsername() + "'";
        try {
            Statement queryStatement = dbConnection.createStatement();
            ResultSet rs = queryStatement.executeQuery(query);
            if (rs.getBoolean("count(*)")) {
                ChatServer.log("Invalid registration credentials.");
                queryStatement.close();
                return false;
            } else {
                byte bytes[] = new byte[12];
                secureRandom.nextBytes(bytes);
                String saltBytes = new String(Base64.getEncoder().encode(bytes));
                String salt = "$6$" + saltBytes;
                String hashedPassword = Crypt.crypt(user.getPassword(), salt);
                String insertStatement = "insert into users values (\"" + user.getUsername() + "\", \"" + hashedPassword + "\", \"" + salt + "\", \"" + user.getEmail() + "\")";
                queryStatement.executeUpdate(insertStatement);
                queryStatement.close();
                ChatServer.log("Registration successful.");
                return true;
            }
        } catch (SQLException e) {
            ChatServer.log(e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the user trying to make requests is a valid user in the database.
     * 
     * @param username username provided by the client that the method searches 
     * for in the database
     * @param password password provided by the client that is compared to the 
     * hashed password in the database
     * @return {@code true} if validating user was successful, otherwise returns {@code false}
     */
    public boolean validateUser(String username, String password) {
        String query = "select password from users where username='" + username + "'";
        Statement queryStatement = null;
        ResultSet rs = null;

        try {
            queryStatement = dbConnection.createStatement();
            rs = queryStatement.executeQuery(query);
            if (!rs.next()) {
                ChatServer.log("Login failed: Invalid credentials.");
                return false;
            }
            String hashedPassword = rs.getString("password");
            if (hashedPassword.equals(Crypt.crypt(password, hashedPassword))) {
                return true;
            } else {
                ChatServer.log("Login failed: Invalid credentials.");
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (queryStatement != null) {
                    queryStatement.close();
                }
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Inserts a message to the database
     * 
     * @param message A {@code ChatMessage} object to be inserted into the database
     * @throws SQLException if a database access error occurs or this method 
     * is called on a closed connection
     */
    public void insertMessage(ChatMessage message) throws SQLException{
        String msg = message.getMessage();
        msg = msg.replaceAll("\"", "''"); // Handles quotes
        String insert = "insert into messages values (\"" + message.getNick() + "\", \"" + msg + "\", " + message.dateAsInt() + ")";

        Statement insertStatement = dbConnection.createStatement();
        insertStatement.executeUpdate(insert);
        insertStatement.close();
    }

    /**
     * Gets messages from the database.
     * 
     * @param since {@code long} int that defines the timestamp from which point onwards this 
     * method should get the messages. If -1, this method returns the last 20 messages
     * @return messages in an {@code ArrayList<ChatMessage>} or {@code null} if an 
     * exception occurs
     */
    public ArrayList<ChatMessage> getMessages(long since) {
        String query = "";
        if (since != -1) {
            query = "select * from messages where sent > " + since + " order by sent asc";
        } else {
            query = "select * from (select * from messages order by sent desc limit 20) order by sent asc";
        }
        try {
            Statement queryStatement = dbConnection.createStatement();
            ResultSet rs = queryStatement.executeQuery(query);
            if (rs == null) {
                queryStatement.close();
                return null;
            } else {
                ArrayList<ChatMessage> messages = new ArrayList<>();
                while (rs.next()) {
                    ChatMessage message = new ChatMessage();
                    message.setNick(rs.getString("user"));
                    message.setMessage(rs.getString("message"));
                    message.setSent(rs.getLong("sent"));
                    messages.add(message);
                }
                queryStatement.close();
                return messages;
            }
        } catch (SQLException e) {
            ChatServer.log(e.getMessage());
            return null;
        }
    }

    /**
     * Returns the number of messages stored in the database.
     * 
     * @return the number of messages in the database or 0 if an exception occurs
     */
    public int numberOfMessages() {
        String query = "select count(*) from messages";
        try {
            Statement queryStatement = dbConnection.createStatement();
            ResultSet rs = queryStatement.executeQuery(query);
            int count = rs.getInt("count(*)");
            queryStatement.close();
            return count;
        } catch (SQLException e) {
            ChatServer.log(e.getMessage());
            return 0;
        }
    }

    /**
     * Checks whether the database does not have any messages in it.
     * 
     * @return {@code true} if there are no messages in the database, 
     * {@code false} otherwise
     */
    public boolean isEmpty() {
        String query = "select count(*) from messages";
        try {
            Statement queryStatement = dbConnection.createStatement();
            ResultSet rs = queryStatement.executeQuery(query);
            if (rs.getBoolean("count(*)")) {
                queryStatement.close();
                return false;
            } else {
                queryStatement.close();
                return true;
            }
        } catch (SQLException e) {
            ChatServer.log(e.getMessage());
            return true;
        }
    }

    /**
     * Closes the connection to the database.
     * 
     * @throws SQLException if a database access error occurs
     */
    public void close() throws SQLException {
        dbConnection.close();
        ChatServer.log("Database closed.");
    }

}
