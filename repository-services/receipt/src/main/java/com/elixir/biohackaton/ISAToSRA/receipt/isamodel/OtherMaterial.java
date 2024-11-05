package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class OtherMaterial {
  @JsonProperty("@id")
  public String id; // other_material-333

  public String name;
  public String type; // = library name
  public ArrayList<Characteristic> characteristics; // -> get characteristics
  public ArrayList<DerivesFrom> derivesFrom;
}
