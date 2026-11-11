package com.example.hireme.ai;

// One message of a conversation with the model: what the user said or what the assistant answered.
public record ChatTurn(Role role, String text) {

    public enum Role { USER, ASSISTANT }

    public static ChatTurn user(String text) {
        return new ChatTurn(Role.USER, text);
    }

    public static ChatTurn assistant(String text) {
        return new ChatTurn(Role.ASSISTANT, text);
    }
}
