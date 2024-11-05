package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceipt {
  public String targetRepository;

  public List<MarsError> errors;

  public List<MarsInfo> info;

  public List<MarsAccession> accessions;
}
