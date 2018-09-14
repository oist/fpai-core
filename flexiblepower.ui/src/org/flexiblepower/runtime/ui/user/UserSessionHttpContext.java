package org.flexiblepower.runtime.ui.user;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.http.HttpContext;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.useradmin.UserAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = HttpContext.class,
           property = { "contextId=fps" },
           configurationPolicy = ConfigurationPolicy.OPTIONAL,
           immediate = true)
@Designate(ocd = UserSessionHttpContext.Config.class)
public class UserSessionHttpContext implements HttpContext {
    private final static Logger logger = LoggerFactory.getLogger(UserSessionHttpContext.class);

    @ObjectClassDefinition(name = "User session management configuration")
    public interface Config {
        @AttributeDefinition(type = AttributeType.BOOLEAN, defaultValue = "false", required = false)
        boolean isDisabled();
    }

    private final SessionManager sessionManager;

    public UserSessionHttpContext() {
        sessionManager = new SessionManager();
    }

    private Bundle bundle;

    private boolean disabled;

    private LoginServlet loginServlet;
    private LogoutServlet logoutServlet;

    private ServiceTracker<UserAdmin, UserAdmin> trackedUserAdmins;

    @Activate
    public void activate(BundleContext context, Map<String, Object> parameters) throws IOException {
        bundle = context.getBundle();

        disabled = false;
        if (parameters.containsKey("isDisabled")) {
            Object isDisabled = parameters.get("isDisabled");
            disabled = Boolean.parseBoolean(isDisabled.toString());
        }

        if (!disabled) {
            try {
                trackedUserAdmins = new ServiceTracker<UserAdmin, UserAdmin>(context, UserAdmin.class, null);
                trackedUserAdmins.open();

                loginServlet = new LoginServlet(context, sessionManager);
                logoutServlet = new LogoutServlet(context, sessionManager);
            } catch (NoClassDefFoundError error) {
                // No UserAdmin package available...
                disabled = true;
            }
        }

        logger.debug("Started user context. Disabled = {}", disabled);
    }

    @Deactivate
    public void deactivate() {
        if (trackedUserAdmins != null) {
            trackedUserAdmins.close();
        }

        if (loginServlet != null) {
            loginServlet.close();
        }
        if (logoutServlet != null) {
            logoutServlet.close();
        }
    }

    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.trace("Entering handleSecurity, request.pathInfo = {}", request.getPathInfo());

        if (disabled || trackedUserAdmins.isEmpty()) {
            return true;
        }

        try {
            Session session = sessionManager.getSession(request);
            request.setAttribute("session", session);
            request.setAttribute("user", session.getUser());
            logger.trace("Leaving handleSecurity, result = true");
            return true;
        } catch (IllegalSessionException e) {
            // Session not valid, redirect to /
            logger.warn("Unauthorized acces to " + request.getPathInfo()
                        + " (Cookies: "
                        + cookiesToString(request.getCookies())
                        + ")");
            response.sendRedirect("/login.html?from=" + request.getPathInfo());
            // response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            // "Please log in before you can access this page");
            logger.trace("Leaving handleSecurity, result = false");
            return false;
        }
    }

    private String cookiesToString(Cookie[] cookies) {
        if (cookies == null || cookies.length == 0) {
            return "";
        }

        List<String> strings = new ArrayList<String>(cookies.length);
        for (Cookie cookie : cookies) {
            strings.add(cookie.getName() + "=" + cookie.getValue());
        }

        return strings.toString();
    }

    @Override
    public URL getResource(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return bundle.getResource(name);
    }

    @Override
    public String getMimeType(String name) {
        return null;
    }
}
