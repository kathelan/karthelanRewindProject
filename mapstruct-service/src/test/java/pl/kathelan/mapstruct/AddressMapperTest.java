package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.dto.AddressDTO;
import pl.kathelan.mapstruct.external.ExternalAddress;
import pl.kathelan.mapstruct.mapper.AddressMapper;
import pl.kathelan.mapstruct.model.Address;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class AddressMapperTest {

    @Autowired
    private AddressMapper mapper;

    // FEATURE 1: Automatyczne mapowanie identycznych pól
    @Test
    @DisplayName("FEATURE 1: Address → AddressDTO — automatyczne mapowanie identycznych pól")
    void toDto_shouldMapAllFields() {
        Address address = Address.builder()
                .street("Marszałkowska")
                .buildingNumber("10A")
                .city("Warszawa")
                .zipCode("00-001")
                .country("Polska")
                .build();

        AddressDTO dto = mapper.toDto(address);

        assertThat(dto.getStreet()).isEqualTo("Marszałkowska");
        assertThat(dto.getBuildingNumber()).isEqualTo("10A");
        assertThat(dto.getCity()).isEqualTo("Warszawa");
        assertThat(dto.getZipCode()).isEqualTo("00-001");
        assertThat(dto.getCountry()).isEqualTo("Polska");
    }

    // FEATURE 2: @InheritInverseConfiguration
    @Test
    @DisplayName("FEATURE 2: AddressDTO → Address — @InheritInverseConfiguration")
    void toModel_shouldReverseMapFromDto() {
        AddressDTO dto = AddressDTO.builder()
                .street("Nowy Świat")
                .buildingNumber("5")
                .city("Kraków")
                .zipCode("30-001")
                .country("Polska")
                .build();

        Address address = mapper.toModel(dto);

        assertThat(address.getStreet()).isEqualTo("Nowy Świat");
        assertThat(address.getCity()).isEqualTo("Kraków");
    }

    // FEATURE 3: @Mapping(source, target) — różne nazwy pól
    @Test
    @DisplayName("FEATURE 3: ExternalAddress → Address — @Mapping z różnymi nazwami pól")
    void fromExternal_shouldMapRenamedFields() {
        ExternalAddress ext = new ExternalAddress("Główna", "15B", "Gdańsk", "80-001", "PL");

        Address address = mapper.fromExternal(ext);

        assertThat(address.getStreet()).isEqualTo("Główna");
        assertThat(address.getBuildingNumber()).isEqualTo("15B");
        assertThat(address.getCity()).isEqualTo("Gdańsk");
        assertThat(address.getZipCode()).isEqualTo("80-001");
        assertThat(address.getCountry()).isEqualTo("PL");
    }

    // FEATURE 4: @InheritInverseConfiguration(name) na fromExternal
    @Test
    @DisplayName("FEATURE 4: Address → ExternalAddress — @InheritInverseConfiguration(name = 'fromExternal')")
    void toExternal_shouldReverseMapToExternalAddress() {
        Address address = Address.builder()
                .street("Lipowa")
                .buildingNumber("3")
                .city("Wrocław")
                .zipCode("50-001")
                .country("PL")
                .build();

        ExternalAddress ext = mapper.toExternal(address);

        assertThat(ext.getStreetLine()).isEqualTo("Lipowa");
        assertThat(ext.getBuildingNo()).isEqualTo("3");
        assertThat(ext.getCityName()).isEqualTo("Wrocław");
        assertThat(ext.getPostalCode()).isEqualTo("50-001");
        assertThat(ext.getCountryCode()).isEqualTo("PL");
    }

    @Test
    @DisplayName("Null source → null target (nullValueMappingStrategy default)")
    void toDto_withNullSource_shouldReturnNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
