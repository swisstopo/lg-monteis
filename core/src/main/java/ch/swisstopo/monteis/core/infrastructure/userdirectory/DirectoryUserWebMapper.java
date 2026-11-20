package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DirectoryUserWebMapper {

  DirectoryUserDto toDto(DirectoryUser user);

  List<DirectoryUserDto> toDtos(List<DirectoryUser> users);
}
