package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Materials {
  public ArrayList<Source> sources;
  public ArrayList<Sample> samples;
  public ArrayList<OtherMaterial> otherMaterials;
}
