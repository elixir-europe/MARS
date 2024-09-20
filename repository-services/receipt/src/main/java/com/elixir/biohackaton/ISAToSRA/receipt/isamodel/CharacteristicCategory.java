package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CharacteristicCategory {
  @JsonProperty("@id")
  public String id;

  public CharacteristicType characteristicType;
}
