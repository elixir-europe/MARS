package com.elixir.biohackaton.ISAToSRA.sra.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceipt {
    private String targetRepository;

    private String[] errors;

    private String[] info;

    private MarsReceiptAccession[] accessions;
}
