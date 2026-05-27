/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Comment;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Service for creating ENA STUDY XML elements from ISA-JSON assay comments.
 *
 * <p>Each assay is treated as the ENA study unit so the ENA study alias can be mapped back to the
 * assay path in the MARS receipt. Study descriptor values are read directly from assay comments
 * using ENA-native field names such as STUDY_TITLE, STUDY_DESCRIPTION,
 * STUDY_ABSTRACT, STUDY_TYPE, and new_study_type.
 */
@Service
@Slf4j
public class WebinStudyXmlCreator {
  private static final Set<String> RESERVED_ASSAY_COMMENT_NAMES =
      Set.of(
          "target_repository",
          "STUDY_TITLE",
          "STUDY_DESCRIPTION",
          "STUDY_ABSTRACT",
          "STUDY_TYPE",
          "existing_study_type",
          "new_study_type");

  public void createENAStudySetElement(
      final Element webinElement,
      final List<Study> studies,
      final String randomSubmissionIdentifier) {
    try {
      final Element studySetElement = webinElement.addElement("STUDY_SET");

      studies.forEach(
          study -> {
            if (study.getAssays() == null) {
              return;
            }

            study
                .getAssays()
                .forEach(
                    assay -> createStudyElement(
                        studySetElement, study, assay, randomSubmissionIdentifier));
          });
    } catch (final Exception e) {
      log.error("Failed to parse ISA JSON and create ENA study", e);
      throw new RuntimeException("Failed to create ENA STUDY elements", e);
    }
  }

  private void createStudyElement(
      final Element studySetElement,
      final Study study,
      final Assay assay,
      final String randomSubmissionIdentifier) {
    final Map<String, String> assayCommentMap = buildCommentMap(assay.getComments());
    final String assayId = requireAssayField(assay.getId(), "assay @id");

    final Element studyElement =
        studySetElement.addElement("STUDY").addAttribute("alias", assayId + "-" + randomSubmissionIdentifier);

    final Element studyDescriptorElement = studyElement.addElement("DESCRIPTOR");
    studyDescriptorElement
        .addElement("STUDY_TITLE")
        .addText(requireAssayComment(assayCommentMap, "STUDY_TITLE", assayId));

    addOptionalTextElement(
        studyDescriptorElement, "STUDY_DESCRIPTION", assayCommentMap.get("STUDY_DESCRIPTION"));
    addOptionalTextElement(
        studyDescriptorElement, "STUDY_ABSTRACT", assayCommentMap.get("STUDY_ABSTRACT"));

    final Element studyTypeElement = studyDescriptorElement.addElement("STUDY_TYPE");
    studyTypeElement.addAttribute(
        "existing_study_type",
        requireAssayComment(
            assayCommentMap,
            firstNonBlankCommentName(assayCommentMap, "STUDY_TYPE", "existing_study_type"),
            assayId));

    final String newStudyType = assayCommentMap.get("new_study_type");
    if (newStudyType != null && !newStudyType.isBlank()) {
      studyTypeElement.addAttribute("new_study_type", newStudyType);
    }

    final Element studyAttributesElement = studyElement.addElement("STUDY_ATTRIBUTES");
    addCommentsAsStudyAttributes(studyAttributesElement, study.getComments(), Set.of());
    addCommentsAsStudyAttributes(
        studyAttributesElement, assay.getComments(), RESERVED_ASSAY_COMMENT_NAMES);
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

  private void addCommentsAsStudyAttributes(
      final Element studyAttributesElement,
      final List<Comment> comments,
      final Set<String> namesToSkip) {
    if (comments == null) {
      return;
    }

    comments.forEach(
        comment -> {
          if (comment == null
              || comment.getName() == null
              || comment.getValue() == null
              || namesToSkip.contains(comment.getName())) {
            return;
          }

          final Element studyAttributeElement =
              studyAttributesElement.addElement("STUDY_ATTRIBUTE");
          studyAttributeElement.addElement("TAG").addText(comment.getName());
          studyAttributeElement.addElement("VALUE").addText(String.valueOf(comment.getValue()));
        });
  }

  private void addOptionalTextElement(
      final Element parentElement, final String elementName, final String value) {
    if (value != null && !value.isBlank()) {
      parentElement.addElement(elementName).addText(value);
    }
  }

  private String requireAssayComment(
      final Map<String, String> assayCommentMap,
      final String commentName,
      final String assayId) {
    final String value = assayCommentMap.get(commentName);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Assay " + assayId + " is missing required ENA study comment " + commentName + ".");
    }
    return value;
  }

  private String requireAssayField(final String value, final String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Cannot create ENA STUDY element because the " + fieldName + " is missing.");
    }
    return value;
  }

  private String firstNonBlankCommentName(
      final Map<String, String> assayCommentMap, final String... commentNames) {
    for (String commentName : commentNames) {
      final String value = assayCommentMap.get(commentName);
      if (value != null && !value.isBlank()) {
        return commentName;
      }
    }
    return commentNames[0];
  }
}
