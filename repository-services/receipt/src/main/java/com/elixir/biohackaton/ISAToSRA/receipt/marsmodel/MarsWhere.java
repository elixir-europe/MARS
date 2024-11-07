package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsWhere {
  public String key;

  public String value;
}
