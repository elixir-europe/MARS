package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsPath {
  public String key;

  @JsonInclude(Include.NON_NULL)
  public MarsWhere where;
}
