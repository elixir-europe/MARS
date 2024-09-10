/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Builder
@Data
public class MarsMessage {
  @Default private List<MarsError> errors = new ArrayList<>();

  @Default private List<MarsInfo> info = new ArrayList<>();
}
