/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.Comment;
import com.elixir.mars.repository.models.isa.Study;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Service for creating ENA STUDY XML elements from ISA-JSON.
 *
 * <p>Each assay is treated as the ENA study unit so the ENA study alias can be mapped back to the
 * assay path in the MARS receipt. Core study descriptor values come from the ISA Study itself,
 * while ENA-specific study fields such as STUDY_ABSTRACT, STUDY_TYPE, and new_study_type are read
 * from assay comments.
 */
@Service
@Slf4j
public class WebinStudyXmlCreator {
  private static final Set<String> RESERVED_ASSAY_COMMENT_NAMES =
      Set.of(
          "target_repository",
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
        studySetElement
            .addElement("STUDY")
            .addAttribute("alias", assayId + "-" + randomSubmissionIdentifier);

    final Element studyDescriptorElement = studyElement.addElement("DESCRIPTOR");
    studyDescriptorElement
        .addElement("STUDY_TITLE")
        .addText(requireStudyField(study != null ? study.getTitle() : null, "study title", assayId));

    addOptionalTextElement(
        studyDescriptorElement, "STUDY_DESCRIPTION", study != null ? study.getDescription() : null);
    addOptionalTextElement(
        studyDescriptorElement,
        "STUDY_ABSTRACT",
        firstNonBlank(assayCommentMap.get("STUDY_ABSTRACT"), study != null ? study.getDescription() : null));

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
    final Map<String, String> commentMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

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
              || namesToSkip.stream()
                  .anyMatch(name -> name.equalsIgnoreCase(comment.getName()))) {
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

  private String requireStudyField(
      final String value, final String fieldName, final String assayId) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Assay " + assayId + " cannot create an ENA STUDY because the " + fieldName + " is missing.");
    }
    return value;
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

  private String firstNonBlank(final String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
