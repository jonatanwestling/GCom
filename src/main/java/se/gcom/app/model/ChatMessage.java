package se.gcom.app.model;

public class ChatMessage {
    private final String sender;
    private final String text;
    private final boolean outgoing;

    public ChatMessage(String sender, String text, boolean outgoing) {
        this.sender = sender;
        this.text = text;
        this.outgoing = outgoing;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public boolean isOutgoing() {
        return outgoing;
    }
}