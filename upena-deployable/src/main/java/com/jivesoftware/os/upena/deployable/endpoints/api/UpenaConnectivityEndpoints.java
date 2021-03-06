/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.upena.deployable.endpoints.api;

import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.shared.ResponseHelper;
import com.jivesoftware.os.upena.service.DiscoveredRoutes;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 *
 * @author jonathan.colt
 */
@Singleton
@Path("/upena/routes")
public class UpenaConnectivityEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final DiscoveredRoutes discoveredRoutes;

    public UpenaConnectivityEndpoints(@Context DiscoveredRoutes discoveredRoutes) {
        this.discoveredRoutes = discoveredRoutes;
    }

    @GET
    @Consumes("application/json")
    @Path("/instances")
    public Response getInstancesRoutes() {
        try {
            return ResponseHelper.INSTANCE.jsonResponse(new DiscoveredRoutes.Routes(discoveredRoutes.routes()));
        } catch (Exception x) {
            LOG.error("Failed getting instance routes", x);
            return ResponseHelper.INSTANCE.errorResponse("Failed building all health view.", x);
        }
    }

    @GET
    @Consumes("application/json")
    @Path("/health/{sinceTimestampMillis}")
    public Response getRoutesHealth(@PathParam("sinceTimestampMillis") long sinceTimestampMillis) {
        try {
            return ResponseHelper.INSTANCE.jsonResponse(new DiscoveredRoutes.RouteHealths(discoveredRoutes.routesHealth(sinceTimestampMillis)));
        } catch (Exception x) {
            LOG.error("Failed getting routes health", x);
            return ResponseHelper.INSTANCE.errorResponse("Failed building all health view.", x);
        }
    }

}
