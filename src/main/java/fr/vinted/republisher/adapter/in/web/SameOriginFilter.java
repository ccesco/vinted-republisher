package fr.vinted.republisher.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * The API has no login: without this filter, any site open in the member's browser could launch
 * browser actions on the Vinted account. A browser always sends the Origin of a POST coming from a page.
 */
@Component
public class SameOriginFilter extends OncePerRequestFilter {

    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;

    // L'interface Angular servie à part (US-37) parle à l'API sous sa propre origine, à déclarer explicitement.
    private final List<String> trustedOrigins;

    public SameOriginFilter(@Value("${web.trusted-origins:}") List<String> trustedOrigins) {
        this.trustedOrigins = List.copyOf(trustedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || trustedOrigins.contains(origin) || isSameOrigin(origin, request)) {
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private static boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI uri = URI.create(origin);
            return request.getServerName().equalsIgnoreCase(uri.getHost())
                    && request.getServerPort() == portOf(uri);
        } catch (IllegalArgumentException unparsable) {
            return false;
        }
    }

    private static int portOf(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equals(uri.getScheme()) ? HTTPS_DEFAULT_PORT : HTTP_DEFAULT_PORT;
    }
}
