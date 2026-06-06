package com.example.scicalculator.web.dto;

import com.example.scicalculator.domain.Operation;
import com.example.scicalculator.service.StepRevision;

import java.math.BigDecimal;
import java.time.Instant;

/** Web view of one step revision — mirrors {@link StepRevision}, the body element of the history endpoint. */
public record HistoryEntryResponse(
        int revision, String username, Instant timestamp,
        int sequenceNumber, Operation operation,
        BigDecimal operand, BigDecimal resultAfter) {

    public static HistoryEntryResponse from(StepRevision r) {
        return new HistoryEntryResponse(r.revision(), r.username(), r.timestamp(),
                r.sequenceNumber(), r.operation(), r.operand(), r.resultAfter());
    }
}
