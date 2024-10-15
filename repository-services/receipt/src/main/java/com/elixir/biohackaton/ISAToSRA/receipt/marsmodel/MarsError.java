package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsError {
  public MarsErrorType type;

  public String message;

  @JsonInclude(Include.NON_NULL)
  public List<MarsPath> path;
}
