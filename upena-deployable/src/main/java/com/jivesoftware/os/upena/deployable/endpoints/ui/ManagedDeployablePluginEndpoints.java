package com.jivesoftware.os.upena.deployable.endpoints.ui;

import com.jivesoftware.os.upena.deployable.ShiroRequestHelper;
import com.jivesoftware.os.upena.deployable.region.ManagedDeployablePluginRegion;
import com.jivesoftware.os.upena.deployable.soy.SoyService;
import com.jivesoftware.os.upena.service.SessionStore;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.eclipse.jetty.http.HttpParser.LOG;

/**
 *
 */
@Singleton
@Path("/ui/deployable")
public class ManagedDeployablePluginEndpoints {

    private final ShiroRequestHelper shiroRequestHelper;
    private final SoyService soyService;
    private final ManagedDeployablePluginRegion pluginRegion;
    private final SessionStore sessionStore;

    public ManagedDeployablePluginEndpoints(@Context ShiroRequestHelper shiroRequestHelper,
        @Context SoyService soyService,
        @Context ManagedDeployablePluginRegion pluginRegion,
        @Context SessionStore sessionStore) {

        this.shiroRequestHelper = shiroRequestHelper;
        this.soyService = soyService;
        this.pluginRegion = pluginRegion;
        this.sessionStore = sessionStore;
    }

    @Path("/probe/{instanceKey}")
    @GET()
    @Produces(MediaType.TEXT_HTML)
    public Response javaDeployableProbe(@PathParam("instanceKey") @DefaultValue("unspecified") String instanceKey,
        @Context HttpServletRequest httpRequest) {
        return shiroRequestHelper.call("/ui/deployable/probe", () -> {
            String rendered = soyService.renderNoChromePlugin(httpRequest.getRemoteUser(), pluginRegion,
                new ManagedDeployablePluginRegion.ManagedDeployablePluginRegionInput(instanceKey, ""));
            return Response.ok(rendered).build();
        });
    }

    @Path("/probe/{instanceKey}")
    @POST
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response action(@PathParam("instanceKey") @DefaultValue("unspecified") String instanceKey,
        @Context HttpServletRequest httpRequest,
        @FormParam("action") @DefaultValue("") String action) {
        return shiroRequestHelper.call("/ui/deployable/probe/action", () -> {
            String rendered = soyService.renderNoChromePlugin(httpRequest.getRemoteUser(), pluginRegion,
                new ManagedDeployablePluginRegion.ManagedDeployablePluginRegionInput(instanceKey, action));
            return Response.ok(rendered).build();
        });
    }

    @Path("/ui/{instanceKey}")
    @GET()
    @Produces(MediaType.TEXT_HTML)
    public Response redirectToUI(@PathParam("instanceKey") @DefaultValue("unspecified") String instanceKey,
        @QueryParam("portName") @DefaultValue("unspecified") String portName,
        @QueryParam("path") @DefaultValue("unspecified") String uiPath,
        @Context HttpServletRequest httpRequest) {

        return shiroRequestHelper.call("/ui/deployable/ui", () -> {
            URI uri = pluginRegion.redirectToUI(instanceKey, portName, uiPath);
            if (uri == null) {
                return Response.ok("Failed to redirect.").build();
            }
            return Response.temporaryRedirect(uri).build();
        });
    }

    @Path("/accessToken/{instanceKey}")
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response uiAccessToken(@PathParam("instanceKey") @DefaultValue("unspecified") String instanceKey) throws Exception {
        try {
            return Response.ok(sessionStore.generateAccessToken(instanceKey).getBytes(StandardCharsets.UTF_8)).build();
        } catch (Exception x) {
            LOG.warn("UI access token failed", x);
            return Response.serverError().build();
        }
    }

}
