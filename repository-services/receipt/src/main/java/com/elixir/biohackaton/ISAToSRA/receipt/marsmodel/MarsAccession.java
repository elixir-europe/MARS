package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsAccession {
  public String value;

  public List<MarsPath> path;
}
