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

public class ChatDatabase {

    private static ChatDatabase singleton = null;
    private static Connection dbConnection = null;
    private static SecureRandom secureRandom = new SecureRandom();

    /**
     * 
     * @return
     */
    public static synchronized ChatDatabase getInstance() {
        if (null == singleton) {
            singleton = new ChatDatabase();
        }
        return singleton;
    }

    /**
     * 
     */
    private ChatDatabase() {

    }

    /**
     * 
     * @param dbName
     * @throws SQLException
     */
    public void open(String dbName) throws SQLException {
        File tempfile = new File(dbName);
        boolean isFileOrDir = false;

        if (tempfile.isDirectory() || tempfile.isFile()) {
            isFileOrDir = true;
        }
        String JDBCConnectionAddress = "jdbc:sqlite:" + dbName;
        try {
            dbConnection = DriverManager.getConnection(JDBCConnectionAddress);
        } catch (SQLException e) {
            ChatServer.log(e.getMessage());
        }
        ChatServer.log("Connected to the database.");
        if (isFileOrDir == false) {
            ChatServer.log("Initializing database...");
            initializeDatabase(dbConnection);
        }
    }

    /**
     * 
     * @param dbConnection
     */
    public void initializeDatabase(Connection dbConnection) {
        try {
            Statement createStatement = dbConnection.createStatement();
            createStatement.execute("create table users (username varchar(50) PRIMARY KEY, password varchar(100) NOT NULL, salt varchar(100) NOT NULL, email varchar(100) NOT NULL)");
            createStatement.execute("create table messages (user varchar(50) NOT NULL, message varchar(255) NOT NULL, sent integer NOT NULL, PRIMARY KEY(user, sent), FOREIGN KEY(user) REFERENCES users(username))");
            createStatement.close();
            ChatServer.log("Database successfully initialized.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     * @param user
     * @return
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
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 
     * @param username
     * @param password
     * @return
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
     * 
     * @param message
     */
    public void insertMessage(ChatMessage message) throws SQLException{
        //String msg = message.getMessage();
        //msg = msg.replaceAll("\"", "''"); // Handles quotes, find a better solution if you can
        //String insert = "insert into messages values (\"" + message.getNick() + "\", \"" + msg + "\", " + message.dateAsInt() + ")";
        String insert = "insert into messages values (\"" + message.getNick() + "\", \"" + message.getMessage() + "\", " + message.dateAsInt() + ")";
        
        try {
            Statement insertStatement = dbConnection.createStatement();
            insertStatement.executeUpdate(insert);
            insertStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     * @return
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
            ChatServer.log("SQLite error: no such table: messages");
            return null;
        }
    }

    /**
     * 
     * @return
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
            ChatServer.log("SQLite error: no such table: messages");
            return 0;
        }
    }

    /**
     * 
     * @return
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
            ChatServer.log("SQLite error: no such table: messages");
            return true;
        }
    }

    public void close() {
        try {
            dbConnection.close();
            ChatServer.log("Database closed.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
