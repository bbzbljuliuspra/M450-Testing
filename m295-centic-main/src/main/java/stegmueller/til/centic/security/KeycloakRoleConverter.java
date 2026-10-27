package stegmueller.til.centic.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // resource_access.centic.roles lesen
        Map<String, Object> resourceAccess = (Map<String, Object>) jwt.getClaims().get("resource_access");

        if (resourceAccess == null || !resourceAccess.containsKey("centic")) {
            return Collections.emptyList();
        }

        Map<String, Object> centic = (Map<String, Object>) resourceAccess.get("centic");
        List<String> roles = (List<String>) centic.get("roles");

        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}