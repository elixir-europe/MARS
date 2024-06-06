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


class OntologySourceReference(IsaBase):
    comments: List[Comment] = []
    description: Optional[str] = None
    file: Optional[str] = None
    name: Optional[str] = None
    version: Optional[str] = None


# TODO: Question: Should these be case-sensitive?
class DataTypeEnum(str, Enum):
    RAW_DATA_FILE = "Raw Data File"
    DERIVED_DATA_FILE = "Derived Data File"
    IMAGE_FILE = "Image File"
    SPECTRAL_RAW_DATA_FILE = "Spectral Raw Data File"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/)
    FREE_INDUCTION_DECAY_FILE = "Free Induction Decay File"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/)


class Data(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: List[Comment] = []
    name: Optional[str] = None
    type: Optional[DataTypeEnum] = None

    @field_validator("type")
    def apply_enum(cls, v: str) -> str:
        if v not in [item.value for item in DataTypeEnum]:
            raise ValueError("Invalid material type")
        return v


class OntologyAnnotation(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    annotationValue: Union[Optional[str], Optional[float], Optional[int]] = Field(
        default=None
    )
    comments: List[Comment] = []
    termAccession: Optional[str] = None
    termSource: Optional[str] = Field(
        description="The abbreviated ontology name. It should correspond to one of the sources as specified in the ontologySourceReference section of the Investigation.",
        default=None,
    )


class MaterialAttributeValue(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[MaterialAttribute] = None
    value: Union[str, float, int, OntologyAnnotation, None] = None
    unit: Optional[OntologyAnnotation] = None
    comments: List[Comment] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-attribute-value-schema-json)


class Factor(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: List[Comment] = []
    factorName: Optional[str] = None
    factorType: Optional[OntologyAnnotation] = None


class FactorValue(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[Factor] = None
    value: Union[str, float, int, OntologyAnnotation, None] = None
    unit: Optional[OntologyAnnotation] = None


class Source(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: List[MaterialAttributeValue] = []
    name: Optional[str] = None
    comments: List[Comment] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#source-schema-json)


class Sample(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    name: Optional[str] = None
    characteristics: List[MaterialAttributeValue] = []
    factorValues: List[FactorValue] = []
    derivesFrom: List[Source] = []
    comments: List[Comment] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#sample-schema-json)


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


class Protocol(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: List[Comment] = []
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


class Material(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: List[MaterialAttributeValue] = []
    comments: List[Comment] = []
    name: Optional[str] = None
    type: Optional[str] = None
    derivesFrom: List[Material] = []

    @field_validator("type")
    def apply_enum(cls, v: str) -> str:
        if v not in [item.value for item in MaterialTypeEnum]:
            raise ValueError("Invalid material type")
        return v


class Process(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: List[Comment] = []
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


class Assay(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristicCategories: List[MaterialAttribute] = []
    comments: List[Comment] = []
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


class Person(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    address: Optional[str] = None
    affiliation: Optional[str] = None
    comments: List[Comment] = []
    email: Optional[str] = None
    fax: Optional[str] = None
    firstName: Optional[str] = None
    lastName: Optional[str] = None
    midInitials: Optional[str] = None
    phone: Optional[str] = None
    roles: List[OntologyAnnotation] = []


class Publication(IsaBase):
    authorList: Optional[str] = None
    comments: List[Comment] = []
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


class Study(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    assays: List[Assay] = []
    characteristicCategories: List[MaterialAttribute] = []
    comments: List[Comment] = []
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


class Investigation(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: List[Comment] = []
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
