package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Role {
  public String termAccession;
  public String termSource;
  public String annotationValue;
}
