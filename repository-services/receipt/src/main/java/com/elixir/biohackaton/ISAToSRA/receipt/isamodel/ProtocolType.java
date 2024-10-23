package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class ProtocolType {
  public String annotationValue;
  public String termAccession;
  public String termSource;
}
