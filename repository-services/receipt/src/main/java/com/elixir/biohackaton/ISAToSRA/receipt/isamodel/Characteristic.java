package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;

@Data
public class Characteristic {
  public Category category;
  public Value value;
  public Unit unit;
}
