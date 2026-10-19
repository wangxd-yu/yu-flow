package org.yu.flow.util.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void reusesValidInboundIdAndEchoesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader(RequestIds.HEADER, "gw-1700000000.abc:01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(RequestIds.current()));

        assertEquals("gw-1700000000.abc:01", seen.get());
        assertEquals("gw-1700000000.abc:01", response.getHeader(RequestIds.HEADER));
        assertEquals("gw-1700000000.abc:01", request.getAttribute(RequestIdFilter.ATTRIBUTE));
        assertNull(RequestIds.current());
    }

    @Test
    void replacesInvalidInboundId() throws Exception {
        String[] invalid = {"a b", "x\r\nINFO forged log line", "<script>", "a".repeat(65), ""};
        for (String inbound : invalid) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
            request.addHeader(RequestIds.HEADER, inbound);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();

            filter.doFilter(request, response, (req, res) -> seen.set(RequestIds.current()));

            assertNotEquals(inbound, seen.get());
            assertTrue(seen.get().matches("[0-9a-f]{32}"), seen.get());
            assertEquals(seen.get(), response.getHeader(RequestIds.HEADER));
        }
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> { });

        assertTrue(first.getHeader(RequestIds.HEADER).matches("[0-9a-f]{32}"));
        assertNotEquals(first.getHeader(RequestIds.HEADER), second.getHeader(RequestIds.HEADER));
    }

    @Test
    void restoresOuterIdAfterNestedDispatch() throws Exception {
        MDC.put(RequestIds.MDC_KEY, "outer");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "inner");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertEquals("inner", RequestIds.current()));

        assertEquals("outer", RequestIds.current());
    }

    @Test
    void openOnlyOwnsIdItCreated() {
        try (RequestIds.Scope outer = RequestIds.open()) {
            String id = outer.id();
            try (RequestIds.Scope inner = RequestIds.open()) {
                assertEquals(id, inner.id());
            }
            assertEquals(id, RequestIds.current());
        }
        assertNull(RequestIds.current());
    }
}
