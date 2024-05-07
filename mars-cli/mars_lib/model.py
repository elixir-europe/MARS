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
    name: Optional[str] = Field(default=None)
    value: Optional[str] = Field(default=None)


class OntologySourceReference(IsaBase):
    comments: Optional[List[Comment]] = Field(default=[])
    description: Optional[str] = Field(default=None)
    file: Optional[str] = Field(default=None)
    name: Optional[str] = Field(default=None)
    version: Optional[str] = Field(default=None)


# TODO: Question: Should these be case-sensitive?
class DataTypeEnum(str, Enum):
    RAW_DATA_FILE = "Raw Data File"
    DERIVED_DATA_FILE = "Derived Data File"
    IMAGE_FILE = "Image File"
    SPECTRAL_RAW_DATA_FILE = "Spectral Raw Data File"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/)
    FREE_INDUCTION_DECAY_FILE = "Free Induction Decay File"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/)


class Data(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    name: Optional[str] = Field(default=None)
    type: Optional[DataTypeEnum] = Field(default=None)

    @field_validator("type")
    def apply_enum(cls, v):
        if v not in [item.value for item in DataTypeEnum]:
            raise ValueError("Invalid material type")
        return v


class OntologyAnnotation(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    annotationValue: Union[Optional[str], Optional[float], Optional[int]] = Field(
        default=[]
    )
    comments: Optional[List[Comment]] = Field(default=[])
    termAccession: Optional[str] = Field(default=None)
    termSource: Optional[str] = Field(
        description="The abbreviated ontology name. It should correspond to one of the sources as specified in the ontologySourceReference section of the Investigation.",
        default=None,
    )


class MaterialAttributeValue(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[MaterialAttribute] = Field(default=None)
    value: Union[
        Optional[OntologyAnnotation], Optional[str], Optional[float], Optional[int]
    ] = Field(default=None)
    unit: Optional[OntologyAnnotation] = Field(default=None)
    comments: Optional[List[Comment]] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-attribute-value-schema-json)


class Factor(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    factorName: Optional[str] = Field(default=None)
    factorType: Optional[OntologyAnnotation] = Field(default=None)


class FactorValue(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    category: Optional[Factor] = Field(default=None)
    value: Union[
        Optional[str], Optional[float], Optional[int], Optional[OntologyAnnotation]
    ] = Field(default=[])
    unit: Optional[OntologyAnnotation] = Field(default=None)


class Source(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: Optional[List[MaterialAttributeValue]] = Field(default=[])
    name: Optional[str] = Field(default=None)
    comments: Optional[List[Comment]] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#source-schema-json)


class Sample(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    name: Optional[str] = Field(default=None)
    characteristics: Optional[List[MaterialAttributeValue]] = Field(default=[])
    factorValues: Optional[List[FactorValue]] = Field(default=[])
    derivesFrom: Optional[List[Source]] = Field(default=[])
    comments: Optional[List[Comment]] = Field(
        default=[]
    )  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#sample-schema-json)


class ProtocolParameter(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    parameterName: Optional[OntologyAnnotation] = Field(default=None)


class ProcessParameterValue(IsaBase):
    category: Optional[ProtocolParameter] = Field(default=None)
    value: Union[
        Optional[str], Optional[float], Optional[int], Optional[OntologyAnnotation]
    ] = Field(default=[])
    unit: Optional[OntologyAnnotation] = Field(default=None)


# Helper class for protocol -> components
class Component(IsaBase):
    componentName: Optional[str] = Field(default=None)
    componentType: Optional[OntologyAnnotation] = Field(default=None)


class Protocol(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    components: Optional[List[Component]] = Field(default=[])
    description: Optional[str] = Field(default=None)
    name: Optional[str] = Field(default=None)
    parameters: Optional[List[ProtocolParameter]] = Field(default=[])
    protocolType: Optional[OntologyAnnotation] = Field(default=None)
    uri: Optional[str] = Field(default=None)
    version: Optional[str] = Field(default=None)


# Enum for material -> type
# TODO: Question: Should these be case-sensitive?
class MaterialTypeEnum(str, Enum):
    EXTRACT_NAME = "Extract Name"
    LABELED_EXTRACT_NAME = "Labeled Extract Name"
    LIBRARY_NAME = "library name"  # TODO: QUESTION: This is not mentioned in the specs (https://isa-specs.readthedocs.io/en/latest/isajson.html#material-schema-json) but was found in DataHub ISA-JSON and ARC ISA-JSON.


class Material(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristics: List[MaterialAttributeValue] = Field(default=[])
    comments: Optional[List[Comment]] = Field(default=[])
    name: Optional[str] = Field(default=None)
    type: Optional[str] = Field(default=None)
    derivesFrom: Optional[List[Material]] = Field(default=[])

    @field_validator("type")
    def apply_enum(cls, v):
        if v not in [item.value for item in MaterialTypeEnum]:
            raise ValueError("Invalid material type")
        return v


class Process(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    date: Optional[str] = Field(default=None)
    executesProtocol: Optional[Protocol] = Field(default=None)
    inputs: Optional[Union[List[Source], List[Sample], List[Material], list[Data]]] = (
        Field(default=[])
    )
    name: Optional[str] = Field(default=None)
    nextProcess: Optional[Process] = Field(default=None)
    outputs: Optional[Union[List[Sample], List[Material], list[Data]]] = Field(
        default=[]
    )
    parameterValues: Optional[List[ProcessParameterValue]] = Field(default=[])
    performer: Optional[str] = Field(default=None)
    previousProcess: Optional[Process] = Field(default=None)


# Helper for assay -> materials
class AssayMaterialType(IsaBase):
    samples: Optional[List[Sample]] = Field(default=[])
    otherMaterials: Optional[List[Material]] = Field(default=[])


class Assay(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristicCategories: Optional[List[MaterialAttribute]] = Field(default=[])
    comments: Optional[List[Comment]] = Field(default=[])
    dataFiles: Optional[List[Data]] = Field(default=[])
    filename: Optional[str] = Field(default=None)
    materials: Optional[AssayMaterialType] = Field(default=None)
    measurementType: Optional[OntologyAnnotation] = Field(default=None)
    processSequence: Optional[List[Process]] = Field(default=[])
    technologyPlatform: Optional[str] = Field(default=None)
    technologyType: Optional[OntologyAnnotation] = Field(default=None)
    unitCategories: Optional[List[OntologyAnnotation]] = Field(default=[])

    @field_validator("comments")
    def detect_target_repo_comments(cls, v):
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
    address: Optional[str] = Field(default=None)
    affiliation: Optional[str] = Field(default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    email: Optional[str] = Field(default=None)
    fax: Optional[str] = Field(default=None)
    firstName: Optional[str] = Field(default=None)
    lastName: Optional[str] = Field(default=None)
    midInitials: Optional[str] = Field(default=None)
    phone: Optional[str] = Field(default=None)
    roles: Optional[List[OntologyAnnotation]] = Field(default=[])


class Publication(IsaBase):
    authorList: Optional[str] = Field(default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    doi: Optional[str] = Field(default=None)
    pubMedID: Optional[str] = Field(default=None)
    status: Optional[OntologyAnnotation] = Field(default=None)
    title: Optional[str] = Field(default=None)


class StudyMaterialType(IsaBase):
    sources: Optional[List[Source]] = Field(default=[])
    samples: Optional[List[Sample]] = Field(default=[])
    otherMaterials: Optional[List[Material]] = Field(default=[])


class MaterialAttribute(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    characteristicType: Optional[OntologyAnnotation] = Field(default=None)


class Study(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    assays: Optional[List[Assay]] = Field(default=[])
    characteristicCategories: Optional[List[MaterialAttribute]] = Field(default=[])
    comments: Optional[List[Comment]] = Field(default=[])
    description: Optional[str] = Field(default=None)
    factors: Optional[List[Factor]] = Field(default=[])
    filename: Optional[str] = Field(default=None)
    identifier: Optional[str] = Field(default=None)
    materials: Optional[StudyMaterialType]
    people: Optional[List[Person]] = Field(default=[])
    processSequence: Optional[List[Process]] = Field(default=[])
    protocols: Optional[List[Protocol]] = Field(default=[])
    publicReleaseDate: Optional[str] = Field(default=None)
    publications: Optional[List[Publication]] = Field(default=[])
    studyDesignDescriptors: Optional[List[OntologyAnnotation]] = Field(default=[])
    submissionDate: Optional[str] = Field(default=None)
    title: Optional[str] = Field(default=None)
    unitCategories: Optional[List[OntologyAnnotation]] = Field(default=[])


class Investigation(IsaBase):
    id: Optional[str] = Field(alias="@id", default=None)
    comments: Optional[List[Comment]] = Field(default=[])
    description: Optional[str] = Field(default=None)
    filename: Optional[str] = Field(default=None)
    identifier: Optional[str] = Field(default=None)
    ontologySourceReferences: Optional[List[OntologySourceReference]] = Field(
        default=[]
    )
    people: Optional[List[Person]] = Field(default=[])
    publicReleaseDate: Optional[str] = Field(default=None)
    publications: Optional[List[Publication]] = Field(default=[])
    studies: Optional[List[Study]] = Field(default=[])
    submissionDate: Optional[str] = Field(default=None)
    title: Optional[str] = Field(default=None)


class IsaJson(IsaBase):
    investigation: Investigation
