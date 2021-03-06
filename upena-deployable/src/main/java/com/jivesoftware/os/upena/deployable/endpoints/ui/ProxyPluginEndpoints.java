package com.jivesoftware.os.upena.deployable.endpoints.ui;

import com.jivesoftware.os.upena.deployable.ShiroRequestHelper;
import com.jivesoftware.os.upena.deployable.UpenaProxy;
import com.jivesoftware.os.upena.deployable.region.ProxyPluginRegion;
import com.jivesoftware.os.upena.deployable.region.ProxyPluginRegion.ProxyInput;
import com.jivesoftware.os.upena.deployable.soy.SoyService;
import java.net.URI;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Encoder;

/**
 *
 */
@Singleton
@Path("/ui/proxy")
public class ProxyPluginEndpoints {

    private final ShiroRequestHelper shiroRequestHelper;

    private final SoyService soyService;
    private final ProxyPluginRegion pluginRegion;

    public ProxyPluginEndpoints(@Context ShiroRequestHelper shiroRequestHelper,
        @Context SoyService soyService,
        @Context ProxyPluginRegion pluginRegion) {

        this.shiroRequestHelper = shiroRequestHelper;
        this.soyService = soyService;
        this.pluginRegion = pluginRegion;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response proxy(@Context HttpServletRequest httpRequest) {
        return shiroRequestHelper.call("proxy", (csrfToken) -> {
            String rendered = soyService.renderPlugin(httpRequest.getRemoteUser(), csrfToken, pluginRegion, new ProxyInput(-1, "", -1, ""));
            return Response.ok(rendered);
        });
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response action(@Context HttpServletRequest httpRequest,
        @FormParam("csrfToken") String csrfToken,
        @FormParam("localPort") @DefaultValue("-1") int localPort,
        @FormParam("remoteHost") @DefaultValue("") String remoteHost,
        @FormParam("remotePort") @DefaultValue("-1") int remotePort,
        @FormParam("urlHost") @DefaultValue("") String urlHost,
        @FormParam("urlPort") @DefaultValue("-1") int urlPort,
        @FormParam("url") @DefaultValue("") String url,
        @FormParam("action") @DefaultValue("") String action) {
        return shiroRequestHelper.csrfCall(csrfToken, "proxy/actions", (csrfToken1) -> {
            String rendered = soyService.renderPlugin(httpRequest.getRemoteUser(), csrfToken1, pluginRegion,
                new ProxyInput(localPort, remoteHost, remotePort, action));
            return Response.ok(rendered);
        });
    }

    @GET
    @Path("/redirect")
    @Produces(MediaType.TEXT_HTML)
    public Response redirect(@Context HttpServletRequest httpRequest,
        @QueryParam("host") @DefaultValue("") String host,
        @QueryParam("port") @DefaultValue("-1") int port,
        @QueryParam("path") @DefaultValue("") String path) {
        return shiroRequestHelper.call("proxy/redirect", (csrfToken) -> {
            UpenaProxy redirect = pluginRegion.redirect(host, port);
            Encoder encoder = ESAPI.encoder();
            URI location = URI.create("http://" + httpRequest.getLocalAddr()
                + ":" + redirect.getLocalPort()
                + (path.startsWith("/") ? path : "/" + path));
            encoder.canonicalize(location.getQuery());
            return Response.temporaryRedirect(location);
        });
    }

}
