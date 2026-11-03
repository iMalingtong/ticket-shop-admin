package com.ticketshop.admin.authentication.api;

import com.ticketshop.admin.authentication.application.AuthProblem;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@RestControllerAdvice
public class AuthExceptionHandler {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AuthExceptionHandler.class);
    private final AuthResponses responses;
    public AuthExceptionHandler(AuthResponses responses) { this.responses=responses; }
    @ExceptionHandler(AuthProblem.class) void problem(AuthProblem e,HttpServletRequest request,HttpServletResponse response) throws IOException { responses.write(request,response,e); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class})
    void input(Exception e,HttpServletRequest request,HttpServletResponse response) throws IOException { responses.write(request,response,new AuthProblem(VALIDATION_ERROR)); }
    @ExceptionHandler(Exception.class) void unavailable(Exception e,HttpServletRequest request,HttpServletResponse response) throws IOException {
        Throwable cause=e; while(cause.getCause()!=null && cause.getCause()!=cause) cause=cause.getCause();
        LOG.warn("请求失败 requestId={} type={} sqlState={} sqlCode={}",AuthResponses.requestId(request),cause.getClass().getSimpleName(),cause instanceof java.sql.SQLException s?s.getSQLState():"",cause instanceof java.sql.SQLException s?s.getErrorCode():0);
        responses.write(request,response,new AuthProblem(SERVICE_UNAVAILABLE));
    }
}
