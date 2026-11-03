package com.ticketshop.admin.session.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface CustomerSessionMapper extends BaseMapper<CustomerSessionEntity> {
    int revokeUnrevoked(@Param("accountId") long accountId, @Param("reason") String reason, @Param("now") java.time.Instant now);
    int shortenUnexpired(@Param("accountId") long accountId, @Param("hours") int hours,
            @Param("policyVersion") long policyVersion, @Param("now") java.time.Instant now);

}
