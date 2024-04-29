package com.elixir.biohackaton.ISAToSRA.sra.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceiptWhere {
    private String key;

    private String value;
}
