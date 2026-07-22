/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.mars.repository.models.isa.*;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinExperimentXmlCreator;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinRunXmlCreator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebinRunXmlCreatorTest {

  private WebinRunXmlCreator runXmlCreator;
  private WebinExperimentXmlCreator experimentXmlCreator;
  private ObjectMapper objectMapper;
  private IsaJson isaJson;
  private IsaJson multiIsaJson;

  @BeforeEach
  void setUp() throws Exception {
    runXmlCreator = new WebinRunXmlCreator();
    experimentXmlCreator = new WebinExperimentXmlCreator();
    objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Load ISA JSON file
    String isaJsonFilePath = "../../test-data/biosamples-input-isa.json";
    String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());
    isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);

    String multiIsaJsonFilePath = "../../test-data/biosamples-input-isa-multi.json";
    String multiIsaJsonFile = Files.readString(new File(multiIsaJsonFilePath).toPath());
    multiIsaJson = objectMapper.readValue(multiIsaJsonFile, IsaJson.class);
  }

  @Test
  void testCreateENARunSetElement() throws Exception {
    // Arrange
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = isaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "test-123";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    // First create experiments to get the experiment sequence map
    final Map<String, String> experimentSequenceMap =
        experimentXmlCreator.createENAExperimentSetElement(
            bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    Assertions.assertNotNull(experimentSequenceMap, "Experiment sequence map should not be null");
    Assertions.assertFalse(
        experimentSequenceMap.isEmpty(), "Experiment sequence map should not be empty");

    // Act - Create RUN elements
    runXmlCreator.createENARunSetElement(
        webinElement, studies, experimentSequenceMap, randomSubmissionIdentifier);

    // Assert
    // Verify RUN_SET element exists
    final Element runSet = webinElement.element("RUN_SET");
    Assertions.assertNotNull(runSet, "RUN_SET element should be created in the XML");

    // Verify RUN elements exist
    @SuppressWarnings("unchecked")
    final List<Element> runs = runSet.elements("RUN");
    Assertions.assertFalse(runs.isEmpty(), "At least one RUN element should be created");

    // Verify first run structure
    final Element firstRun = runs.get(0);
    Assertions.assertNotNull(firstRun.attribute("alias"), "RUN should have an alias attribute");

    // Verify TITLE element
    final Element titleElement = firstRun.element("TITLE");
    Assertions.assertNotNull(titleElement, "RUN should have a TITLE element");

    // Verify EXPERIMENT_REF element
    final Element experimentRef = firstRun.element("EXPERIMENT_REF");
    Assertions.assertNotNull(experimentRef, "RUN should have an EXPERIMENT_REF element");
    Assertions.assertNotNull(
        experimentRef.attribute("refname"), "EXPERIMENT_REF should have a refname attribute");

    // Verify the refname matches an experiment from the experiment sequence map
    final String refname = experimentRef.attributeValue("refname");
    Assertions.assertTrue(
        experimentSequenceMap.containsValue(refname),
        "EXPERIMENT_REF refname should match an experiment ID from the sequence map");

    // Verify DATA_BLOCK element
    final Element dataBlock = firstRun.element("DATA_BLOCK");
    Assertions.assertNotNull(dataBlock, "RUN should have a DATA_BLOCK element");

    // Verify FILES element
    final Element files = dataBlock.element("FILES");
    Assertions.assertNotNull(files, "DATA_BLOCK should have a FILES element");

    // Verify FILE element
    @SuppressWarnings("unchecked")
    final List<Element> fileElements = files.elements("FILE");
    Assertions.assertFalse(fileElements.isEmpty(), "FILES should contain at least one FILE");

    final Element fileElement = fileElements.get(0);
    Assertions.assertNotNull(
        fileElement.attribute("filename"), "FILE should have a filename attribute");
    Assertions.assertNotNull(
        fileElement.attribute("filetype"), "FILE should have a filetype attribute");
    Assertions.assertNotNull(
        fileElement.attribute("checksum"), "FILE should have a checksum attribute");
    Assertions.assertEquals(
        "MD5",
        fileElement.attributeValue("checksum_method"),
        "FILE should have checksum_method set to MD5");

    // Print XML for debugging (optional)
    final OutputFormat format = OutputFormat.createPrettyPrint();
    final XMLWriter writer = new XMLWriter(System.out, format);
    System.out.println("\n=== Generated ENA Run XML ===");
    writer.write(document);
  }

  @Test
  void testCreateENARunSetElementWithMultipleDataFiles() throws Exception {
    // This test verifies that paired files stay in one run while a separate
    // single-end experiment creates its own run.
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = multiIsaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "test-456";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    // First create experiments
    final Map<String, String> experimentSequenceMap =
        experimentXmlCreator.createENAExperimentSetElement(
            bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    // Create runs
    runXmlCreator.createENARunSetElement(
        webinElement, studies, experimentSequenceMap, randomSubmissionIdentifier);

    final Element runSet = webinElement.element("RUN_SET");
    Assertions.assertNotNull(runSet);

    @SuppressWarnings("unchecked")
    final List<Element> runs = runSet.elements("RUN");
    Assertions.assertEquals(2, runs.size(), "Expected one paired run and one single-end run");

    final Element pairedRun =
        runs.stream()
            .filter(
                run ->
                    "#process/nucleic_acid_sequencing/334-test-456"
                        .equals(run.attributeValue("alias")))
            .findFirst()
            .orElse(null);
    Assertions.assertNotNull(pairedRun, "Expected a run for the paired sequencing process");

    final Element pairedFiles = pairedRun.element("DATA_BLOCK").element("FILES");
    @SuppressWarnings("unchecked")
    final List<Element> pairedFileElements = pairedFiles.elements("FILE");
    Assertions.assertEquals(
        2, pairedFileElements.size(), "Paired sequencing run should include two FASTQ files");

    final Element singleRun =
        runs.stream()
            .filter(
                run ->
                    "#process/nucleic_acid_sequencing/339-test-456"
                        .equals(run.attributeValue("alias")))
            .findFirst()
            .orElse(null);
    Assertions.assertNotNull(singleRun, "Expected a run for the single-end sequencing process");

    final Element singleFiles = singleRun.element("DATA_BLOCK").element("FILES");
    @SuppressWarnings("unchecked")
    final List<Element> singleFileElements = singleFiles.elements("FILE");
    Assertions.assertEquals(
        1, singleFileElements.size(), "Single-end sequencing run should contain one FASTQ file");
  }
}
