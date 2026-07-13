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
import com.fasterxml.jackson.databind.DeserializationFeature;
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
      ObjectMapper objectMapper = isaJsonObjectMapper();
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
    final ObjectMapper objectMapper = isaJsonObjectMapper();
    final IsaJson isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
    final CapturingBioSamplesSubmitter bioSamplesSubmitter = new CapturingBioSamplesSubmitter();

    bioSamplesSubmitter.createBioSamples(isaJson.getInvestigation().getStudies(), "test-token");

    final BioSample leafSample =
        bioSamplesSubmitter.updatedSamples.stream()
            .filter(sample -> "leaf 1".equals(sample.getName()))
            .findFirst()
            .orElseThrow();
    final BioSample sourceSample = bioSamplesSubmitter.createdSampleByName("plant 1");

    Assertions.assertTrue(hasAttribute(sourceSample, "genotype", "Col-0"));
    Assertions.assertTrue(
        hasAttribute(sourceSample, "growth condition", "16 h light / 8 h dark growth chamber"));
    Assertions.assertTrue(hasAttribute(sourceSample, "dev_stage", "vegetative rosette stage"));
    Assertions.assertTrue(hasAttribute(sourceSample, "isolation_source", "whole plant"));
    Assertions.assertTrue(hasAttribute(leafSample, "organism part", "leaf"));
    Assertions.assertTrue(
        hasAttribute(
            leafSample,
            "sample description",
            "young rosette leaf collected for DNA extraction"));
    Assertions.assertTrue(
        leafSample.getAttributes().stream()
            .anyMatch(
                attribute ->
                    "isolation_source".equals(attribute.getType())
                        && "leaf tissue".equals(attribute.getValue())),
        "Expected child BioSample to include the sample-collection process parameter.");
    Assertions.assertTrue(hasAttribute(leafSample, "collection method", "sterile scalpel excision"));
    Assertions.assertTrue(
        hasAttribute(leafSample, "sample preservation", "flash frozen in liquid nitrogen"));
  }

  @Test
  void createBioSamplesKeepsEachChildSampleLinkedToItsOwnSource() throws Exception {
    final String isaJsonFilePath = "../../test-data/biosamples-input-isa-multi.json";
    final String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());
    final ObjectMapper objectMapper = isaJsonObjectMapper();
    final IsaJson isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
    final CapturingBioSamplesSubmitter bioSamplesSubmitter = new CapturingBioSamplesSubmitter();

    bioSamplesSubmitter.createBioSamples(isaJson.getInvestigation().getStudies(), "test-token");

    final BioSample leafSample = bioSamplesSubmitter.updatedSampleByName("leaf 1");
    final BioSample rootSample = bioSamplesSubmitter.updatedSampleByName("root 1");

    Assertions.assertEquals("SAMEA_TEST_1", derivedFromTarget(leafSample));
    Assertions.assertEquals("SAMEA_TEST_2", derivedFromTarget(rootSample));
    Assertions.assertTrue(hasAttribute(rootSample, "collection date", "2022-01-03"));
    Assertions.assertTrue(hasAttribute(rootSample, "organism part", "root"));
    Assertions.assertTrue(hasAttribute(rootSample, "isolation_source", "root tissue"));
    Assertions.assertTrue(hasAttribute(rootSample, "collection method", "washed root excision"));
    Assertions.assertTrue(
        hasAttribute(rootSample, "sample preservation", "flash frozen in liquid nitrogen"));
  }

  @Test
  void createBioSamplesSupportsMultipleChildSamplesFromTheSameSource() throws Exception {
    final String isaJsonFilePath = "../../test-data/biosamples-input-isa-multi.json";
    final String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());
    final ObjectMapper objectMapper = isaJsonObjectMapper();
    final IsaJson isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
    final Study study = isaJson.getInvestigation().getStudies().get(0);

    study.getMaterials().getSamples().stream()
        .filter(sample -> "root 1".equals(sample.getName()))
        .findFirst()
        .orElseThrow()
        .getDerivesFrom()
        .get(0)
        .setId("#source/330");
    study.getProcessSequence().stream()
        .filter(process -> "#process/sample_collection/431".equals(process.getId()))
        .findFirst()
        .orElseThrow()
        .getInputs()
        .get(0)
        .setId("#source/330");

    final CapturingBioSamplesSubmitter bioSamplesSubmitter = new CapturingBioSamplesSubmitter();
    bioSamplesSubmitter.createBioSamples(isaJson.getInvestigation().getStudies(), "test-token");

    final BioSample leafSample = bioSamplesSubmitter.updatedSampleByName("leaf 1");
    final BioSample rootSample = bioSamplesSubmitter.updatedSampleByName("root 1");

    Assertions.assertEquals("SAMEA_TEST_1", derivedFromTarget(leafSample));
    Assertions.assertEquals("SAMEA_TEST_1", derivedFromTarget(rootSample));
    Assertions.assertTrue(hasAttribute(rootSample, "organism part", "root"));
    Assertions.assertTrue(hasAttribute(rootSample, "collection method", "washed root excision"));
  }

  private static String derivedFromTarget(final BioSample sample) {
    return sample.getRelationships().stream()
        .filter(relationship -> "derived from".equals(relationship.getType()))
        .findFirst()
        .orElseThrow()
        .getTarget();
  }

  private static boolean hasAttribute(
      final BioSample sample, final String attributeType, final String attributeValue) {
    return sample.getAttributes().stream()
        .anyMatch(
            attribute ->
                attributeType.equals(attribute.getType())
                    && attributeValue.equals(attribute.getValue()));
  }

  private static ObjectMapper isaJsonObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }

  private static class CapturingBioSamplesSubmitter extends BioSamplesSubmitter {
    private final List<BioSample> createdSamples = new ArrayList<>();
    private final List<BioSample> updatedSamples = new ArrayList<>();
    private int accessionSequence = 1;

    @Override
    protected BioSample createSampleInBioSamples(final BioSample sample, final String webinToken) {
      final BioSample createdSample =
          BioSample.Builder.fromSample(sample)
              .withAccession("SAMEA_TEST_" + accessionSequence++)
              .build();
      createdSamples.add(createdSample);
      return createdSample;
    }

    @Override
    protected BioSample updateSampleWithRelationshipsToBioSamples(
        final BioSample sampleWithRelationship, final String webinToken) {
      updatedSamples.add(sampleWithRelationship);
      return sampleWithRelationship;
    }

    private BioSample updatedSampleByName(final String sampleName) {
      return updatedSamples.stream()
          .filter(sample -> sampleName.equals(sample.getName()))
          .findFirst()
          .orElseThrow();
    }

    private BioSample createdSampleByName(final String sampleName) {
      return createdSamples.stream()
          .filter(sample -> sampleName.equals(sample.getName()))
          .findFirst()
          .orElseThrow();
    }
  }
}
