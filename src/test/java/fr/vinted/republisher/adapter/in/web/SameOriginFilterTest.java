package fr.vinted.republisher.adapter.in.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SameOriginFilterTest {

    private static final String INTERFACE_IN_DEVELOPMENT = "http://localhost:4200";

    @Test
    void action_from_the_trusted_interface_origin_is_accepted() throws ServletException, IOException {
        MockHttpServletResponse response = filter(new SameOriginFilter(List.of(INTERFACE_IN_DEVELOPMENT)),
                INTERFACE_IN_DEVELOPMENT);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void action_from_another_site_is_refused_even_with_a_trusted_origin() throws ServletException, IOException {
        MockHttpServletResponse response = filter(new SameOriginFilter(List.of(INTERFACE_IN_DEVELOPMENT)),
                "https://example.test");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void interface_origin_is_refused_when_not_trusted() throws ServletException, IOException {
        MockHttpServletResponse response = filter(new SameOriginFilter(List.of()), INTERFACE_IN_DEVELOPMENT);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static MockHttpServletResponse filter(SameOriginFilter filter, String origin)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/wardrobe/scan");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
