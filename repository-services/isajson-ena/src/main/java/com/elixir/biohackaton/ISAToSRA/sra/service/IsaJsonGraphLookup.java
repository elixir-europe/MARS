/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.DataFile;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Input;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Materials;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.OtherMaterial;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Output;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.ProcessSequence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared ISA-JSON graph helpers for ENA conversion.
 *
 * <p>Experiment XML, run XML, and receipt conversion all need to resolve the same process/data-file
 * lineage. Keeping the lookup rules here avoids subtle drift, especially around ISA's
 * #data_file/... versus #data/... ID forms.
 */
final class IsaJsonGraphLookup {
  private static final String DATA_FILE_ID_PREFIX = "#data_file/";
  private static final String DATA_ID_PREFIX = "#data/";

  private IsaJsonGraphLookup() {}

  /** Finds the process that produced an output material or data file. */
  static ProcessSequence findProcessByOutputId(
      final List<ProcessSequence> processSequence, final String outputId) {
    if (processSequence == null || outputId == null) {
      return null;
    }

    final String normalizedOutputId = normalizeDataFileId(outputId);

    for (final ProcessSequence process : processSequence) {
      if (process == null || process.getOutputs() == null) {
        continue;
      }

      for (final Output output : process.getOutputs()) {
        if (output == null || output.getId() == null) {
          continue;
        }

        if (normalizeDataFileId(output.getId()).equals(normalizedOutputId)) {
          return process;
        }
      }
    }

    return null;
  }

  static OtherMaterial findOtherMaterialFromProcessInput(
      final ProcessSequence process, final Materials materials) {
    if (process == null || process.getInputs() == null) {
      return null;
    }

    final Map<String, OtherMaterial> otherMaterialsById = buildOtherMaterialsById(materials);
    for (final Input input : process.getInputs()) {
      if (input == null || input.getId() == null) {
        continue;
      }

      final OtherMaterial otherMaterial = otherMaterialsById.get(input.getId());
      if (otherMaterial != null) {
        return otherMaterial;
      }
    }

    return null;
  }

  static List<DataFile> findDataFilesFromProcessOutputs(
      final ProcessSequence process, final List<DataFile> assayDataFiles) {
    final List<DataFile> dataFiles = new ArrayList<>();
    if (process == null || process.getOutputs() == null || assayDataFiles == null) {
      return dataFiles;
    }

    for (final Output output : process.getOutputs()) {
      if (output == null || output.getId() == null) {
        continue;
      }

      final String normalizedOutputId = normalizeDataFileId(output.getId());
      for (final DataFile dataFile : assayDataFiles) {
        if (dataFile == null || dataFile.getId() == null) {
          continue;
        }

        if (normalizeDataFileId(dataFile.getId()).equals(normalizedOutputId)) {
          dataFiles.add(dataFile);
        }
      }
    }

    return dataFiles;
  }

  static Map<String, OtherMaterial> buildOtherMaterialsById(final Materials materials) {
    final Map<String, OtherMaterial> otherMaterialsById = new HashMap<>();
    if (materials == null || materials.getOtherMaterials() == null) {
      return otherMaterialsById;
    }

    for (final OtherMaterial otherMaterial : materials.getOtherMaterials()) {
      if (otherMaterial != null && otherMaterial.getId() != null) {
        otherMaterialsById.put(otherMaterial.getId(), otherMaterial);
      }
    }

    return otherMaterialsById;
  }

  /** Treat ISA #data_file/... and process #data/... references as equivalent. */
  static String normalizeDataFileId(final String id) {
    return id == null ? null : id.replace(DATA_FILE_ID_PREFIX, DATA_ID_PREFIX);
  }
}
