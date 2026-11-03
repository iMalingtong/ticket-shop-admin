package com.ticketshop.admin.common.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface MutationRequestMapper extends BaseMapper<MutationRequestEntity> {
}
