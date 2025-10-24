package org.example;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;
public class MultibankEndpointProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public MultibankEndpointProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new MultibankResource(session);
    }

    @Override
    public void close() {}
}