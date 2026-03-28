package pl.kathelan.common.util;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;

public final class XmlDateTimeUtils {

    private XmlDateTimeUtils() {}

    public static LocalDateTime toLocalDateTime(XMLGregorianCalendar xgc) {
        if (xgc == null) return null;
        return xgc.toGregorianCalendar().toZonedDateTime().toLocalDateTime();
    }

    public static XMLGregorianCalendar toXmlGregorianCalendar(LocalDateTime dt) {
        try {
            GregorianCalendar gc = GregorianCalendar.from(dt.atZone(ZoneId.systemDefault()));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Failed to create XMLGregorianCalendar", e);
        }
    }
}