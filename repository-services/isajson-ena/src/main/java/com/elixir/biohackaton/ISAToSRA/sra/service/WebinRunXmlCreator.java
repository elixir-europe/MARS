/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.models.isa.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
public class WebinRunXmlCreator {
  public void createENARunSetElement(
      final Element webinElement,
      final List<Study> studies,
      final Map<String, String> experimentSequenceMap,
      final String randomSubmissionIdentifier) {
    final String lastExperimentKeyInRawFile =
        Collections.max(experimentSequenceMap.entrySet(), Map.Entry.comparingByKey()).getKey();
    final String lastExperimentIdGenerated =
        Collections.max(experimentSequenceMap.entrySet(), Map.Entry.comparingByKey()).getValue();

    final Element runSetElement = webinElement.addElement("RUN_SET");

    studies.forEach(
        study ->
            study
                .getAssays()
                .forEach(
                    assay -> {
                      final String assayId = assay.getId();
                      final Element runElement =
                          runSetElement
                              .addElement("RUN")
                              .addAttribute("alias", assayId + "-" + randomSubmissionIdentifier);

                      runElement.addElement("TITLE").addText(assayId);
                      runElement
                          .addElement("EXPERIMENT_REF")
                          .addAttribute("refname", lastExperimentIdGenerated);

                      final AtomicReference<Output> dataFileOutput = new AtomicReference<>();
                      assay
                          .getProcessSequence()
                          .forEach(
                              processSequence ->
                                  processSequence
                                      .getInputs()
                                      .forEach(
                                          input -> {
                                            if (input.getId().equals(lastExperimentKeyInRawFile)) {
                                              dataFileOutput.set(
                                                  processSequence.getOutputs().get(0));
                                            }
                                          }));

                      final Output output = dataFileOutput.get();

                      if (output != null) {
                        assay
                            .getDataFiles()
                            .forEach(
                                dataFile -> {
                                  final String outputId = output.getId();

                                  if (dataFile
                                      .getId()
                                      .contains(outputId.substring(outputId.indexOf("/")))) {
                                    final List<Comment> comments = dataFile.getComments();
                                    final String fileName;
                                    final AtomicReference<String> fileType =
                                        new AtomicReference<>();
                                    final AtomicReference<String> checksum =
                                        new AtomicReference<>();

                                    fileName = dataFile.getName();

                                    comments.forEach(
                                        comment -> {
                                          if (comment.getName().equals("file type")) {
                                            fileType.set(comment.getValue().toString());
                                          }

                                          if (comment.getName().equals("file checksum")) {
                                            checksum.set(comment.getValue().toString());
                                          }
                                        });

                                    if (fileName != null
                                        && fileType.get() != null
                                        && checksum.get() != null) {
                                      final Element dataBlockElement =
                                          runElement.addElement("DATA_BLOCK");
                                      final Element filesElement =
                                          dataBlockElement.addElement("FILES");
                                      filesElement
                                          .addElement("FILE")
                                          .addAttribute("filename", fileName)
                                          .addAttribute("filetype", fileType.get())
                                          .addAttribute("checksum_method", "MD5")
                                          .addAttribute("checksum", checksum.get());
                                    } else {
                                      throw new RuntimeException("Run file(s) not found");
                                    }
                                  }
                                });
                      }
                    }));
  }
}
