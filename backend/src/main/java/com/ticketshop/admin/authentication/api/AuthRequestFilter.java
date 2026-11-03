package com.ticketshop.admin.authentication.api;

import com.ticketshop.admin.authentication.application.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRequestFilter extends OncePerRequestFilter {
    private final AuthResponses responses;
    public AuthRequestFilter(AuthResponses responses) { this.responses=responses; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        request.setAttribute("requestId",UUID.randomUUID().toString()); response.setHeader("X-Request-Id",AuthResponses.requestId(request));
        if(request.getRequestURI().startsWith("/api/")) response.setHeader("Cache-Control","no-store");
        RequestBudget.start();
        try {
            // Bound JSON before Spring parsing, including chunked bodies (read at most limit+1).
            if(request.getRequestURI().startsWith("/api/") && !request.getMethod().equals("GET")) {
                if(request.getContentLengthLong()>16384) throw new AuthProblem(VALIDATION_ERROR);
                byte[] body=request.getInputStream().readNBytes(16385); if(body.length>16384) throw new AuthProblem(VALIDATION_ERROR);
                var wrapped=new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() {
                        var input=new java.io.ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            public int read() { return input.read(); }
                            public boolean isFinished() { return input.available()==0; }
                            public boolean isReady() { return true; }
                            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                        };
                    }
                };
                chain.doFilter(wrapped,response);
            } else chain.doFilter(request,response);
        } catch(AuthProblem e) { if(!response.isCommitted()) responses.write(request,response,e); }
        finally { RequestBudget.clear(); }
    }
}
