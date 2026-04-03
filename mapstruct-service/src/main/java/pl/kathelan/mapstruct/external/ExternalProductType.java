package pl.kathelan.mapstruct.external;

/**
 * Enum z zewnętrznego systemu używający prefiksu EXT_.
 * Demonstracja @EnumMapping z nameTransformationStrategy = "strip_prefix".
 */
public enum ExternalProductType {
    EXT_ELECTRONICS,
    EXT_CLOTHING,
    EXT_FOOD,
    EXT_BOOKS,
    EXT_HOME_APPLIANCES,
    EXT_OTHER
}
