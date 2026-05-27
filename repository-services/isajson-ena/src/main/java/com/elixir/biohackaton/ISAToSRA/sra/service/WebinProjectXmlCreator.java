/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Comment;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Investigation;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Service for creating ENA PROJECT XML elements from ISA-JSON assay comments.
 *
 * <p>Each assay is treated as the ENA study/project unit so the resulting ENA project alias stays
 * aligned with the assay-based receipt path. Project title and description are read directly from
 * assay comments using the ENA study field names.
 */
@Service
public class WebinProjectXmlCreator {
  public void createENAProjectSetElement(
      final Element webinElement,
      final Investigation investigation,
      final String randomSubmissionIdentifier) {
    final Element projectSetElement = webinElement.addElement("PROJECT_SET");

    if (investigation == null || investigation.getStudies() == null) {
      return;
    }

    investigation.getStudies().forEach(
        study -> {
          if (study.getAssays() == null) {
            return;
          }

          study
              .getAssays()
              .forEach(
                  assay -> createProjectElement(
                      projectSetElement, study, assay, randomSubmissionIdentifier));
        });
  }

  private void createProjectElement(
      final Element projectSetElement,
      final Study study,
      final Assay assay,
      final String randomSubmissionIdentifier) {
    final Map<String, String> assayCommentMap = buildCommentMap(assay.getComments());
    final String assayId = requireField(assay.getId(), "assay @id");
    final String projectTitle = requireField(assayCommentMap.get("STUDY_TITLE"), "STUDY_TITLE");
    final String projectDescription =
        firstNonBlank(
            assayCommentMap.get("STUDY_DESCRIPTION"),
            assayCommentMap.get("STUDY_ABSTRACT"),
            study != null ? study.getDescription() : null);

    final Element projectElement = projectSetElement.addElement("PROJECT");
    projectElement.addAttribute("alias", assayId + "-" + randomSubmissionIdentifier);
    projectElement.addElement("TITLE").addText(projectTitle);
    projectElement.addElement("DESCRIPTION").addText(requireField(projectDescription, "STUDY_DESCRIPTION or STUDY_ABSTRACT"));

    final Element submissionProjectElement = projectElement.addElement("SUBMISSION_PROJECT");
    submissionProjectElement.addElement("SEQUENCING_PROJECT");
  }

  private Map<String, String> buildCommentMap(final List<Comment> comments) {
    final Map<String, String> commentMap = new HashMap<>();

    if (comments == null) {
      return commentMap;
    }

    for (Comment comment : comments) {
      if (comment == null || comment.getName() == null || comment.getValue() == null) {
        continue;
      }

      commentMap.put(comment.getName(), String.valueOf(comment.getValue()));
    }

    return commentMap;
  }

  private String firstNonBlank(final String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String requireField(final String value, final String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Cannot create ENA PROJECT element because " + fieldName + " is missing.");
    }
    return value;
  }
}
