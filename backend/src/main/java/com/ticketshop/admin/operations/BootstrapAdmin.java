package com.ticketshop.admin.operations;

import com.ticketshop.admin.TicketShopAdminApplication;
import com.ticketshop.admin.authentication.application.AuthService;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.*;

/** No HTTP endpoint, no plaintext command argument, no default credential. */
public final class BootstrapAdmin {
    private BootstrapAdmin() {}
    public static void run() {
        var console=System.console(); if(console==null) throw new IllegalStateException("请在交互终端执行离线引导；不接受管道密码");
        String username=console.readLine("管理员账号："),displayName=console.readLine("显示名称：");
        char[] password=console.readPassword("初始密码（隐藏输入）："),confirmation=console.readPassword("再次输入：");
        if(password==null || confirmation==null) throw new IllegalStateException("已取消引导");
        try {
            if(!Arrays.equals(password,confirmation)) throw new IllegalStateException("两次密码不一致");
            var app=new SpringApplication(TicketShopAdminApplication.class); app.setWebApplicationType(WebApplicationType.NONE);
            try(var context=app.run()) {
                context.getBean(AuthService.class).bootstrap(username,displayName,new String(password),UUID.randomUUID().toString());
                console.printf("管理员引导完成；首次登录必须改密。%n");
            }
        } finally { Arrays.fill(password,'\0'); Arrays.fill(confirmation,'\0'); }
    }
}
