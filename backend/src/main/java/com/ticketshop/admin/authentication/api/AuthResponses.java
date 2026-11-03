package com.ticketshop.admin.authentication.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketshop.admin.authentication.application.AuthProblem;
import com.ticketshop.admin.common.api.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class AuthResponses {
    private final ObjectMapper json; private final Clock clock;
    public AuthResponses(ObjectMapper json,Clock clock) { this.json=json; this.clock=clock; }
    public static String requestId(HttpServletRequest request) { return (String)request.getAttribute("requestId"); }
    public ApiFailure failure(AuthProblem problem,HttpServletRequest request) {
        return new ApiFailure(new ApiFailure.Problem(problem.code(),message(problem.code()),problem.details()),requestId(request),clock.instant());
    }
    public void write(HttpServletRequest request,HttpServletResponse response,AuthProblem problem) throws IOException {
        response.setStatus(problem.code().httpStatus()); response.setContentType("application/json;charset=UTF-8");
        if(problem.details()!=null && problem.details().retryAfterSeconds()!=null) response.setHeader("Retry-After",problem.details().retryAfterSeconds().toString());
        json.writeValue(response.getOutputStream(),failure(problem,request));
    }
    private String message(ErrorCode code) { return switch(code) {
        case INVALID_CREDENTIALS -> "账号或密码不正确";
        case LOGIN_RATE_LIMITED -> "尝试次数过多，请稍后再试";
        case SERVICE_UNAVAILABLE -> "服务暂不可用，请稍后重试";
        case CSRF_INVALID -> "页面校验已失效，请刷新后重试";
        case PASSWORD_CHANGE_REQUIRED -> "请先修改初始密码";
        case VALIDATION_ERROR,PROTOCOL_INVALID -> "输入格式不正确";
        default -> "请求未获准，请重新登录或联系管理员";
    }; }
}
