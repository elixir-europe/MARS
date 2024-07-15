/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceiptPath {
  private String key;

  @JsonInclude(Include.NON_NULL)
  private MarsReceiptWhere where;
}
