package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class Sample {
  @JsonProperty("@id")
  public String id;

  public String name;
  public ArrayList<DerivesFrom> derivesFrom;
  public ArrayList<Characteristic> characteristics;
  public ArrayList<FactorValue> factorValues;
}
