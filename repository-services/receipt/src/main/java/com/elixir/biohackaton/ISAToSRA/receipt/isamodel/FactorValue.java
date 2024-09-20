package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;

@Data
public class FactorValue {
  public Category category;
  public Value value;
  public Unit unit;
}
