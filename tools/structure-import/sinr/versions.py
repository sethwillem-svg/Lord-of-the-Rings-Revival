"""Source DataVersion handling (spec "Versions")."""

TARGET = 3955          # 1.21.1
DIRECT = range(3953, 3956)
UPGRADE = range(1519, 3953)


class VersionError(ValueError):
    pass


def classify(data_version: int) -> str:
    """``direct`` (1.21-1.21.1), ``upgrade`` (1.13-1.20.x: needs the add-on's upgrade step), or raises."""
    if data_version in DIRECT:
        return "direct"
    if data_version in UPGRADE:
        return "upgrade"
    raise VersionError(f"DataVersion {data_version} is not supported (1519..3955)")


UPGRADE_HINT = ("run the upgrade step: ./gradlew runGameTestServer -PmeJar=<jar> "
                "-PstructureUpgrade=tools/structure-import/work, then run build again")
