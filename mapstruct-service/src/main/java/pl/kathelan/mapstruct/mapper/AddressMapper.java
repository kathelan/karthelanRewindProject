package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.dto.AddressDTO;
import pl.kathelan.mapstruct.external.ExternalAddress;
import pl.kathelan.mapstruct.model.Address;

@Mapper(config = MapStructConfig.class)
public interface AddressMapper {

    // FEATURE 1: Automatyczne mapowanie identycznych pól
    AddressDTO toDto(Address address);

    // FEATURE 2: @InheritInverseConfiguration — odwrócenie bez powtarzania
    @InheritInverseConfiguration
    Address toModel(AddressDTO dto);

    // FEATURE 3: @Mapping z różnymi nazwami pól (ExternalAddress ma inne nazewnictwo)
    @Mapping(source = "streetLine",   target = "street")
    @Mapping(source = "buildingNo",   target = "buildingNumber")
    @Mapping(source = "cityName",     target = "city")
    @Mapping(source = "postalCode",   target = "zipCode")
    @Mapping(source = "countryCode",  target = "country")
    Address fromExternal(ExternalAddress externalAddress);

    // FEATURE 4: @InheritInverseConfiguration na metodzie z niestandardową nazwą
    @InheritInverseConfiguration(name = "fromExternal")
    ExternalAddress toExternal(Address address);
}
