/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.BioSamplesSubmitter;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.MarsReceiptService;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
class BiosampleReceiptToMarsTest {

  @Test
  void convertToMars() {
    try {

      // Reading Inputs
      String isaJsonFilePath = "../../test-data/biosamples-input-isa.json";
      String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());

      // Try
      // https://wwwdev.ebi.ac.uk/ena/submit/webin/auth/swagger-ui/index.html#/AuthenticationAPI/getToken to get the token
      String webinToken = "";
      if (webinToken.isEmpty()) {
        return; // Ignore the test when the token is not prepared
      }

      // Mapping inputs to the proper objects
      ObjectMapper jsonMapper = new ObjectMapper();
      BioSamplesSubmitter bioSamplesSubmitter = new BioSamplesSubmitter();
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      final IsaJson isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
      final List<Study> studies = isaJson.getInvestigation().getStudies();
      final BiosampleAccessionsMap accessionsMap =
          bioSamplesSubmitter.createBioSamples(studies, webinToken);

      // Converting Biosample receipt to MARS receipt
      MarsReceiptService marsReceiptService = new MarsReceiptService();
      marsReceiptService.convertReceiptToMars(accessionsMap, isaJson);

      // Saving the result as a Json file
      String marsReceiptPath = "../../test-data/mars-biosample-receipt.json";
      Files.write(new File(marsReceiptPath).toPath(), jsonMapper.writeValueAsBytes(marsReceiptService.getMarsReceipt()));
    } catch (Exception ex) {
      System.console().printf("%s", ex);
    }
  }
}
