package com.tuomasmattila.chatserver;

/**
 * A class for creating objects containing the HTTP status codes 
 * and response messages of results. Can hold the HTTP status code
 * as an {@code int} and the response message as a {@code String}.
 */
public class Result {

    private int code;
    private String response;

    public Result() {
        this.code = 0;
        this.response = "";
    }

    public Result(int code, String response){
        this.code = code;
        this.response = response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public void printResult() {
        System.out.println("Code " + code + ": " + response);
    }
    
}
