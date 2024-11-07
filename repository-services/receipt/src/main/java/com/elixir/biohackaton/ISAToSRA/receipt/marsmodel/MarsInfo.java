package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsInfo {
  @JsonInclude(Include.NON_NULL)
  public String name;

  public String message;
}
