from __future__ import annotations

from enum import Enum
from typing import List, Optional, Union

from pydantic import BaseModel, Field, field_validator, ConfigDict
from mars_lib.target_repo import TargetRepository, TARGET_REPO_KEY


class IsaBase(BaseModel):
    # model_config = ConfigDict(extra="allow")
    model_config = ConfigDict(extra="forbid")


class Comment(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    name: Optional[str] = None
    value: Optional[str] = None


class CommentedIsaBase(IsaBase):
    comments: List[Comment] = []


class OntologySourceReference(CommentedIsaBase):
    description: Optional[str] = None
    file: Optional[str] = None
    name: Optional[str] = None
    version: Optional[str] = None


# TODO: Question: Should these be case-sensitive?
class DataTypeEnum(str, Enum):
    RAW_DATA_FILE = "Raw Data File"
    DERIVED_DATA_FILE = "Derived Data File"
    IMAGE_FILE = "Image File"
    # The following names are not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#data-schema-json).
    # However, spectral data file names are mentioned in the ISA-Tab specs (https://isa-specs.readthedocs.io/en/latest/isatab.html).
    # TODO: Review and support all possible data file names mentioned in the the ISA-Tab specs (Section 2.3.8).
    # Metabolights support the following data file types:
    RAW_SPECTRAL_DATA_FILE = "Raw Spectral Data File"
    DERIVED_SPECTRAL_DATA_FILE = "Derived Spectral Data File"
    FREE_INDUCTION_DECAY_DATA_FILE = "Free Induction Decay Data File"
    ACQUSITION_PARAMETER_DATA_FILE = "Acquisition Parameter Data File"
    METABOLITE_ASSIGNMENT_FILE = "Metabolite Assignment File"  # Used in MetaboLights to report metabolite assignments


DATA_TYPE_VALUES = {item.value for item in DataTypeEnum}


class Data(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    name: Optional[str] = None
    type: Optional[DataTypeEnum] = None

    @field_validator("type")
    def apply_enum(cls, v: str) -> str:
        if v not in DATA_TYPE_VALUES:
            raise ValueError("Invalid material type")
        return v


class OntologyAnnotation(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    annotationValue: Union[Optional[str], Optional[float], Optional[int]] = Field(
        default=None
    )
    termAccession: Optional[str] = None
    termSource: Optional[str] = Field(
        description="The abbreviated ontology name. It should correspond to one of the sources as specified in the ontologySourceReference section of the Investigation.",
        default=None,
    )


# TODO: QUESTION: comments field is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-attribute-value-schema-json)
class MaterialAttributeValue(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[MaterialAttribute] = None
    value: Union[str, float, int, OntologyAnnotation, None] = None
    unit: Optional[OntologyAnnotation] = None


class Factor(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    factorName: Optional[str] = None
    factorType: Optional[OntologyAnnotation] = None


class FactorValue(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[Factor] = None
    value: Union[str, float, int, OntologyAnnotation, None] = None
    unit: Optional[OntologyAnnotation] = None


# TODO: QUESTION: comments field is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-attribute-value-schema-json)
class Source(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: List[MaterialAttributeValue] = []
    name: Optional[str] = None


# TODO: QUESTION: comments field is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-attribute-value-schema-json)
class Sample(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    name: Optional[str] = None
    characteristics: List[MaterialAttributeValue] = []
    factorValues: List[FactorValue] = []
    derivesFrom: List[Source] = []


class ProtocolParameter(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    parameterName: Optional[OntologyAnnotation] = None


class ProcessParameterValue(IsaBase):
    category: Optional[ProtocolParameter] = None
    value: Union[str, float, int, OntologyAnnotation, None] = None
    unit: Optional[OntologyAnnotation] = None


# Helper class for protocol -> components
class Component(IsaBase):
    componentName: Optional[str] = None
    componentType: Optional[OntologyAnnotation] = None


class Protocol(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    components: List[Component] = []
    description: Optional[str] = None
    name: Optional[str] = None
    parameters: List[ProtocolParameter] = []
    protocolType: Optional[OntologyAnnotation] = None
    uri: Optional[str] = None
    version: Optional[str] = None


# Enum for material -> type
# TODO: Question: Should these be case-sensitive?
class MaterialTypeEnum(str, Enum):
    EXTRACT_NAME = "Extract Name"
    LABELED_EXTRACT_NAME = "Labeled Extract Name"
    LIBRARY_NAME = "library name"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-schema-json) but was found in DataHub ISA-JSON and ARC ISA-JSON.


class Material(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: List[MaterialAttributeValue] = []
    name: Optional[str] = None
    type: Optional[str] = None
    derivesFrom: List[Material] = []

    @field_validator("type")
    def apply_enum(cls, v: str) -> str:
        if v not in [item.value for item in MaterialTypeEnum]:
            raise ValueError("Invalid material type")
        return v


class Process(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    date: Optional[str] = None
    executesProtocol: Optional[Protocol] = None
    inputs: Optional[Union[List[Source], List[Sample], List[Material], list[Data]]] = []
    name: Optional[str] = None
    nextProcess: Optional[Process] = None
    outputs: Optional[Union[List[Sample], List[Material], list[Data]]] = Field(
        default=[]
    )
    parameterValues: List[ProcessParameterValue] = []
    performer: Optional[str] = None
    previousProcess: Optional[Process] = None


# Helper for assay -> materials
class AssayMaterialType(IsaBase):
    samples: List[Sample] = []
    otherMaterials: List[Material] = []


class Assay(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristicCategories: List[MaterialAttribute] = []
    dataFiles: List[Data] = []
    filename: Optional[str] = None
    materials: Optional[AssayMaterialType] = None
    measurementType: Optional[OntologyAnnotation] = None
    processSequence: List[Process] = []
    technologyPlatform: Optional[str] = None
    technologyType: Optional[OntologyAnnotation] = None
    unitCategories: List[OntologyAnnotation] = []

    @field_validator("comments")
    def detect_target_repo_comments(cls, v: List[Comment]) -> Optional[List[Comment]]:
        target_repo_comments = [
            comment for comment in v if comment.name == TARGET_REPO_KEY
        ]
        if len(target_repo_comments) == 0:
            raise ValueError("'target repository' comment is missing")
        elif len(target_repo_comments) > 1:
            raise ValueError("Multiple 'target repository' comments found")
        else:
            if target_repo_comments[0].value in [
                item.value for item in TargetRepository
            ]:
                return v
            else:
                raise ValueError(
                    f"Invalid 'target repository' value: '{target_repo_comments[0].value}'"
                )


class Person(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    address: Optional[str] = None
    affiliation: Optional[str] = None
    email: Optional[str] = None
    fax: Optional[str] = None
    firstName: Optional[str] = None
    lastName: Optional[str] = None
    midInitials: Optional[str] = None
    phone: Optional[str] = None
    roles: List[OntologyAnnotation] = []


class Publication(CommentedIsaBase):
    authorList: Optional[str] = None
    doi: Optional[str] = None
    pubMedID: Optional[str] = None
    status: Optional[OntologyAnnotation] = None
    title: Optional[str] = None


class StudyMaterialType(IsaBase):
    sources: List[Source] = []
    samples: List[Sample] = []
    otherMaterials: List[Material] = []


class MaterialAttribute(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristicType: Optional[OntologyAnnotation] = None


class Study(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    assays: List[Assay] = []
    characteristicCategories: List[MaterialAttribute] = []
    description: Optional[str] = None
    factors: List[Factor] = []
    filename: Optional[str] = None
    identifier: Optional[str] = None
    materials: Optional[StudyMaterialType]
    people: List[Person] = []
    processSequence: List[Process] = []
    protocols: List[Protocol] = []
    publicReleaseDate: Optional[str] = None
    publications: List[Publication] = []
    studyDesignDescriptors: List[OntologyAnnotation] = []
    submissionDate: Optional[str] = None
    title: Optional[str] = None
    unitCategories: List[OntologyAnnotation] = []


class Investigation(CommentedIsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    description: Optional[str] = None
    filename: Optional[str] = None
    identifier: Optional[str] = None
    ontologySourceReferences: List[OntologySourceReference] = Field(default=[])
    people: List[Person] = []
    publicReleaseDate: Optional[str] = None
    publications: List[Publication] = []
    studies: List[Study] = []
    submissionDate: Optional[str] = None
    title: Optional[str] = None


class IsaJson(IsaBase):
    investigation: Investigation
