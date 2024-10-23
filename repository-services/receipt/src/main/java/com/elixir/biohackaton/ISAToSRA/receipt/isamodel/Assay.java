package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Assay {
  @JsonProperty("@id")
  public String id;

  public String filename;
  public MeasurementType measurementType;
  public TechnologyType technologyType;
  public String technologyPlatform;
  public ArrayList<CharacteristicCategory> characteristicCategories;
  public Materials materials;
  public ArrayList<ProcessSequence> processSequence;
  public ArrayList<DataFile> dataFiles;
  public ArrayList<Object> unitCategories;
}
