import json
from typing import Union, List, Any, Tuple, Optional, Dict

from mars_lib.logging import print_and_log
from mars_lib.models.isa_json import (
    Investigation,
    Assay,
    Comment,
    IsaJson,
    MaterialAttribute,
    MaterialAttributeValue,
    Study,
    OntologyAnnotation,
    Data as DataFile,
)
from pydantic import ValidationError
from mars_lib.target_repo import TARGET_REPO_KEY, TargetRepository
import uuid
from mars_lib.models.repository_response import (
    RepositoryResponse,
    Filter,
    Accession,
    Path,
)


def reduce_isa_json_for_target_repo(
    input_isa_json: IsaJson, target_repo: str
) -> IsaJson:
    """
    Filters out assays that are not meant to be sent to the specified target repository.

    Args:
        input_isa_json (IsaJson): Input ISA JSON that contains the original information.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        IsaJson: Filtered ISA JSON.
    """
    filtered_isa_json = input_isa_json.model_copy(deep=True)
    new_studies = []
    studies = filtered_isa_json.investigation.studies
    for study in studies:
        if target_repo == TargetRepository.BIOSAMPLES.value:
            filtered_assays = []
        else:
            assays = study.assays
            filtered_assays = [
                assay
                for assay in assays
                if is_assay_for_target_repo(assay, target_repo)
            ]

        study.assays = filtered_assays
        new_studies.append(study)

    filtered_isa_json.investigation.studies = new_studies
    return filtered_isa_json


def detect_target_repo_comment(comments: List[Comment]) -> Comment:
    """Will detect the comment that contains the target repository.

    Args:
        comments (List[Comment]): List of comments.

    Returns:
        Comment: The comment where the name corresponds with the name of the provided target repo.
    """
    if len(comments) < 1:
        raise ValueError("No comments found! Not able to detect the target repository!")
    return next(comment for comment in comments if comment.name == TARGET_REPO_KEY)


def is_assay_for_target_repo(assay: Assay, target_repo: str) -> bool:
    """
    Defines whether the assays are meant for the target repository.

    Args:
        assay (Assay): Assay model.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        bool: Boolean defining whether the assay is destined for the provided target repo.
    """
    target_repo_comment = detect_target_repo_comment(assay.comments)
    if target_repo_comment.value == target_repo:
        return True
    else:
        return False


def load_isa_json(
    file_path: str, investigation_is_root: bool
) -> Union[IsaJson, ValidationError]:
    """
    Reads the file and validates it as a valid ISA JSON.

    Args:
        file_path (str): Path to ISA JSON as string.
        investigation_is_root (bool): Boolean indicating if the investigation is the root of the ISA JSON. Set this to True if the ISA-JSON does not contain a 'investigation' field.

    Returns:
        Union[IsaJson, ValidationError]: Depending on the validation, returns a filtered ISA JSON or a pydantic validation error.
    """
    with open(file_path, "r") as json_file:
        isa_json = json.load(json_file)

    if investigation_is_root:
        inv = Investigation.model_validate(isa_json)
        return IsaJson(investigation=inv)
    else:
        return IsaJson.model_validate(isa_json)


def get_filter_for_accession_key(accession: Accession, key: str) -> Optional[Filter]:
    """
    Returns the studies node from the accession.

    Args:
        accession (Accession): The accession to be searched.
        key (str): The key to be searched.

    Returns:
        Path: The studies node.
    """
    return next((p.where for p in accession.path if p.key == key), None)


def apply_filter(filter: Filter, nodes: Union[List[Study], List[Assay]]) -> Any:
    """
    Filters the studies based on the filter.

    Args:
        filter (Filter): The filter to be applied.
        studies (List[Study]): The studies to be filtered.

    Returns:
       Study: The filtered study.
    """
    filter_key = "id" if filter.key == "@id" else filter.key
    return next(
        (node for node in nodes if getattr(node, filter_key) == filter.value), None
    )


def accession_data_file_comment_present(node: DataFile) -> bool:
    accession_data_file_comments = [
        comment for comment in node.comments if comment.name == "accession"
    ]
    if len(accession_data_file_comments) > 1:
        raise AttributeError(
            "There should not be more than 1 data file comment with the name 'accession'."
        )
    elif len(accession_data_file_comments) > 0:
        return True
    else:
        return False


