package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Source {
  @JsonProperty("@id")
  public String id;

  public String name;
  public ArrayList<Characteristic> characteristics;
}
