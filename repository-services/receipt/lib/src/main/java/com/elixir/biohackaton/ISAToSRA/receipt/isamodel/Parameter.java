/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Parameter {
  @JsonProperty("@id")
  public String id;

  public ParameterName parameterName;
}
