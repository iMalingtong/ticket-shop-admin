package com.ticketshop.admin.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;

/** Infrastructure only; writes are composed by transactional application services, never controllers. */
@Mapper
public interface CustomerAccountMapper extends BaseMapper<CustomerAccountEntity> {
    CustomerAccountEntity lockById(@Param("id") long id);
    IPage<CustomerAccountEntity> search(IPage<CustomerAccountEntity> page, @Param("filter") CustomerFilter filter);
    int updateProfile(@Param("id") long id, @Param("displayName") String displayName,
            @Param("remarkPresent") boolean remarkPresent, @Param("remark") String remark,
            @Param("actorId") long actorId, @Param("now") java.time.Instant now, @Param("expectedVersion") long expectedVersion);
    int updateStatus(@Param("id") long id, @Param("from") String from, @Param("to") String to,
            @Param("actorId") long actorId, @Param("now") java.time.Instant now, @Param("expectedVersion") long expectedVersion);
    int updateValidity(@Param("id") long id, @Param("validFrom") java.time.Instant validFrom,
            @Param("validUntil") java.time.Instant validUntil, @Param("actorId") long actorId,
            @Param("now") java.time.Instant now, @Param("expectedVersion") long expectedVersion);

}
