package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import java.util.ArrayList;
import lombok.Data;

@Data
public class Unit {
  public String termSource;
  public String termAccession;
  public ArrayList<Object> comments;
}
