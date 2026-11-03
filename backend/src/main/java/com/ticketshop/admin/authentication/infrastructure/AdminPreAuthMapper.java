package com.ticketshop.admin.authentication.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface AdminPreAuthMapper extends BaseMapper<AdminPreAuthEntity> {
}
