package pl.kathelan.mapstruct.mapper.qualifier;

import org.mapstruct.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * FEATURE: Custom @Qualifier annotation — typ-bezpieczny kwalifikator
 * dla metod formatujących ceny w widoku mobilnym (skrócony format).
 */
@Qualifier
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MobileFormat {
}
