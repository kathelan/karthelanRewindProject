package pl.kathelan.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XmlDateTimeUtils — konwersja między LocalDateTime (Java 8+) a XMLGregorianCalendar (JAXB/SOAP).
 *
 * XMLGregorianCalendar to typ używany przez JAXB do reprezentacji dat w XML/SOAP.
 * Java nie konwertuje między nimi automatycznie, dlatego potrzebny jest ten helper.
 *
 * Używany np. przy wysyłaniu i odbieraniu dat w wiadomościach SOAP.
 */
@DisplayName("XmlDateTimeUtils")
class XmlDateTimeUtilsTest {

    @Nested
    @DisplayName("toLocalDateTime — XMLGregorianCalendar → LocalDateTime")
    class ToLocalDateTime {

        @Test
        @DisplayName("poprawnie konwertuje datę i czas ze wszystkimi polami")
        void convertsAllDateTimeFields() {
            LocalDateTime original = LocalDateTime.of(2025, 6, 15, 12, 30, 0);
            XMLGregorianCalendar xmlCal = XmlDateTimeUtils.toXmlGregorianCalendar(original);

            LocalDateTime result = XmlDateTimeUtils.toLocalDateTime(xmlCal);

            assertThat(result.getYear()).isEqualTo(2025);
            assertThat(result.getMonth()).isEqualTo(original.getMonth());
            assertThat(result.getDayOfMonth()).isEqualTo(15);
            assertThat(result.getHour()).isEqualTo(12);
            assertThat(result.getMinute()).isEqualTo(30);
        }

        @Test
        @DisplayName("zwraca null gdy wejście to null — null obsługuje @NotNull, nie ten walidator")
        void returnsNullForNull() {
            assertThat(XmlDateTimeUtils.toLocalDateTime(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toXmlGregorianCalendar — LocalDateTime → XMLGregorianCalendar")
    class ToXmlGregorianCalendar {

        @Test
        @DisplayName("poprawnie konwertuje rok, miesiąc i dzień")
        void convertsDatePart() throws DatatypeConfigurationException {
            LocalDateTime dt = LocalDateTime.of(2025, 6, 15, 12, 30, 0);

            XMLGregorianCalendar result = XmlDateTimeUtils.toXmlGregorianCalendar(dt);

            GregorianCalendar gc = result.toGregorianCalendar();
            assertThat(gc.get(java.util.Calendar.YEAR)).isEqualTo(2025);
            assertThat(gc.get(java.util.Calendar.MONTH)).isEqualTo(java.util.Calendar.JUNE);
            assertThat(gc.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Round-trip — konwersja w obie strony zachowuje wartość")
    class RoundTrip {

        @Test
        @DisplayName("LocalDateTime → XMLGregorianCalendar → LocalDateTime zwraca tę samą datę i czas")
        void preservesDateTimeAfterDoubleConversion() {
            LocalDateTime original = LocalDateTime.of(2025, 3, 28, 10, 0, 0);

            LocalDateTime result = XmlDateTimeUtils.toLocalDateTime(
                    XmlDateTimeUtils.toXmlGregorianCalendar(original)
            );

            assertThat(result.toLocalDate()).isEqualTo(original.toLocalDate());
            assertThat(result.toLocalTime().withNano(0)).isEqualTo(original.toLocalTime());
        }
    }
}
