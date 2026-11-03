package com.ticketshop.admin.session.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface AdminSessionMapper extends BaseMapper<AdminSessionEntity> {
}
