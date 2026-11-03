package com.ticketshop.admin.policy.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface LoginPolicyMapper extends BaseMapper<LoginPolicyEntity> {
    @Select("SELECT * FROM login_policy WHERE id = #{id} FOR UPDATE")
    LoginPolicyEntity lockById(@Param("id") long id);

}
