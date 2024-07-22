/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Builder
@Data
public class MarsReceiptMessage {
  @Default private List<MarsReceiptError> errors = new ArrayList<>();

  @Default private List<MarsReceiptInfo> info = new ArrayList<>();
}
