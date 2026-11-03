package com.ticketshop.admin.authentication.application;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;
import java.sql.*;
import static com.ticketshop.admin.common.api.ErrorCode.*;

/** One monotonic deadline per request, shared by transaction/SQL/cache phases. */
@Component
@Intercepts(@Signature(type=StatementHandler.class,method="prepare",args={Connection.class,Integer.class}))
public class RequestBudget implements Interceptor {
    private static final ThreadLocal<Long> END=new ThreadLocal<>();
    public static void start() { END.set(System.nanoTime()+10_000_000_000L); }
    public static void clear() { END.remove(); }
    public static int seconds() { Long end=END.get(); if(end==null) return 8; long remaining=(end-System.nanoTime())/1_000_000_000L-2; /* reserve 2s for bounded commit/rollback and response cleanup */ if(remaining<1) throw new AuthProblem(SERVICE_UNAVAILABLE); return (int)Math.min(8,remaining); }
    public static void redis() { if(seconds()<2) throw new AuthProblem(SERVICE_UNAVAILABLE); }
    @Override public Object intercept(Invocation invocation) throws Throwable { int remaining=seconds(); var statement=(Statement)invocation.proceed(); int prior=statement.getQueryTimeout(); statement.setQueryTimeout(prior==0 ? remaining : Math.min(prior,remaining)); return statement; }
}
