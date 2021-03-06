package com.jivesoftware.os.upena.deployable.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.server.CorsContainerResponseFilter;
import com.jivesoftware.os.routing.bird.server.HasServletContextHandler;
import com.jivesoftware.os.routing.bird.server.JacksonFeature;
import com.jivesoftware.os.routing.bird.server.binding.Injectable;
import com.jivesoftware.os.routing.bird.server.binding.InjectableBinder;
import io.swagger.jaxrs.listing.ApiListingResource;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.servlet.DispatcherType;
import javax.ws.rs.container.ContainerRequestFilter;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;
import org.glassfish.jersey.server.filter.HttpMethodOverrideFilter;
import org.glassfish.jersey.servlet.ServletContainer;

/**
 *
 * @author jonathan.colt
 */
public class UpenaJerseyEndpoints implements HasServletContextHandler {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String shiroConfigLocation;
    private final Set<Class<?>> allClasses = new HashSet<>();
    private final Set<Class<?>> allInjectedClasses = new HashSet<>();
    private final Set<Object> allBinders = new HashSet<>();
    private final List<Injectable<?>> allInjectables = Lists.newArrayList();
    private final List<ContainerRequestFilter> containerRequestFilters = Lists.newArrayList();
    private boolean supportCORS = false;
    private boolean csrfEnabled = false;

    private final ObjectMapper mapper;

    public UpenaJerseyEndpoints(String shiroConfigLocation) {

        this.shiroConfigLocation = shiroConfigLocation;
        this.mapper = new ObjectMapper()
            .configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public UpenaJerseyEndpoints addProvider(Class<?> provider) {
        allClasses.add(provider);
        return this;
    }

    public UpenaJerseyEndpoints addEndpoint(Class<?> jerseyEndpoint) {
        allClasses.add(jerseyEndpoint);
        return this;
    }

    public UpenaJerseyEndpoints addBinder(Binder requestInfoInjectable) {
        allBinders.add(requestInfoInjectable);
        return this;
    }

    public UpenaJerseyEndpoints addInjectable(Object injectableInstance) {
        return addInjectable(Injectable.of(injectableInstance));
    }

    public UpenaJerseyEndpoints addInjectable(Class<?> injectableClass, Object injectableInstance) {
        return addInjectable(Injectable.ofUnsafe(injectableClass, injectableInstance));
    }

    public UpenaJerseyEndpoints addInjectable(Injectable<?> injectable) {
        Class<?> injectableClass = injectable.getClazz();
        if (allInjectedClasses.contains(injectableClass)) {
            LOG.warn("You should only inject a single instance for any given class. You have already injected class {}", injectableClass);
        } else {
            allInjectedClasses.add(injectableClass);
            allInjectables.add(injectable);
        }

        return this;
    }

    public UpenaJerseyEndpoints addContainerRequestFilter(ContainerRequestFilter containerRequestFilter) {
        containerRequestFilters.add(containerRequestFilter);
        return this;
    }

    public UpenaJerseyEndpoints enableCORS() {
        supportCORS = true;
        return this;
    }

    public UpenaJerseyEndpoints enableCSRF() {
        csrfEnabled = true;
        return this;
    }

    public List<Injectable<?>> getInjectables() {
        return Collections.unmodifiableList(allInjectables);
    }

    public UpenaJerseyEndpoints humanReadableJson() {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        return this;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    @Override
    public Handler getHandler(final Server server, String context, String applicationName) {

        Set<String> packages = new HashSet<>();
        packages.add(ApiListingResource.class.getPackage().getName());
        for (Class<?> clazz : allClasses) {
            packages.add(clazz.getPackage().getName());
        }

        ResourceConfig rc = new ResourceConfig();
        rc.packages(packages.toArray(new String[0]));

        rc.registerClasses(allClasses);
        rc.register(HttpMethodOverrideFilter.class);
        rc.register(new JacksonFeature().withMapper(mapper));
        rc.register(MultiPartFeature.class); // adds support for multi-part API requests
        rc.registerInstances(allBinders);
        rc.registerInstances(
            new InjectableBinder(allInjectables),
            new AbstractBinder() {
            @Override
            protected void configure() {
                bind(server).to(Server.class);
            }
        }
        );

        if (supportCORS) {
            rc.register(CorsContainerResponseFilter.class);
        }

        for (ContainerRequestFilter containerRequestFilter : containerRequestFilters) {
            rc.register(containerRequestFilter);
        }

        if (csrfEnabled) {
            rc.register(CsrfProtectionFilter.class);
        }

        ServletContainer servletContainer = new ServletContainer(rc);
        ServletHolder servletHolder = new ServletHolder(servletContainer);

        HashSessionManager sessionManager = new HashSessionManager();
        sessionManager.setMaxInactiveInterval((int)TimeUnit.DAYS.toSeconds(1));
        sessionManager.setHttpOnly(true);

        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setSessionHandler(new SessionHandler(sessionManager));
        servletContextHandler.setContextPath(context);
        if (!applicationName.isEmpty()) {
            servletContextHandler.setDisplayName(applicationName);
        }
        servletContextHandler.addServlet(servletHolder, "/*");

        if (shiroConfigLocation != null) {
            servletContextHandler.setInitParameter("shiroConfigLocations", shiroConfigLocation);
        }
        servletContextHandler.addEventListener(new EnvironmentLoaderListener());
        servletContextHandler.addFilter(ShiroFilter.class, "/ui/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE,
            DispatcherType.ERROR));

        return servletContextHandler;
    }

}
