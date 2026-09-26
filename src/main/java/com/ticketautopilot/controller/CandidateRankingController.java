package com.ticketautopilot.controller;

import com.ticketautopilot.dto.CandidateRankingResult;
import com.ticketautopilot.dto.RankCandidatesRequest;
import com.ticketautopilot.service.CandidateRankingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/candidate-ranking")
public class CandidateRankingController {

    private final CandidateRankingService rankingService;

    public CandidateRankingController(CandidateRankingService rankingService) {
        this.rankingService = rankingService;
    }

    @PostMapping
    public List<CandidateRankingResult> rank(@Valid @RequestBody RankCandidatesRequest request) {
        return rankingService.rank(request.jobTitle(), request.jobDescription(), request.candidates());
    }
}