def accession_characteristic_category_present(node: Union[Study, Assay]) -> bool:
    """
    Checks if the node has an accession characteristic category.

    Args:
        node (Union[Study, Assay]): The study or assay to be checked.

    Returns:
        bool: Boolean indicating whether the node has an accession characteristic category.
    """
    accession_characteristics_categories = [
        char_cat
        for char_cat in node.characteristicCategories
        if char_cat.characteristicType
        and char_cat.characteristicType.annotationValue == "accession"
    ]

    if len(accession_characteristics_categories) > 1:
        raise AttributeError(
            "There should be not more than one accession characteristic category."
        )
    elif len(accession_characteristics_categories) > 0:
        return True
    else:
        return False


def accession_characteristic_present(
    node: Union[Study, Assay], material_type_path: Path
) -> bool:
    """
    Checks if the node has an accession characteristic.

    Args:
        node (Union[Study, Assay]): The study or assay to be checked.
        material_type_path (Path): The path to the material type.

    Returns:
        bool: Boolean indicating whether the node has an accession characteristic.
    """
    if material_type_path.where:
        material = apply_filter(
            material_type_path.where, getattr(node.materials, material_type_path.key)
        )
    else:
        raise ValueError(
            f"'where' atribute is missing in path {material_type_path.key}."
        )

    accession_characteristics = []
    for char in material.characteristics:
        if char.category and char.category.characteristicType:
            if char.category.characteristicType.annotationValue:
                if char.category.characteristicType.annotationValue == "accession":
                    accession_characteristics.append(char)
            else:
                if char.category.characteristicType == "accession":
                    accession_characteristics.append(char)

    if len(accession_characteristics) > 1:
        raise AttributeError(
            "There should be not more than one accession characteristic."
        )
    elif len(accession_characteristics) > 0:
        return True
    else:
        return False


def add_accession_to_material_node(
    node: Any, accession_number: str, material_type_path: Path
) -> None:
    """
    Adds the accession number to the node.

    Args:
        node (Any): The node to be updated.
        accession_number (str): The accession number to be added.
        material_type_path (Path): The path to the material type.
    """
    if type(node) not in [Study, Assay]:
        raise ValueError("Node must be either 'Study' or 'Assay'.")

    node_materials = getattr(node.materials, material_type_path.key)
    if material_type_path.where:
        updated_material = apply_filter(material_type_path.where, node_materials)
    else:
        raise ValueError(
            f"'where' attribute is missing in path {material_type_path.key}."
        )

    accession_characteristics_category = next(
        (
            char_cat
            for char_cat in node.characteristicCategories
            if char_cat.characteristicType
            and char_cat.characteristicType.annotationValue == "accession"
        ),
        None,
    )

    if not accession_characteristics_category:
        raise ValueError("Accession characteristic category is not present.")

    updated_material_accession_characteristic = next(
        (
            char
            for char in updated_material.characteristics
            if char.category
            and char.category.id == accession_characteristics_category.id
        ),
        None,
    )
    updated_material.characteristics.remove(updated_material_accession_characteristic)

    if not updated_material_accession_characteristic:
        raise ValueError("Accession characteristic is not present.")

    accession_ontology_annotation = OntologyAnnotation()
    accession_ontology_annotation.id = (
        f"#ontology_annotation/accession_{updated_material.id}"
    )
    accession_ontology_annotation.annotationValue = accession_number
    updated_material_accession_characteristic.value = accession_ontology_annotation

    updated_material.characteristics.append(updated_material_accession_characteristic)


def add_accession_to_data_file_node(node: DataFile, accession_number: str) -> None:
    accession_comment = next(
        (
            comment
            for comment in node.comments
            if comment.name and comment.name.lower() == "accession"
        ),
        None,
    )
    if not accession_comment:
        raise ValueError(
            f"Accession comment not found in data file'{node.id} - {node.name}'."
        )

    accession_comment.value = accession_number


