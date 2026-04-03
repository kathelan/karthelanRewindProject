package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.dto.CustomerDTO;
import pl.kathelan.mapstruct.mapper.CustomerMapper;
import pl.kathelan.mapstruct.model.Customer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class CustomerMapperTest {

    @Autowired
    private CustomerMapper mapper;

    private Customer buildCustomer() {
        return Customer.builder()
                .id(10L)
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan.kowalski@example.com")
                .phoneNumber("+48 500 000 000")
                .build();
    }

    // FEATURE 9: expression dla fullName
    @Test
    @DisplayName("FEATURE 9: expression — firstName + lastName → fullName")
    void toDto_shouldConcatenateFullName() {
        CustomerDTO dto = mapper.toDto(buildCustomer());

        assertThat(dto.getFullName()).isEqualTo("Jan Kowalski");
        assertThat(dto.getFirstName()).isEqualTo("Jan");
        assertThat(dto.getLastName()).isEqualTo("Kowalski");
        assertThat(dto.getEmail()).isEqualTo("jan.kowalski@example.com");
    }

    // FEATURE 10: @BeanMapping(ignoreByDefault = true) — tylko jawnie zadeklarowane pola
    @Test
    @DisplayName("FEATURE 10: @BeanMapping(ignoreByDefault=true) — tylko deklarowane pola w toModel")
    void toModel_shouldOnlyMapDeclaredFields() {
        CustomerDTO dto = CustomerDTO.builder()
                .id(10L)
                .firstName("Anna")
                .lastName("Nowak")
                .email("anna@example.com")
                .phoneNumber("+48 600 000 000")
                .fullName("Anna Nowak (to pole jest ignorowane w toModel)")
                .build();

        Customer customer = mapper.toModel(dto);

        assertThat(customer.getId()).isEqualTo(10L);
        assertThat(customer.getFirstName()).isEqualTo("Anna");
        assertThat(customer.getLastName()).isEqualTo("Nowak");
        assertThat(customer.getEmail()).isEqualTo("anna@example.com");
        // fullName nie ma odpowiednika w Customer — @BeanMapping(ignoreByDefault=true) pomija je bezpiecznie
    }

    @Test
    @DisplayName("fullName z jednym słowem (edge case)")
    void toDto_withSingleWordName_shouldUseAsLastName() {
        Customer customer = Customer.builder()
                .id(1L)
                .firstName("")
                .lastName("Cher")
                .email("cher@example.com")
                .phoneNumber("")
                .build();

        CustomerDTO dto = mapper.toDto(customer);

        assertThat(dto.getFullName()).isEqualTo(" Cher");
    }
}
