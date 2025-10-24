package org.example;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.services.resource.RealmResourceProvider;

public class MultibankEndpointProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public MultibankEndpointProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        // Создаем ресурс с правильной инициализацией контекста
        MultibankResource resource = new MultibankResource(session);
        
        // Пытаемся получить UriInfo из контекста сессии если доступно
        KeycloakUriInfo uriInfo = session.getContext().getUri();
        if (uriInfo != null) {
            System.out.println("MultibankEndpointProvider: URI context available: " + uriInfo.getBaseUri());
        } else {
            System.out.println("MultibankEndpointProvider: URI context is null");
        }
        
        return resource;
    }

    @Override
    public void close() {}
}