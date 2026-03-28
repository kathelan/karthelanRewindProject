package pl.kathelan.common.util;

import org.junit.jupiter.api.Test;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

class XmlDateTimeUtilsTest {

    @Test
    void toLocalDateTime_convertsCorrectly() {
        LocalDateTime original = LocalDateTime.of(2025, 6, 15, 12, 30, 0);
        XMLGregorianCalendar xmlCal = XmlDateTimeUtils.toXmlGregorianCalendar(original);

        LocalDateTime result = XmlDateTimeUtils.toLocalDateTime(xmlCal);

        assertThat(result.getYear()).isEqualTo(original.getYear());
        assertThat(result.getMonth()).isEqualTo(original.getMonth());
        assertThat(result.getDayOfMonth()).isEqualTo(original.getDayOfMonth());
        assertThat(result.getHour()).isEqualTo(original.getHour());
        assertThat(result.getMinute()).isEqualTo(original.getMinute());
    }

    @Test
    void toLocalDateTime_returnsNullForNull() {
        assertThat(XmlDateTimeUtils.toLocalDateTime(null)).isNull();
    }

    @Test
    void toXmlGregorianCalendar_convertsCorrectly() throws DatatypeConfigurationException {
        LocalDateTime dt = LocalDateTime.of(2025, 6, 15, 12, 30, 0);

        XMLGregorianCalendar result = XmlDateTimeUtils.toXmlGregorianCalendar(dt);

        GregorianCalendar gc = result.toGregorianCalendar();
        assertThat(gc.get(java.util.Calendar.YEAR)).isEqualTo(2025);
        assertThat(gc.get(java.util.Calendar.MONTH)).isEqualTo(java.util.Calendar.JUNE);
        assertThat(gc.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(15);
    }

    @Test
    void roundTrip_preservesDateTime() {
        LocalDateTime original = LocalDateTime.of(2025, 3, 28, 10, 0, 0);

        LocalDateTime result = XmlDateTimeUtils.toLocalDateTime(
                XmlDateTimeUtils.toXmlGregorianCalendar(original)
        );

        assertThat(result.toLocalDate()).isEqualTo(original.toLocalDate());
        assertThat(result.toLocalTime().withNano(0)).isEqualTo(original.toLocalTime());
    }
}