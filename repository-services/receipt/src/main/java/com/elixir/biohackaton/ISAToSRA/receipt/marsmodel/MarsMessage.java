package com.elixir.biohackaton.ISAToSRA.receipt.marsmodel;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Builder
@Data
public class MarsMessage {
  @Default public List<MarsError> errors = new ArrayList<>();

  @Default public List<MarsInfo> info = new ArrayList<>();
}
