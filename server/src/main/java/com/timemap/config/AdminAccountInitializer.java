package com.timemap.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timemap.mapper.AdminAccountMapper;
import com.timemap.model.entity.AdminAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements CommandLineRunner {

    private final AdminAccountMapper adminAccountMapper;

    @Override
    public void run(String... args) {
        try {
            long count = adminAccountMapper.selectCount(
                    new LambdaQueryWrapper<AdminAccount>().eq(AdminAccount::getUsername, "admin"));
            if (count == 0) {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                AdminAccount account = new AdminAccount();
                account.setUsername("admin");
                account.setPasswordHash(encoder.encode("Admin@2026"));
                account.setNickname("超级管理员");
                account.setRole("super_admin");
                account.setIsEnabled(1);
                account.setMustChangePassword(1);
                account.setLoginFailCount(0);
                account.setPasswordHistory("[\"" + account.getPasswordHash() + "\"]");
                adminAccountMapper.insert(account);
                log.info("默认管理员账号已创建: admin / Admin@2026");
            }
        } catch (Exception e) {
            log.warn("初始化管理员账号失败（表可能不存在）: {}", e.getMessage());
        }
    }
}
