package org.example.entity;

import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.List;

public class OtpJpaEntityProviderFactory implements JpaEntityProviderFactory {

    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        return new JpaEntityProvider() {
            @Override
            public void close() {}

            @Override
            public List<Class<?>> getEntities() {
                return List.of(OtpEntity.class);
            }

            @Override
            public String getChangelogLocation() {
                return null;
            }

            @Override
            public String getFactoryId() {
                return "otp-jpa";
            }
        };
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "otp-jpa";
    }
}

