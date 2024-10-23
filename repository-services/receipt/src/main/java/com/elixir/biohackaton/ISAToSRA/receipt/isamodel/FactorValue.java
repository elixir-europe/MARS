package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class FactorValue {
  public Category category;
  public Value value;
  public Unit unit;
}
