package com.zhyq.park.receivable.dto;

public record ReceivableGenerateResult(int totalCandidates, int inserted, int updated, int skipped) {
    public ReceivableGenerateResult(int totalCandidates, int inserted, int skipped) {
        this(totalCandidates, inserted, 0, skipped);
    }
}
