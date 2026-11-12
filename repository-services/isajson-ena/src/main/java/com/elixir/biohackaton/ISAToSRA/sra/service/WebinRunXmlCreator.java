/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.IsaJsonGraphLookup;
import com.elixir.mars.repository.models.isa.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Creates ENA RUN XML from ISA sequencing processes.
 *
 * <p>Runs are grouped by the sequencing process that produced one or more data files and are linked
 * back to the ENA experiment generated for the input library.
 *
 * <p>Each raw data file supplies the attributes of its ENA {@code FILE} element through ISA
 * comments named after those attributes: {@code filetype} and {@code checksum} are required, {@code
 * checksum_method} defaults to MD5 (see {@link DataFileComments}).
 */
@Service
public class WebinRunXmlCreator {
  /**
   * Creates the ENA RUN_SET element for every submitted assay.
   *
   * <p>The traversal starts at ISA data files, resolves the sequencing process that produced them,
   * and emits one ENA RUN for each sequencing process instead of one run per data file.
   */
  public void createENARunSetElement(
      final Element webinElement,
      final List<Study> studies,
      final Map<String, String> experimentSequenceMap,
      final String randomSubmissionIdentifier) {
    final Element runSetElement = webinElement.addElement("RUN_SET");

    // Bottom-up approach: Start from DataFiles and work up
    studies.forEach(
        study ->
            study
                .getAssays()
                .forEach(
                    assay -> {
                      final Set<String> processedSequencingProcesses = new HashSet<>();
                      if (assay.getDataFiles() != null) {
                        assay
                            .getDataFiles()
                            .forEach(
                                dataFile -> {
                                  // Find the process that produced this data file
                                  final ProcessSequence sequencingProcess =
                                      IsaJsonGraphLookup.findProcessByOutputId(
                                          assay.getProcessSequence(), dataFile.getId());

                                  if (sequencingProcess != null) {
                                    if (!processedSequencingProcesses.add(
                                        sequencingProcess.getId())) {
                                      return;
                                    }

                                    // Find the library (experiment) that was input to sequencing
                                    final OtherMaterial library =
                                        IsaJsonGraphLookup.findOtherMaterialFromProcessInput(
                                            assay.getProcessSequence(),
                                            sequencingProcess,
                                            assay.getMaterials());

                                    if (library != null
                                        && experimentSequenceMap.containsKey(library.getId())) {
                                      final String experimentId =
                                          experimentSequenceMap.get(library.getId());
                                      final List<DataFile> runDataFiles =
                                          IsaJsonGraphLookup.findDataFilesFromProcessOutputs(
                                              assay.getProcessSequence(),
                                              sequencingProcess,
                                              assay.getDataFiles());
                                      createRunElement(
                                          runSetElement,
                                          sequencingProcess,
                                          runDataFiles,
                                          experimentId,
                                          randomSubmissionIdentifier);
                                    }
                                  }
                                });
                      }
                    }));
  }

  /** Creates one ENA RUN element from all data files produced by a sequencing process. */
  private void createRunElement(
      final Element runSetElement,
      final ProcessSequence sequencingProcess,
      final List<DataFile> dataFiles,
      final String experimentId,
      final String randomSubmissionIdentifier) {
    final Element runElement =
        runSetElement
            .addElement("RUN")
            .addAttribute("alias", sequencingProcess.getId() + "-" + randomSubmissionIdentifier);

    final String runTitle =
        !dataFiles.isEmpty() && dataFiles.get(0).getName() != null ? dataFiles.get(0).getName() : "";
    runElement.addElement("TITLE").addText(runTitle);
    runElement.addElement("EXPERIMENT_REF").addAttribute("refname", experimentId);

    if (dataFiles.isEmpty()) {
      throw new RuntimeException(
          "Run file(s) not found or missing required metadata for sequencing process "
              + sequencingProcess.getId());
    }

    final Element dataBlockElement = runElement.addElement("DATA_BLOCK");
    final Element filesElement = dataBlockElement.addElement("FILES");

    for (final DataFile dataFile : dataFiles) {
      final String fileName = dataFile.getName();
      final String fileType = DataFileComments.findValue(dataFile, DataFileComments.FILETYPE);
      final String checksumMethod = DataFileComments.resolveChecksumMethod(dataFile);
      final String checksum = DataFileComments.findValue(dataFile, DataFileComments.CHECKSUM);

      if (fileName == null || fileType == null || checksum == null) {
        throw new RuntimeException(
            "Run file(s) not found or missing required metadata: fileName="
                + fileName
                + ", "
                + DataFileComments.FILETYPE
                + "="
                + fileType
                + ", "
                + DataFileComments.CHECKSUM
                + "="
                + checksum
                + ". Each raw data file needs the comments '"
                + DataFileComments.FILETYPE
                + "' and '"
                + DataFileComments.CHECKSUM
                + "', optionally with '"
                + DataFileComments.CHECKSUM_METHOD
                + "'.");
      }

      filesElement
          .addElement("FILE")
          .addAttribute("filename", fileName)
          .addAttribute("filetype", fileType)
          .addAttribute("checksum_method", checksumMethod)
          .addAttribute("checksum", checksum);
    }
  }
}
