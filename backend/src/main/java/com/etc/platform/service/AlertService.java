package com.etc.platform.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final SimpMessagingTemplate messagingTemplate;

    
    public AlertService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    
    public void sendFakePlateAlert(String plate, String message) {
        AlertMessage alert = new AlertMessage("fakeplate", plate, message);
        messagingTemplate.convertAndSend("/topic/alert", alert);
    }

    
    public void sendCongestionAlert(String gantryId, String level) {
        AlertMessage alert = new AlertMessage("congestion", gantryId, "congestion level: " + level);
        messagingTemplate.convertAndSend("/topic/alert", alert);
    }

    
    private record AlertMessage(String type, String title, String content) {
    }
}