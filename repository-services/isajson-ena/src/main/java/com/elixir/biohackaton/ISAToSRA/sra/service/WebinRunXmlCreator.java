/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import java.util.List;
import java.util.Map;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
public class WebinRunXmlCreator {
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
                      if (assay.getDataFiles() != null) {
                        assay
                            .getDataFiles()
                            .forEach(
                                dataFile -> {
                                  // Find the process that produced this data file
                                  final ProcessSequence sequencingProcess =
                                      findProcessByOutputId(
                                          assay.getProcessSequence(), dataFile.getId());

                                  if (sequencingProcess != null) {
                                    // Find the library (experiment) that was input to sequencing
                                    final OtherMaterial library =
                                        findLibraryFromProcessInput(
                                            sequencingProcess, assay.getMaterials());

                                    if (library != null
                                        && experimentSequenceMap.containsKey(library.getId())) {
                                      final String experimentId =
                                          experimentSequenceMap.get(library.getId());
                                      createRunElement(
                                          runSetElement,
                                          dataFile,
                                          assay,
                                          experimentId,
                                          randomSubmissionIdentifier);
                                    }
                                  }
                                });
                      }
                    }));
  }

  /**
   * Finds a process that has the given output ID. Handles both #data_file/334 and #data/334
   * formats.
   */
  private ProcessSequence findProcessByOutputId(
      final List<ProcessSequence> processSequence, final String outputId) {
    if (processSequence == null || outputId == null) {
      return null;
    }

    // Normalize the outputId (handle both #data_file/334 and #data/334)
    final String normalizedOutputId = normalizeDataFileId(outputId);

    for (final ProcessSequence process : processSequence) {
      if (process.getOutputs() != null) {
        for (final Output output : process.getOutputs()) {
          if (output.getId() != null) {
            final String normalizedProcessOutputId = normalizeDataFileId(output.getId());
            if (normalizedProcessOutputId.equals(normalizedOutputId)) {
              return process;
            }
          }
        }
      }
    }
    return null;
  }

  /** Normalizes data file IDs to handle both #data_file/334 and #data/334 formats. */
  private String normalizeDataFileId(final String id) {
    if (id == null) {
      return null;
    }
    // Convert #data_file/334 to #data/334 for comparison
    return id.replace("#data_file/", "#data/");
  }

  /** Finds the Library (OtherMaterial) that was used as input to a process. */
  private OtherMaterial findLibraryFromProcessInput(
      final ProcessSequence process, final Materials materials) {
    if (process.getInputs() == null || materials == null || materials.getOtherMaterials() == null) {
      return null;
    }

    for (final Input input : process.getInputs()) {
      if (input.getId() != null) {
        for (final OtherMaterial otherMaterial : materials.getOtherMaterials()) {
          if (otherMaterial.getId() != null && otherMaterial.getId().equals(input.getId())) {
            return otherMaterial;
          }
        }
      }
    }
    return null;
  }

  /** Creates an ENA RUN element from a DataFile. */
  private void createRunElement(
      final Element runSetElement,
      final DataFile dataFile,
      final Assay assay,
      final String experimentId,
      final String randomSubmissionIdentifier) {
    final Element runElement =
        runSetElement
            .addElement("RUN")
            .addAttribute("alias", dataFile.getId() + "-" + randomSubmissionIdentifier);

    runElement.addElement("TITLE").addText(dataFile.getName() != null ? dataFile.getName() : "");
    runElement.addElement("EXPERIMENT_REF").addAttribute("refname", experimentId);

    // Extract file metadata from comments
    final String fileName = dataFile.getName();
    String fileType = null;
    String checksum = null;

    if (dataFile.getComments() != null) {
      for (final Comment comment : dataFile.getComments()) {
        if ("file type".equals(comment.getName())) {
          fileType = comment.getValue() != null ? comment.getValue().toString() : null;
        }
        if ("file checksum".equals(comment.getName())) {
          checksum = comment.getValue() != null ? comment.getValue().toString() : null;
        }
      }
    }

    if (fileName != null && fileType != null && checksum != null) {
      final Element dataBlockElement = runElement.addElement("DATA_BLOCK");
      final Element filesElement = dataBlockElement.addElement("FILES");
      filesElement
          .addElement("FILE")
          .addAttribute("filename", fileName)
          .addAttribute("filetype", fileType)
          .addAttribute("checksum_method", "MD5")
          .addAttribute("checksum", checksum);
    } else {
      throw new RuntimeException(
          "Run file(s) not found or missing required metadata: fileName="
              + fileName
              + ", fileType="
              + fileType
              + ", checksum="
              + checksum);
    }
  }
}
