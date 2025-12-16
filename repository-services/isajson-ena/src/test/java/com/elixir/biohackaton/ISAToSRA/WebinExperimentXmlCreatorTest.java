/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinExperimentXmlCreator;
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
class WebinExperimentXmlCreatorTest {

  private WebinExperimentXmlCreator experimentXmlCreator;
  private ObjectMapper objectMapper;
  private IsaJson isaJson;

  @BeforeEach
  void setUp() throws Exception {
    experimentXmlCreator = new WebinExperimentXmlCreator();
    objectMapper = new ObjectMapper();

    // Load ISA JSON file
    String isaJsonFilePath = "../../test-data/biosamples-input-isa.json";
    String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());
    isaJson = objectMapper.readValue(isaJsonFile, IsaJson.class);
  }

  @Test
  void testCreateENAExperimentSetElement() throws Exception {
    // Arrange
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = isaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "test-123";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    // Act
    final Map<String, String> experimentSequence =
        experimentXmlCreator.createENAExperimentSetElement(
            bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    // Assert
    Assertions.assertNotNull(experimentSequence, "Experiment sequence map should not be null");
    Assertions.assertFalse(
        experimentSequence.isEmpty(), "Experiment sequence map should not be empty");

    // Verify EXPERIMENT_SET element exists
    final Element experimentSet = webinElement.element("EXPERIMENT_SET");
    Assertions.assertNotNull(experimentSet, "EXPERIMENT_SET element should be created in the XML");

    // Verify EXPERIMENT elements exist
    @SuppressWarnings("unchecked")
    final List<Element> experiments = experimentSet.elements("EXPERIMENT");
    Assertions.assertFalse(
        experiments.isEmpty(), "At least one EXPERIMENT element should be created");

    // Verify first experiment structure
    final Element firstExperiment = experiments.get(0);
    Assertions.assertNotNull(
        firstExperiment.attribute("alias"), "EXPERIMENT should have an alias attribute");

    // Verify TITLE element
    final Element titleElement = firstExperiment.element("TITLE");
    Assertions.assertNotNull(titleElement, "EXPERIMENT should have a TITLE element");
    Assertions.assertFalse(titleElement.getText().isEmpty(), "TITLE should not be empty");

    // Verify STUDY_REF element
    final Element studyRef = firstExperiment.element("STUDY_REF");
    Assertions.assertNotNull(studyRef, "EXPERIMENT should have a STUDY_REF element");
    Assertions.assertNotNull(
        studyRef.attribute("refname"), "STUDY_REF should have a refname attribute");

    // Verify DESIGN element
    final Element design = firstExperiment.element("DESIGN");
    Assertions.assertNotNull(design, "EXPERIMENT should have a DESIGN element");

    // Verify DESIGN_DESCRIPTION
    final Element designDescription = design.element("DESIGN_DESCRIPTION");
    Assertions.assertNotNull(designDescription, "DESIGN should have a DESIGN_DESCRIPTION element");

    // Verify SAMPLE_DESCRIPTOR
    final Element sampleDescriptor = design.element("SAMPLE_DESCRIPTOR");
    Assertions.assertNotNull(sampleDescriptor, "DESIGN should have a SAMPLE_DESCRIPTOR element");
    Assertions.assertEquals(
        "SAMEA130793922",
        sampleDescriptor.attributeValue("accession"),
        "SAMPLE_DESCRIPTOR should have the correct accession");

    // Verify LIBRARY_DESCRIPTOR
    final Element libraryDescriptor = design.element("LIBRARY_DESCRIPTOR");
    Assertions.assertNotNull(libraryDescriptor, "DESIGN should have a LIBRARY_DESCRIPTOR element");

    // Verify PLATFORM element
    final Element platform = firstExperiment.element("PLATFORM");
    Assertions.assertNotNull(platform, "EXPERIMENT should have a PLATFORM element");

    // Verify OXFORD_NANOPORE element
    final Element oxfordNanopore = platform.element("OXFORD_NANOPORE");
    Assertions.assertNotNull(oxfordNanopore, "PLATFORM should have an OXFORD_NANOPORE element");

    // Verify INSTRUMENT_MODEL
    final Element instrumentModel = oxfordNanopore.element("INSTRUMENT_MODEL");
    Assertions.assertNotNull(
        instrumentModel, "OXFORD_NANOPORE should have an INSTRUMENT_MODEL element");

    // Print XML for debugging (optional)
    final OutputFormat format = OutputFormat.createPrettyPrint();
    final XMLWriter writer = new XMLWriter(System.out, format);
    System.out.println("\n=== Generated ENA Experiment XML ===");
    writer.write(document);
    System.out.println("\n=== Experiment Sequence Map ===");
    experimentSequence.forEach((key, value) -> System.out.println(key + " -> " + value));
  }

  @Test
  void testCreateENAExperimentSetElementWithMultipleDataFiles() throws Exception {
    // This test verifies that multiple data files from the same library
    // only create one experiment (deduplication)
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = isaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "test-456";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    final Map<String, String> experimentSequence =
        experimentXmlCreator.createENAExperimentSetElement(
            bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    // Verify that we have experiments
    Assertions.assertNotNull(experimentSequence);
    final Element experimentSet = webinElement.element("EXPERIMENT_SET");
    Assertions.assertNotNull(experimentSet);

    // Count unique libraries in the ISA JSON
    final long uniqueLibraries =
        studies.stream()
            .flatMap(study -> study.getAssays().stream())
            .flatMap(
                assay ->
                    assay.getMaterials() != null && assay.getMaterials().getOtherMaterials() != null
                        ? assay.getMaterials().getOtherMaterials().stream()
                        : java.util.stream.Stream.empty())
            .filter(
                material ->
                    WebinExperimentXmlCreator.OTHER_MATERIAL_LIBRARY_NAME_DETERMINES_EXPERIMENT
                        .equalsIgnoreCase(material.getType()))
            .map(OtherMaterial::getId)
            .distinct()
            .count();

    // The number of experiments should match the number of unique libraries
    @SuppressWarnings("unchecked")
    final List<Element> experiments = experimentSet.elements("EXPERIMENT");
    Assertions.assertEquals(
        uniqueLibraries,
        experiments.size(),
        "Number of experiments should match number of unique libraries");
  }
}
