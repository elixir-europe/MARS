/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.mars.repository.models.isa.*;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinExperimentXmlCreator;
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
class WebinExperimentXmlCreatorTest {

  private WebinExperimentXmlCreator experimentXmlCreator;
  private ObjectMapper objectMapper;
  private IsaJson isaJson;
  private IsaJson multiIsaJson;

  @BeforeEach
  void setUp() throws Exception {
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
    Assertions.assertEquals(
        "Arabidopsis leaf amplicon sequencing experiment",
        titleElement.getText(),
        "TITLE should come from the upstream other-material Title characteristic");

    // Verify STUDY_REF element
    final Element studyRef = firstExperiment.element("STUDY_REF");
    Assertions.assertNotNull(studyRef, "EXPERIMENT should have a STUDY_REF element");
    Assertions.assertNotNull(
        studyRef.attribute("refname"), "STUDY_REF should have a refname attribute");
    Assertions.assertEquals(
        "#assay/18_20_21-test-123",
        studyRef.attributeValue("refname"),
        "STUDY_REF should point at the assay-backed ENA study alias");

    // Verify DESIGN element
    final Element design = firstExperiment.element("DESIGN");
    Assertions.assertNotNull(design, "EXPERIMENT should have a DESIGN element");

    // Verify DESIGN_DESCRIPTION
    final Element designDescription = design.element("DESIGN_DESCRIPTION");
    Assertions.assertNotNull(designDescription, "DESIGN should have a DESIGN_DESCRIPTION element");
    Assertions.assertEquals(
        "Amplicon sequencing of Arabidopsis thaliana leaf DNA.",
        designDescription.getText(),
        "DESIGN_DESCRIPTION should come from the ENA-native assay parameter");

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
    Assertions.assertEquals(
        "arabidopsis_leaf_amplicon_library",
        libraryDescriptor.elementText("LIBRARY_NAME"),
        "LIBRARY_NAME should come from the ENA-native assay parameter");
    Assertions.assertEquals(
        "AMPLICON",
        libraryDescriptor.elementText("LIBRARY_STRATEGY"),
        "LIBRARY_STRATEGY should come from the ENA-native assay parameter");
    Assertions.assertEquals(
        "GENOMIC",
        libraryDescriptor.elementText("LIBRARY_SOURCE"),
        "LIBRARY_SOURCE should come from the ENA-native assay parameter");
    Assertions.assertEquals(
        "PCR",
        libraryDescriptor.elementText("LIBRARY_SELECTION"),
        "LIBRARY_SELECTION should come from the ENA-native assay parameter");

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
    Assertions.assertEquals(
        "MinION",
        instrumentModel.getText(),
        "INSTRUMENT_MODEL should come from the ENA-native sequencing parameter");

    // Print XML for debugging (optional)
    final OutputFormat format = OutputFormat.createPrettyPrint();
    final XMLWriter writer = new XMLWriter(System.out, format);
    System.out.println("\n=== Generated ENA Experiment XML ===");
    writer.write(document);
    System.out.println("\n=== Experiment Sequence Map ===");
    experimentSequence.forEach((key, value) -> System.out.println(key + " -> " + value));
  }