def create_accession_characteristic_category(
    node: Union[Study, Assay],
) -> Tuple[str, MaterialAttribute]:
    """
    creates a new characteristic category for the accession number.

    Args:
        node (Union[Study, Assay]): node to be updated

    Returns:
        MaterialAttribute: The newly created characteristic category.
    """
    if type(node) not in [Study, Assay]:
        raise ValueError("Node must be either 'Study' or 'Assay'.")

    category = MaterialAttribute()
    accession_id = str(uuid.uuid4())
    category.id = f"#characteristic_category/accession_{accession_id}"
    category.characteristicType = OntologyAnnotation(annotationValue="accession")
    node.characteristicCategories.append(category)

    return accession_id, category


def create_accession_data_file_comment(node: DataFile) -> Comment:
    comment = Comment()
    accession_id = str(uuid.uuid4())
    comment.id = f"#comment/accession_{accession_id}"
    comment.name = "accession"
    node.comments.append(comment)
    return comment


def fetch_existing_accession_data_file_comment(node: DataFile) -> Comment:
    accession_comment = next(
        comment
        for comment in node.comments
        if comment.name and comment.name.lower() == "accession"
    )
    if not accession_comment:
        raise ValueError(f"Accession comment not found in {node.id}.")
    return accession_comment


def fetch_existing_characteristic_category(
    node: Union[Study, Assay],
) -> Tuple[str, MaterialAttribute]:
    """
    Fetches the existing characteristic category for the accession number.

    Args:
        node (Union[Study, Assay]): study or assay to search
    """
    accession_cat = next(
        char_cat
        for char_cat in node.characteristicCategories
        if char_cat.characteristicType
        and char_cat.characteristicType.annotationValue
        and isinstance(char_cat.characteristicType.annotationValue, str)
        and char_cat.characteristicType.annotationValue.lower() == "accession"
    )
    if not accession_cat:
        raise ValueError(f"Accession characteristic category not found in{node.id}.")

    accession_id = (
        accession_cat.id.split("_")[-1] if accession_cat.id else str(uuid.uuid4())
    )
    return (accession_id, accession_cat)


def create_accession_characteristic(
    node: Union[Study, Assay],
    material_type_path: Path,
    category: MaterialAttribute,
    accession_id: str,
) -> None:
    """
    Creates a new characteristic for the accession number.

    Args:
        node (Union[Study, Assay]): node to be updated
        material_type_path (Path): path to the material type,
        category (MaterialAttribute): characteristic category for the accession number.
        accession_id (str): UUID for the accession.
    """
    current_materials = getattr(node.materials, material_type_path.key)
    if not material_type_path.where:
        raise ValueError(
            f"'where' atribute is missing in path {material_type_path.key}."
        )

    updated_material = apply_filter(material_type_path.where, current_materials)

    new_material_attribute_value = MaterialAttributeValue()
    new_material_attribute_value.id = (
        f"#material_attribute_value/accession_{accession_id}"
    )
    new_material_attribute_value.category = category
    updated_material.characteristics.append(new_material_attribute_value)


