package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Comment {
  public String name;
  public String value;

  @JsonProperty("@id")
  public String id;
}
