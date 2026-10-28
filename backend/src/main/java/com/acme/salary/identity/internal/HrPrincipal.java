package com.acme.salary.identity.internal;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

record HrPrincipal(UUID id, UserDetails delegate) implements UserDetails {
  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return delegate.getAuthorities();
  }

  @Override
  public String getPassword() {
    return delegate.getPassword();
  }

  @Override
  public String getUsername() {
    return delegate.getUsername();
  }

  @Override
  public boolean isEnabled() {
    return delegate.isEnabled();
  }

  @Override
  public boolean isAccountNonExpired() {
    return delegate.isAccountNonExpired();
  }

  @Override
  public boolean isAccountNonLocked() {
    return delegate.isAccountNonLocked();
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return delegate.isCredentialsNonExpired();
  }
}
