package com.ticketautopilot.repository;

import com.ticketautopilot.domain.Decision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
}