  @Test
  void testExperimentTitleCanComeFromProcessMetadata() throws Exception {
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = isaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "test-process-title";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    final Parameter titleParameter = new Parameter();
    titleParameter.setId("#parameter/experiment_title_test");
    final ParameterName titleParameterName = new ParameterName();
    titleParameterName.setAnnotationValue("Experiment Title");
    titleParameter.setParameterName(titleParameterName);

    final Protocol libraryProtocol =
        studies.get(0).getProtocols().stream()
            .filter(protocol -> "#protocol/20_20".equals(protocol.getId()))
            .findFirst()
            .orElseThrow();
    libraryProtocol.getParameters().add(titleParameter);

    final ParameterValue titleParameterValue = new ParameterValue();
    final Category titleCategory = new Category();
    titleCategory.setId("#parameter/experiment_title_test");
    titleParameterValue.setCategory(titleCategory);
    final Value titleValue = new Value();
    titleValue.setAnnotationValue("Process metadata experiment title");
    titleParameterValue.setValue(titleValue);

    final ProcessSequence libraryConstructionProcess =
        studies.get(0).getAssays().get(0).getProcessSequence().stream()
            .filter(process -> "#process/library_construction/333".equals(process.getId()))
            .findFirst()
            .orElseThrow();
    libraryConstructionProcess.getParameterValues().add(titleParameterValue);

    experimentXmlCreator.createENAExperimentSetElement(
        bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    final Element experimentSet = webinElement.element("EXPERIMENT_SET");
    @SuppressWarnings("unchecked")
    final List<Element> experiments = experimentSet.elements("EXPERIMENT");

    Assertions.assertEquals(
        "Process metadata experiment title",
        experiments.get(0).elementText("TITLE"),
        "TITLE should come from process metadata when present");
  }

  @Test
  void testExperimentMetadataCanComeFromOtherMaterialCharacteristics() throws Exception {
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = isaJson.getInvestigation().getStudies();
    final Assay assay = studies.get(0).getAssays().get(0);
    final String randomSubmissionIdentifier = "test-material-metadata";
    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SOURCE", "SAMEA130793922");

    final ProcessSequence libraryConstructionProcess =
        assay.getProcessSequence().stream()
            .filter(process -> "#process/library_construction/333".equals(process.getId()))
            .findFirst()
            .orElseThrow();
    libraryConstructionProcess
        .getParameterValues()
        .removeIf(
            parameterValue ->
                parameterValue.getCategory() != null
                    && "#parameter/353".equals(parameterValue.getCategory().getId()));

    final CharacteristicCategory strategyCategory = new CharacteristicCategory();
    strategyCategory.setId("#characteristic_category/library_strategy_test");
    final CharacteristicType strategyType = new CharacteristicType();
    strategyType.setAnnotationValue("LIBRARY_STRATEGY");
    strategyCategory.setCharacteristicType(strategyType);
    assay.getCharacteristicCategories().add(strategyCategory);

    final OtherMaterial extract =
        assay.getMaterials().getOtherMaterials().stream()
            .filter(material -> "#other_material/332".equals(material.getId()))
            .findFirst()
            .orElseThrow();
    final Characteristic strategyCharacteristic = new Characteristic();
    final Category strategyCharacteristicCategory = new Category();
    strategyCharacteristicCategory.setId("#characteristic_category/library_strategy_test");
    strategyCharacteristic.setCategory(strategyCharacteristicCategory);
    final Value strategyValue = new Value();
    strategyValue.setAnnotationValue("WGS");
    strategyCharacteristic.setValue(strategyValue);
    extract.getCharacteristics().add(strategyCharacteristic);

    experimentXmlCreator.createENAExperimentSetElement(
        bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    final Element experimentSet = webinElement.element("EXPERIMENT_SET");
    @SuppressWarnings("unchecked")
    final List<Element> experiments = experimentSet.elements("EXPERIMENT");
    final Element libraryDescriptor =
        experiments.get(0).element("DESIGN").element("LIBRARY_DESCRIPTOR");

    Assertions.assertEquals(
        "WGS",
        libraryDescriptor.elementText("LIBRARY_STRATEGY"),
        "LIBRARY_STRATEGY should come from other-material characteristics when process metadata is absent");
  }

  @Test
  void testCreateENAExperimentSetElementWithMultipleDataFiles() throws Exception {
    // Paired data files from one library should deduplicate to one experiment, while a second
    // library in the same assay should still create its own experiment.
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = multiIsaJson.getInvestigation().getStudies();
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

    // The number of experiments should match the number of unique libraries
    @SuppressWarnings("unchecked")
    final List<Element> experiments = experimentSet.elements("EXPERIMENT");
    Assertions.assertEquals(
        2, experimentSequence.size(), "Expected two unique libraries in the multi ISA fixture");
    Assertions.assertEquals(
        experimentSequence.size(),
        experiments.size(),
        "Number of experiments should match the resolved experiment sequence");
    Assertions.assertTrue(
        experimentSequence.containsKey("#other_material/333"),
        "Expected the paired-end data files to resolve to library 1");
    Assertions.assertTrue(
        experimentSequence.containsKey("#other_material/338"),
        "Expected the single-end data file to resolve to library 2");
  }
}
