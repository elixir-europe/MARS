package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Investigation {
  public String identifier;
  public String title;
  public String description;
  public String submissionDate;
  public String publicReleaseDate;
  public ArrayList<Object> ontologySourceReferences;
  public String filename;
  public ArrayList<Comment> comments;
  public ArrayList<Object> publications;
  public ArrayList<Person> people;
  public ArrayList<Study> studies;
}
