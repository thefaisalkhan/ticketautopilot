package com.ticketautopilot.controller;

import com.ticketautopilot.dto.TicketDecisionResponse;
import com.ticketautopilot.dto.TicketRequest;
import com.ticketautopilot.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TriageService triageService;

    public TicketController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping
    public TicketDecisionResponse submit(@Valid @RequestBody TicketRequest request) {
        String engine = request.engine() != null ? request.engine() : "rule_based";
        return triageService.triage(request.subject(), request.body(), engine);
    }
}
