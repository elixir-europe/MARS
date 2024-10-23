/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.*;
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
  void convertToMars() {
    try {

      // Reading Inputs
      String enaReceiptFilePath = "../../test-data/ena-receipt.json";
      String isaJsonFilePath = "../../test-data/biosamples-input-isa.json";
      String receiptFile = Files.readString(new File(enaReceiptFilePath).toPath());
      String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());

      // Mapping inputs to the proper objects
      ObjectMapper jsonMapper = new ObjectMapper();
      Receipt receipt = jsonMapper.readValue(receiptFile, Receipt.class);
      IsaJson isaJson = jsonMapper.readValue(isaJsonFile, IsaJson.class);

      // Converting ENA receipt to MARS receipt
      MarsReceiptService marsReceiptService = new MarsReceiptService();
      MarsReceipt marsReceipt = marsReceiptService.convertReceiptToMars(receipt, isaJson);

      // Saving the result as a Json file
      String marsReceiptPath = "../../test-data/mars-ena-receipt.json";
      Files.write(new File(marsReceiptPath).toPath(), jsonMapper.writeValueAsBytes(marsReceipt));
    } catch (Exception ex) {
      System.console().printf("%s", ex);
    }
  }
}
