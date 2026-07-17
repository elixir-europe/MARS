/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinExperimentXmlCreator;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinStudyXmlCreator;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebinHoloFoodIsaJsonCompatibilityTest {
  private static final String HOLOFOOD_SAMPLE_ACCESSION = "SAMEA13901688";

  private IsaJson holoFoodIsaJson;
  private WebinStudyXmlCreator studyXmlCreator;
  private WebinExperimentXmlCreator experimentXmlCreator;

  @BeforeEach
  void setUp() throws Exception {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final String holoFoodIsaJsonFile =
        Files.readString(new File("../../test-data/HoloFoodinISA_v1.0.json").toPath());
    holoFoodIsaJson = objectMapper.readValue(holoFoodIsaJsonFile, IsaJson.class);
    studyXmlCreator = new WebinStudyXmlCreator();
    experimentXmlCreator = new WebinExperimentXmlCreator();
  }

  @Test
  void testHoloFoodIsaJsonCreatesENAStudyAndExperimentElements() {
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = document.addElement("WEBIN");
    final List<Study> studies = holoFoodIsaJson.getInvestigation().getStudies();
    final String randomSubmissionIdentifier = "holofood-ci";

    studyXmlCreator.createENAStudySetElement(webinElement, studies, randomSubmissionIdentifier);

    final Map<String, String> bioSampleAccessions = new HashMap<>();
    bioSampleAccessions.put("SAMPLE", HOLOFOOD_SAMPLE_ACCESSION);
    bioSampleAccessions.put("#sample/3066", HOLOFOOD_SAMPLE_ACCESSION);

    final Map<String, String> experimentSequence =
        experimentXmlCreator.createENAExperimentSetElement(
            bioSampleAccessions, webinElement, studies, randomSubmissionIdentifier);

    final Element study = webinElement.element("STUDY_SET").element("STUDY");
    Assertions.assertEquals("#assay/262_263-holofood-ci", study.attributeValue("alias"));
    Assertions.assertEquals(
        "Transcriptome Sequencing",
        study.element("DESCRIPTOR").element("STUDY_TYPE").attributeValue("existing_study_type"));

    Assertions.assertEquals(1, experimentSequence.size());
    Assertions.assertTrue(experimentSequence.containsKey("#other_material/3067"));

    final Element experiment = webinElement.element("EXPERIMENT_SET").element("EXPERIMENT");
    Assertions.assertEquals("CC08.13B1a_HostT", experiment.elementText("TITLE"));

    final Element design = experiment.element("DESIGN");
    Assertions.assertEquals(
        "rna library for sequencing", design.elementText("DESIGN_DESCRIPTION"));
    Assertions.assertEquals(
        HOLOFOOD_SAMPLE_ACCESSION,
        design.element("SAMPLE_DESCRIPTOR").attributeValue("accession"));

    final Element libraryDescriptor = design.element("LIBRARY_DESCRIPTOR");
    Assertions.assertEquals(
        "Illumina Ribo-Zero Plus rRNA Depletion Kit + NEB Next Ultra RNA Library Prep Kit",
        libraryDescriptor.elementText("LIBRARY_NAME"));
    Assertions.assertEquals("RNA-Seq", libraryDescriptor.elementText("LIBRARY_STRATEGY"));
    Assertions.assertEquals("TRANSCRIPTOMIC", libraryDescriptor.elementText("LIBRARY_SOURCE"));
    Assertions.assertEquals("cDNA", libraryDescriptor.elementText("LIBRARY_SELECTION"));
    Assertions.assertNotNull(libraryDescriptor.element("LIBRARY_LAYOUT").element("PAIRED"));

    Assertions.assertEquals(
        "Illumina NovaSeq 6000",
        experiment.element("PLATFORM").element("Illumina").elementText("INSTRUMENT_MODEL"));
  }
}
