import { Pipe, PipeTransform } from '@angular/core';
import { DirectoryUserDto } from '@core/generated';

export function userDisplayName(user: DirectoryUserDto): string {
  const name = [user.firstName, user.lastName]
    .map((part) => part?.trim())
    .filter((part) => !!part)
    .join(' ');
  return name || user.email || user.id;
}

export function userOptionLabel(user: DirectoryUserDto): string {
  const name = userDisplayName(user);
  return user.email ? `${name} (${user.email})` : name;
}

@Pipe({ name: 'userName' })
export class UserNamePipe implements PipeTransform {
  transform(user: DirectoryUserDto): string {
    return userDisplayName(user);
  }
}

@Pipe({ name: 'userOption' })
export class UserOptionPipe implements PipeTransform {
  transform(user: DirectoryUserDto): string {
    return userOptionLabel(user);
  }
}
