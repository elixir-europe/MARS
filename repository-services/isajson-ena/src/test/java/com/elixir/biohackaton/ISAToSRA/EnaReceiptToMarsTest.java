/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.models.receipt.MarsError;
import com.elixir.mars.repository.models.receipt.MarsErrorType;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.service.MarsReceiptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EnaReceiptToMarsTest {

  @Test
  void convertToMars_validInput() {
    String enaReceiptFilePath = "../../test-data/ena-receipt.json";
    String marsReceiptPath = "../../test-data/mars-ena-receipt.json";
    convertToMars(enaReceiptFilePath, marsReceiptPath);
  }

  @Test
  void convertToMars_invalidInput() {
    String enaReceiptFilePath = "../../test-data/ena-receipt-invalid.json";
    String marsReceiptPath = "../../test-data/mars-ena-receipt-invalid.json";
    convertToMars(enaReceiptFilePath, marsReceiptPath);
  }

  void convertToMars(final String enaReceiptFilePath, final String marsReceiptPath) {
    ObjectMapper jsonMapper = new ObjectMapper();
    MarsReceipt marsReceipt;
    try {
      try {
        // Reading Inputs
        String receiptFile = Files.readString(new File(enaReceiptFilePath).toPath());
        String isaJsonFilePath = "../../test-data/biosamples-modified-isa.json";
        String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());

        // Mapping inputs to the proper objects
        Receipt receipt = jsonMapper.readValue(receiptFile, Receipt.class);
        IsaJson isaJson = jsonMapper.readValue(isaJsonFile, IsaJson.class);

        // Converting ENA receipt to MARS receipt
        marsReceipt = new MarsReceiptService().convertReceiptToMars(receipt, isaJson);
      } catch (MarsReceiptException e) {
        marsReceipt = errorReceipt(e.getError());
      } catch (Exception e) {
        marsReceipt = errorReceipt(
            MarsError.builder().message(String.valueOf(e.getMessage())).build());
      }

      Files.write(
          new File(marsReceiptPath).toPath(),
          jsonMapper.writeValueAsBytes(marsReceipt));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private MarsReceipt errorReceipt(final MarsError error) {
    return MarsReceipt.builder()
        .targetRepository("ena")
        .errors(List.of(
            MarsError.builder()
                .type(MarsErrorType.INVALID_METADATA)
                .message(error.message)
                .path(error.path)
                .build()))
        .info(List.of())
        .accessions(List.of())
        .build();
  }
}
