package com.elixir.biohackaton.ISAToSRA.sra.model;

import lombok.Builder;
import lombok.Data;
import lombok.Builder.Default;

import java.util.List;
import java.util.ArrayList;

@Builder
@Data
public class MarsReceiptMessage {
    @Default
    private List<MarsReceiptError> errors = new ArrayList<>();

    @Default
    private List<MarsReceiptInfo> info= new ArrayList<>();
}
