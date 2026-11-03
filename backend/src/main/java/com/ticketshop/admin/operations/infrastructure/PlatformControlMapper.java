package com.ticketshop.admin.operations.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface PlatformControlMapper extends BaseMapper<PlatformControlEntity> {
    @Select("SELECT * FROM platform_control WHERE id = #{id} FOR UPDATE")
    PlatformControlEntity lockById(@Param("id") long id);

}
