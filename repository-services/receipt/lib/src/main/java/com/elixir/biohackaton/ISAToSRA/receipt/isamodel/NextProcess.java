/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.isamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NextProcess {
  @JsonProperty("@id")
  public String id;
}
