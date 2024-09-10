/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsReceipt {
  private String targetRepository;

  private MarsError[] errors;

  private MarsInfo[] info;

  private MarsAccession[] accessions;
}
