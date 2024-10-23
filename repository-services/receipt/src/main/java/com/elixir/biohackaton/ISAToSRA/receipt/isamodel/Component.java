package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Component {
  public String componentName;
  public ComponentType componentType;
}
