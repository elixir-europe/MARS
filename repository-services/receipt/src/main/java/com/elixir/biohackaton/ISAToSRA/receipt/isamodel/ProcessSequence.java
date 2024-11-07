package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessSequence {
  @JsonProperty("@id")
  public String id;

  public String name;
  public ExecutesProtocol executesProtocol;
  public ArrayList<ParameterValue> parameterValues;
  public String performer;
  public String date;
  public PreviousProcess previousProcess;
  public NextProcess nextProcess;
  public ArrayList<Input> inputs;
  public ArrayList<Output> outputs;
}
