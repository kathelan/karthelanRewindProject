package pl.kathelan.mapstruct.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * Kontekst formatowania walutowego — demonstracja @Context w MapStruct.
 * Przekazywany do metod mappera jako @Context, niewidoczny dla samego mapowania.
 */
@Getter
@AllArgsConstructor
public class CurrencyContext {
    private final Locale locale;
    private final Currency currency;

    public static CurrencyContext polish() {
        return new CurrencyContext(new Locale("pl", "PL"), Currency.getInstance("PLN"));
    }

    public static CurrencyContext euro() {
        return new CurrencyContext(Locale.GERMANY, Currency.getInstance("EUR"));
    }

    public String formatAmount(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format(locale, "%,.2f %s", amount, currency.getSymbol(locale));
    }
}
