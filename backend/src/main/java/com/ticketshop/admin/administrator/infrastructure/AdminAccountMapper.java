package com.ticketshop.admin.administrator.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccountEntity> {
    @Select("SELECT * FROM admin_account WHERE id = #{id} FOR UPDATE")
    AdminAccountEntity lockById(@Param("id") long id);

}
