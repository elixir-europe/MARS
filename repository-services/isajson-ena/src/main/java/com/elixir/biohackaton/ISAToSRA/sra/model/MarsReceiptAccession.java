/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceiptAccession {
  private String value;

  private MarsReceiptPath[] path;
}
