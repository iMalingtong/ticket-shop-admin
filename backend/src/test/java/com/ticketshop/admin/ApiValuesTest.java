package com.ticketshop.admin;

import org.junit.jupiter.api.Test;
import com.ticketshop.admin.common.api.ApiValues;
import static org.assertj.core.api.Assertions.*;

class ApiValuesTest {
    @Test void idsAreLosslessPositiveDecimalStrings() {
        assertThat(ApiValues.id(9007199254740993L)).isEqualTo("9007199254740993");
        assertThat(ApiValues.parseId("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        for (String value : new String[]{"0","01","-1","9223372036854775808","1.0"}) {
            assertThatThrownBy(() -> ApiValues.parseId(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void paginationAndCompareAndSetAreValidated() {
        assertThatThrownBy(() -> ApiValues.requirePage(0,10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiValues.requirePage(1,200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiValues.requireChanged(0)).isInstanceOf(ApiValues.VersionConflictException.class);
        ApiValues.requireChanged(1);
    }
}
