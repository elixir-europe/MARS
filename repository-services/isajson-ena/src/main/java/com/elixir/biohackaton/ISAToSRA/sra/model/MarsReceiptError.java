/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceiptError {
  private MarsReceiptErrorType type;

  private String message;

  @JsonInclude(Include.NON_NULL)
  private MarsReceiptPath[] path;
}
