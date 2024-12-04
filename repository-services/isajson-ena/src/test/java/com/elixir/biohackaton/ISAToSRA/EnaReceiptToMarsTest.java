/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.service.MarsReceiptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
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
    MarsReceiptService marsReceiptService = new MarsReceiptService();
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
        marsReceiptService.convertReceiptToMars(receipt, isaJson);
      } catch (MarsReceiptException e) {
        marsReceiptService.setMarsReceiptErrors(e.getError());
      } catch (Exception e) {
        marsReceiptService.setMarsReceiptErrors(e.getMessage());
      }

      // Saving the result as a Json file
      Files.write(
          new File(marsReceiptPath).toPath(),
          jsonMapper.writeValueAsBytes(marsReceiptService.getMarsReceipt()));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
