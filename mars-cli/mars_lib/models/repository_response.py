import json
from typing import List, Optional
from pydantic import BaseModel, field_validator, Field, ConfigDict
import pydantic
import pydantic.alias_generators
from mars_lib.target_repo import TargetRepository


class Filter(BaseModel):
    key: str
    value: str


class Path(BaseModel):
    key: str
    where: Optional[Filter] = None


class Accession(BaseModel):
    path: List[Path] = []
    value: str

    @field_validator("path")
    def validate_path(cls, path):
        keys = [p.key for p in path]
        if len(keys) != len(set(keys)):
            raise ValueError("Duplicate keys found in path list")
        return path


class Error(BaseModel):
    type: str
    message: str
    path: List[Path] = []


class Info(BaseModel):
    name: str
    message: str


class RepositoryResponse(BaseModel):
    # This is a Pydantic configuration that will convert the field names to camel case and be accessible as alias.
    model_config = ConfigDict(alias_generator=pydantic.alias_generators.to_camel)

    target_repository: str
    accessions: List[Accession] = []
    errors: List[Error] = []
    info: List[Info] = []

    @field_validator("target_repository")
    def validate_target_repository(cls, v):
        if v not in [item.value for item in TargetRepository]:
            raise ValueError(f"Invalid 'target repository' value: '{v}'")
        return v

    @classmethod
    def from_json_file(cls, json_file):
        with open(json_file, "r") as file:
            data = json.load(file)

        target_repository = data.get("targetRepository")
        accessions = [Accession(**acc) for acc in data.get("accessions", [])]
        errors = [Error(**err) for err in data.get("errors", [])]
        info = [Info(**inf) for inf in data.get("info", [])]

        return cls(
            targetRepository=target_repository,
            accessions=accessions,
            errors=errors,
            info=info,
        )

    @classmethod
    def from_json(cls, json_string: str):
        data = json.loads(json_string)

        target_repository = data.get("targetRepository")
        accessions = [Accession(**acc) for acc in data.get("accessions", [])]
        errors = [Error(**err) for err in data.get("errors", [])]
        info = [Info(**inf) for inf in data.get("info", [])]

        return cls(
            targetRepository=target_repository,
            accessions=accessions,
            errors=errors,
            info=info,
        )
