package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Person {
  @JsonProperty("@id")
  public String id;

  public String lastName;
  public String firstName;
  public String midInitials;
  public String email;
  public String phone;
  public String fax;
  public String address;
  public String affiliation;
  public ArrayList<Role> roles;
  public ArrayList<Comment> comments;
}
