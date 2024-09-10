/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import lombok.Data;

@Data
public class ParameterValue {
  public Category category;
  public Value value;
  public Unit unit;
}
