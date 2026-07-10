/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.BioSample;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.BioSamplesSubmitter;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.MarsReceiptService;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsReceipt;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
      MarsReceipt marsReceipt = marsReceiptService.convertReceiptToMars(accessionsMap, isaJson);

      // Saving the result as a Json file
      String marsReceiptPath = "../../test-data/mars-biosample-receipt.json";
      Files.write(new File(marsReceiptPath).toPath(), jsonMapper.writeValueAsBytes(marsReceipt));
    } catch (Exception ex) {
      System.console().printf("%s", ex);
    }
  }

  @Test
  void createBioSamplesUsesSampleCollectionProcessParametersForChildSampleAttributes()
      throws Exception {
    final String isaJsonFilePath = "../../test-data/biosamples-input-isa.json";
    final String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());
    final ObjectMapper objectMapper = new ObjectMapper();
    final IsaJson isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
    final CapturingBioSamplesSubmitter bioSamplesSubmitter = new CapturingBioSamplesSubmitter();

    bioSamplesSubmitter.createBioSamples(isaJson.getInvestigation().getStudies(), "test-token");

    final BioSample leafSample =
        bioSamplesSubmitter.updatedSamples.stream()
            .filter(sample -> "leaf 1".equals(sample.getName()))
            .findFirst()
            .orElseThrow();

    Assertions.assertTrue(
        leafSample.getAttributes().stream()
            .anyMatch(
                attribute ->
                    "isolation_source".equals(attribute.getType())
                        && "seedling leaf tissue".equals(attribute.getValue())),
        "Expected child BioSample to include the sample-collection process parameter.");
  }

  private static class CapturingBioSamplesSubmitter extends BioSamplesSubmitter {
    private final List<BioSample> updatedSamples = new ArrayList<>();
    private int accessionSequence = 1;

    @Override
    protected BioSample createSampleInBioSamples(final BioSample sample, final String webinToken) {
      return BioSample.Builder.fromSample(sample)
          .withAccession("SAMEA_TEST_" + accessionSequence++)
          .build();
    }

    @Override
    protected BioSample updateSampleWithRelationshipsToBioSamples(
        final BioSample sampleWithRelationship, final String webinToken) {
      updatedSamples.add(sampleWithRelationship);
      return sampleWithRelationship;
    }
  }
}
