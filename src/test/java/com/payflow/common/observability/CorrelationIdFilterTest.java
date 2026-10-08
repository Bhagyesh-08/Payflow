package com.payflow.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseProvidedCorrelationId() throws Exception {
        // Arrange
        String providedId = "test-correlation-id-123";
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(providedId);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, providedId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldCleanUpMDCAfterRequest() throws Exception {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("test-id");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert - MDC should be cleared after filter execution
        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }

    @Test
    void shouldCleanUpMDCEvenWhenExceptionThrown() throws Exception {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("test-id");
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(request, response);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            filter.doFilterInternal(request, response, filterChain)
        );

        // MDC should still be cleared
        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }

    @Test
    void shouldGenerateNewIdWhenProvidedIdIsBlank() throws Exception {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("   ");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }
}
