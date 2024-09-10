/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MarsWhere {
  private String key;

  private String value;
}
