package com.gateready.engage;

public interface EmailSender {
    void send(String to, String subject, String body);
}
