package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.dto.CustomerDTO;
import pl.kathelan.mapstruct.model.Customer;

@Mapper(config = MapStructConfig.class, uses = {AddressMapper.class})
public interface CustomerMapper {

    // FEATURE 9: expression do obliczenia fullName z dwóch pól źródłowych
    @Mapping(target = "fullName",
             expression = "java(customer.getFirstName() + \" \" + customer.getLastName())")
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    CustomerDTO toDto(Customer customer);

    // FEATURE 10: @BeanMapping(ignoreByDefault = true) — mapuj TYLKO to co zadeklarujesz
    // fullName nie ma odpowiednika w Customer — ignorujemy
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id",          source = "id")
    @Mapping(target = "firstName",   source = "firstName")
    @Mapping(target = "lastName",    source = "lastName")
    @Mapping(target = "email",       source = "email")
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    Customer toModel(CustomerDTO dto);
}
