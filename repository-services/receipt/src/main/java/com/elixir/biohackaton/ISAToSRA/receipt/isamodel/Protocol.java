package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Protocol {
  @JsonProperty("@id")
  public String id;

  public String name;
  public ProtocolType protocolType;
  public String description;
  public String uri;
  public String version;
  public ArrayList<Parameter> parameters;
  public ArrayList<Component> components;
}
