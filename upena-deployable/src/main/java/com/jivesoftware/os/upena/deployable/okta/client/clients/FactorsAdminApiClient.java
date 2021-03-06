package com.jivesoftware.os.upena.deployable.okta.client.clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jivesoftware.os.upena.deployable.okta.client.framework.ApiClientConfiguration;
import com.jivesoftware.os.upena.deployable.okta.client.framework.FilterBuilder;
import com.jivesoftware.os.upena.deployable.okta.client.framework.JsonApiClient;
import com.jivesoftware.os.upena.deployable.okta.client.models.factors.OrgAuthFactor;

import java.io.IOException;
import java.util.List;

public class FactorsAdminApiClient extends JsonApiClient {

    public FactorsAdminApiClient(ApiClientConfiguration config) {
        super(config);
    }

    ////////////////////////////////////////////
    // COMMON METHODS
    ////////////////////////////////////////////

    public List<OrgAuthFactor> getOrgFactors() throws IOException {
        return get(getEncodedPath("/factors"), new TypeReference<List<OrgAuthFactor>>() { });
    }

    public List<OrgAuthFactor> getOrgFactors(FilterBuilder filterBuilder) throws IOException {
        return get(getEncodedPath("/factors?" + FILTER + "=%s", filterBuilder.toString()), new TypeReference<List<OrgAuthFactor>>() { });
    }

    // OrgAuthFactor LIFECYCLE

    public OrgAuthFactor activateOrgFactor(String orgAuthFactorId) throws IOException {
        return activateOrgFactor(orgAuthFactorId, null);
    }

    public OrgAuthFactor activateOrgFactor(String orgAuthFactorId, OrgAuthFactor orgAuthFactor) throws IOException {
        return post(getEncodedPath("/factors/%s/lifecycle/activate", orgAuthFactorId), orgAuthFactor, new TypeReference<OrgAuthFactor>() {
        });
    }

    public OrgAuthFactor deActivateOrgFactor(String orgAuthFactorId) throws IOException {
        return post(getEncodedPath("/factors/%s/lifecycle/deactivate", orgAuthFactorId), null, new TypeReference<OrgAuthFactor>() { });
    }

    @Override
    protected String getFullPath(String relativePath) {
        return String.format("/api/v%d/org%s", this.apiVersion, relativePath);
    }

}
