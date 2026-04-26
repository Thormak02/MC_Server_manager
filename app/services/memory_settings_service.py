RAM_MIN_MB = 512
RAM_MAX_MB = 64 * 1024
RAM_STEP_MB = 512


def validate_memory_bounds(
    memory_min_mb: int | None,
    memory_max_mb: int | None,
) -> tuple[int | None, int | None]:
    def _validate(value: int | None, label: str) -> int | None:
        if value is None:
            return None
        if value < RAM_MIN_MB or value > RAM_MAX_MB:
            raise ValueError(f"{label} muss zwischen {RAM_MIN_MB} MB und {RAM_MAX_MB} MB liegen.")
        if value % RAM_STEP_MB != 0:
            raise ValueError(f"{label} muss in {RAM_STEP_MB}-MB-Schritten angegeben werden.")
        return value

    validated_min = _validate(memory_min_mb, "RAM min")
    validated_max = _validate(memory_max_mb, "RAM max")
    if validated_min is not None and validated_max is not None and validated_min > validated_max:
        raise ValueError("RAM min darf RAM max nicht ueberschreiten.")
    return validated_min, validated_max