def update_isa_json(isa_json: IsaJson, repo_response: RepositoryResponse) -> IsaJson:
    """
    Adds the accession to the ISA JSON.

    Args:
        isa_json (IsaJson): The ISA JSON to be updated.
        repo_response (RepositoryResponse): The response from the repository.

    Returns:
        IsaJson: The updated ISA JSON.
    """
    # TODO: Modify to include datafile related accessions as well. E.G: ENA's run accessions.
    investigation = isa_json.investigation
    target_repository = repo_response.target_repository
    for accession in repo_response.accessions:

        has_assay_in_path = len([p for p in accession.path if p.key == "assays"]) > 0
        has_materials_in_path = (
            len([p for p in accession.path if p.key == "materials"]) > 0
        )
        has_data_files_in_path = (
            len([p for p in accession.path if p.key == "dataFiles"]) > 0
        )
        target_level = "assay" if has_assay_in_path else "study"

        study_filter = get_filter_for_accession_key(accession, "studies")
        if not study_filter:
            raise ValueError(f"Study filter is not present in {accession.path}.")

        ################################################################################################################
        # Filter the study or assay based on the filter
        ################################################################################################################
        updated_node = apply_filter(study_filter, investigation.studies)
        if target_level == "assay":
            assay_filter = get_filter_for_accession_key(accession, "assays")
            if not assay_filter:
                raise ValueError(f"Assay filter is not present in {accession.path}.")

            updated_node = apply_filter(assay_filter, updated_node.assays)
        ################################################################################################################

        if has_materials_in_path:
            material_type_path = next(
                p
                for p in accession.path
                if p.key in ["sources", "samples", "otherMaterials"]
            )

            if not updated_node:
                raise ValueError(f"Node not found for {accession.value}.")
            if not accession_characteristic_category_present(updated_node):
                (accession_id, category) = create_accession_characteristic_category(
                    updated_node
                )
            else:
                (accession_id, category) = fetch_existing_characteristic_category(
                    updated_node
                )

            if not accession_characteristic_present(updated_node, material_type_path):
                create_accession_characteristic(
                    updated_node, material_type_path, category, accession_id
                )

            add_accession_to_material_node(
                updated_node, accession.value, material_type_path
            )
        elif has_data_files_in_path:
            data_file_filter = get_filter_for_accession_key(accession, "dataFiles")
            if not data_file_filter:
                raise ValueError(
                    f"Data file filter is not present in {accession.path}."
                )

            updated_node = apply_filter(data_file_filter, updated_node.dataFiles)
            if not accession_data_file_comment_present(updated_node):
                create_accession_data_file_comment(updated_node)

            add_accession_to_data_file_node(updated_node, accession.value)
        else:
            # Add study accession to study comments
            updated_study = apply_filter(study_filter, investigation.studies)

            study_accession_comment: Comment = Comment(
                name=f"{target_repository}_{target_level}_accession",
                value=accession.value,
            )
            updated_study.comments.append(study_accession_comment)

            # Add study accession to assay comments
            updated_assay = next(
                filter(
                    lambda assay: is_assay_for_target_repo(assay, target_repository),
                    updated_study.assays,
                ),
                None,
            )
            if updated_assay:
                assay_accession_comment: Comment = Comment(
                    name=f"{target_repository}_{target_level}_accession",
                    value=accession.value,
                )
                updated_assay.comments.append(assay_accession_comment)
    isa_json.investigation = investigation
    return isa_json


def map_data_files_to_repositories(
    files: List[str], isa_json: IsaJson
) -> Dict[str, List[str]]:
    # Note: This works well in
    df_map: Dict[str, List[str]] = {}
    assays: List[Assay] = [
        assay for study in isa_json.investigation.studies for assay in study.assays
    ]

    files_dicts = [{"full_name": f, "short_name": f.split("/")[-1]} for f in files]
    remaining_files = files_dicts.copy()
    for assay in assays:
        target_repo_comment: Comment = detect_target_repo_comment(assay.comments)
        # This is an effect of everything being optional in the Comment model.
        # Should we decide to make the value mandatory, this guard clause would not be necessary anymore.
        if target_repo_comment.value is None:
            raise ValueError(
                f"At least one assay in the ISA-JSON has no '{TARGET_REPO_KEY}' comment. Mapping not possible. Make sure all assays in the ISA-JSON have this comment!"
            )
        assay_data_files = [df.name for df in assay.dataFiles]

        # Check if the files in the ISA-JSON are present in the command
        # If not, raise an error
        for adf in assay_data_files:
            if adf not in [fd["short_name"] for fd in files_dicts]:
                raise ValueError(
                    f"""Assay for repository '{target_repo_comment.value}' has encountered a mismatch while mapping the data files to the ISA-JSON.
                Data File '{adf}' is missing in the data files passed in the command:
                {files}
                Please correct the mismatch!"""
                )
            else:
                remaining_files = [
                    fd for fd in remaining_files if fd["short_name"] != adf
                ]

        df_map[target_repo_comment.value] = [
            fd["full_name"]
            for fd in files_dicts
            if fd["short_name"] in assay_data_files
        ]

    [
        print_and_log(
            msg=f"File '{rf['short_name']}' could not be mapped to any data file in the ISA-JSON. For this reason, it will be skipped during submission!",
            level="warning",
        )
        for rf in remaining_files
    ]

    return df_map
