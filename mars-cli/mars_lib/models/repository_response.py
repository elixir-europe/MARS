from ast import List
from email import message
from os import name
from pydantic import BaseModel, field_validator, Field
from target_repo import TargetRepository


class Filter(BaseModel):
    key: str
    value: str


class Path(BaseModel):
    key: str
    where: List[Filter] = Field(default_value=[])


class Accession(BaseModel):
    path: List[Path] = Field(default_value=[])
    value: str


class Error(BaseModel):
    type: str
    message: str
    path: List[Path] = Field(default_value=[])


class Info(BaseModel):
    name: str
    message: str


class Reponse(BaseModel):
    target_repository: str = Field(alias="targetRepository")
    accessions: List[Accession] = Field(default_value=[])
    errors: List[Error] = Field(default_value=[])
    info: List[Info] = Field(default_value=[])

    @field_validator("target_repository")
    def validate_target_repository(cls, v):
        if v not in [item.value for item in TargetRepository]:
            raise ValueError(f"Invalid 'target repository' value: '{v}'")
        return v
